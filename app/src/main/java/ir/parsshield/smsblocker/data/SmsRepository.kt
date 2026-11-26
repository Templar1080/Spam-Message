package ir.parsshield.smsblocker.data

import android.Manifest
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import ir.parsshield.smsblocker.engine.ClassificationResult
import ir.parsshield.smsblocker.engine.IranSmsClassifier
import ir.parsshield.smsblocker.engine.RuleAction
import ir.parsshield.smsblocker.engine.RuleTarget
import ir.parsshield.smsblocker.engine.SenderAnalyzer
import ir.parsshield.smsblocker.engine.SmsCategory
import ir.parsshield.smsblocker.service.SimCardHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class ProcessedSmsOutcome(
    val entity: SmsEntity?,
    val result: ClassificationResult,
    val isBlocked: Boolean,
    val wasDuplicate: Boolean
)

class SmsRepository(
    private val context: Context,
    private val dao: SmsDao,
    private val preferences: PreferencesRepository
) {
    val allMessages: Flow<List<SmsEntity>> = dao.observeAllMessages()
    val allRules: Flow<List<RuleEntity>> = dao.observeRules()

    suspend fun processIncomingSms(
        sender: String,
        body: String,
        timestamp: Long = System.currentTimeMillis(),
        simSlot: Int? = null
    ): ProcessedSmsOutcome = withContext(Dispatchers.IO) {
        val settings = preferences.getSnapshot()
        val rules = dao.getAllRulesSnapshot().map { it.toDomain() }
        val contactName = lookupContactName(sender)
        val threadAddress = SenderAnalyzer.canonicalizeThreadAddress(sender)

        val result = IranSmsClassifier.classify(
            sender = sender,
            body = body,
            isSavedContact = contactName != null,
            filterMode = settings.filterMode,
            customRules = rules,
            neverBlockOtpEvenFromBlocked = settings.neverBlockOtpEvenFromBlocked,
            zeroToleranceCommercialPanels = settings.zeroToleranceCommercialPanels
        )

        val shouldActuallyBlock = settings.shieldEnabled && result.shouldBlock

        val existing = dao.findRecentDuplicate(
            threadAddress = threadAddress,
            body = body,
            minTimestamp = timestamp - 10_000L
        )
        if (existing != null) {
            return@withContext ProcessedSmsOutcome(
                entity = existing,
                result = result,
                isBlocked = shouldActuallyBlock,
                wasDuplicate = true
            )
        }

        if (shouldActuallyBlock && settings.dropSpamCompletely) {
            preferences.incrementVaporizedCount()
            return@withContext ProcessedSmsOutcome(
                entity = null,
                result = result.copy(shouldBlock = true),
                isBlocked = true,
                wasDuplicate = false
            )
        }

        var insertedSystemId: Long? = null
        if (!shouldActuallyBlock && isDefaultSmsApp(context)) {
            insertedSystemId = insertIntoSystemInbox(sender, body, timestamp)
        }

        if (!shouldActuallyBlock &&
            result.category == SmsCategory.OTP &&
            result.otpCode != null &&
            settings.autoCopyOtpToClipboard
        ) {
            withContext(Dispatchers.Main) {
                copyOtpToClipboard(context, result.otpCode)
            }
        }

        val entity = SmsEntity(
            threadAddress = threadAddress,
            sender = sender,
            displayName = contactName,
            body = body,
            timestamp = timestamp,
            category = result.category,
            isBlocked = shouldActuallyBlock,
            isOutgoing = false,
            simSlot = simSlot,
            otpCode = result.otpCode,
            spamScore = result.spamScore,
            reasonSummary = result.reasonSummary,
            isRead = shouldActuallyBlock,
            systemSmsId = insertedSystemId
        )

        val newId = dao.insertMessage(entity)
        ProcessedSmsOutcome(
            entity = entity.copy(id = newId),
            result = result.copy(shouldBlock = shouldActuallyBlock),
            isBlocked = shouldActuallyBlock,
            wasDuplicate = false
        )
    }

    suspend fun scanExistingSystemInbox(limit: Int = 350): Int = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext 0
        }

        val settings = preferences.getSnapshot()
        val rules = dao.getAllRulesSnapshot().map { it.toDomain() }
        var importedCount = 0

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.TYPE} IN (${Telephony.Sms.MESSAGE_TYPE_INBOX}, ${Telephony.Sms.MESSAGE_TYPE_SENT})",
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val subIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                var scanned = 0
                while (cursor.moveToNext() && scanned < limit) {
                    scanned++
                    val sysId = cursor.getLong(idIdx)
                    if (dao.existsBySystemId(sysId)) continue

                    val sender = cursor.getString(addrIdx)?.takeIf { it.isNotBlank() } ?: "Unknown"
                    val body = cursor.getString(bodyIdx) ?: continue
                    val timestamp = cursor.getLong(dateIdx)
                    val isRead = cursor.getInt(readIdx) == 1
                    val isOutgoing = cursor.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_SENT
                    val rawSubId = if (subIdx >= 0) cursor.getInt(subIdx) else -1
                    val simSlot = SimCardHelper.resolveSimSlotNumber(context, rawSubId)

                    val contactName = lookupContactName(sender)
                    val threadAddress = SenderAnalyzer.canonicalizeThreadAddress(sender)

                    if (isOutgoing) {
                        dao.insertMessage(
                            SmsEntity(
                                threadAddress = threadAddress,
                                sender = sender,
                                displayName = contactName,
                                body = body,
                                timestamp = timestamp,
                                category = SmsCategory.PERSONAL,
                                isBlocked = false,
                                isOutgoing = true,
                                simSlot = simSlot,
                                isRead = true,
                                systemSmsId = sysId
                            )
                        )
                    } else {
                        val result = IranSmsClassifier.classify(
                            sender = sender,
                            body = body,
                            isSavedContact = contactName != null,
                            filterMode = settings.filterMode,
                            customRules = rules,
                            neverBlockOtpEvenFromBlocked = settings.neverBlockOtpEvenFromBlocked,
                            zeroToleranceCommercialPanels = settings.zeroToleranceCommercialPanels
                        )
                        dao.insertMessage(
                            SmsEntity(
                                threadAddress = threadAddress,
                                sender = sender,
                                displayName = contactName,
                                body = body,
                                timestamp = timestamp,
                                category = result.category,
                                isBlocked = result.shouldBlock,
                                isOutgoing = false,
                                simSlot = simSlot,
                                otpCode = result.otpCode,
                                spamScore = result.spamScore,
                                reasonSummary = result.reasonSummary,
                                isRead = isRead || result.shouldBlock,
                                systemSmsId = sysId
                            )
                        )
                    }
                    importedCount++
                }
            }
        }

        importedCount
    }

    suspend fun markThreadAsRead(threadAddress: String) = withContext(Dispatchers.IO) {
        dao.markThreadRead(threadAddress)
    }

    suspend fun markNotSpamAndWhitelist(entity: SmsEntity) = withContext(Dispatchers.IO) {
        var sysId = entity.systemSmsId
        if (sysId == null && isDefaultSmsApp(context)) {
            sysId = insertIntoSystemInbox(entity.sender, entity.body, entity.timestamp)
        }
        dao.updateMessage(
            entity.copy(
                isBlocked = false,
                category = if (entity.otpCode != null) SmsCategory.OTP else SmsCategory.PERSONAL,
                reasonSummary = "Restored & sender added to whitelist.",
                systemSmsId = sysId
            )
        )
        dao.insertRule(
            RuleEntity(
                pattern = entity.sender,
                target = RuleTarget.SENDER,
                action = RuleAction.ALLOW
            )
        )
    }

    suspend fun markAsSpamAndBlockSender(entity: SmsEntity) = withContext(Dispatchers.IO) {
        dao.updateMessage(
            entity.copy(
                isBlocked = true,
                category = SmsCategory.SPAM_AD,
                spamScore = 100,
                isRead = true,
                reasonSummary = "Blocked & sender added to blacklist (OTPs remain protected)."
            )
        )
        dao.markThreadBlockedExceptOtp(entity.threadAddress)
        dao.insertRule(
            RuleEntity(
                pattern = entity.sender,
                target = RuleTarget.SENDER,
                action = RuleAction.BLOCK
            )
        )
    }

    suspend fun blockSenderById(messageId: Long) = withContext(Dispatchers.IO) {
        val entity = dao.findById(messageId) ?: return@withContext
        markAsSpamAndBlockSender(entity)
    }

    suspend fun blockThreadSender(threadAddress: String, rawSender: String) =
        withContext(Dispatchers.IO) {
            dao.markThreadBlockedExceptOtp(threadAddress)
            dao.insertRule(
                RuleEntity(
                    pattern = rawSender,
                    target = RuleTarget.SENDER,
                    action = RuleAction.BLOCK
                )
            )
        }

    suspend fun deleteMessage(entity: SmsEntity) = withContext(Dispatchers.IO) {
        dao.deleteMessage(entity)
    }

    suspend fun clearAllBlocked() = withContext(Dispatchers.IO) {
        dao.clearAllBlocked()
    }

    suspend fun addRule(pattern: String, target: RuleTarget, action: RuleAction) =
        withContext(Dispatchers.IO) {
            val trimmed = pattern.trim()
            if (trimmed.isNotEmpty()) {
                dao.insertRule(RuleEntity(pattern = trimmed, target = target, action = action))
            }
        }

    suspend fun deleteRule(rule: RuleEntity) = withContext(Dispatchers.IO) {
        dao.deleteRule(rule)
    }

    /**
     * Sends an SMS using the user's chosen SIM card ([subscriptionId] for SIM 1 / SIM 2) or
     * default subscription, working both when ParsShield is Default SMS App and when SEND_SMS is granted.
     */
    suspend fun sendSmsMessage(
        destination: String,
        text: String,
        subscriptionId: Int? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val destTrimmed = destination.trim()
            val effectiveSubId = subscriptionId?.takeIf {
                it != SubscriptionManager.INVALID_SUBSCRIPTION_ID && it >= 0
            } ?: preferences.getSnapshot().preferredSimSubId.takeIf { it >= 0 }

            val smsManager = when {
                effectiveSubId != null -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java)
                            .createForSubscriptionId(effectiveSubId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(effectiveSubId)
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    context.getSystemService(SmsManager::class.java)
                }
                else -> {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            }

            val parts = smsManager.divideMessage(text)
            smsManager.sendMultipartTextMessage(destTrimmed, null, parts, null, null)

            val simSlot = SimCardHelper.resolveSimSlotNumber(context, effectiveSubId)
            val now = System.currentTimeMillis()
            var sysId: Long? = null
            if (isDefaultSmsApp(context)) {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, destTrimmed)
                    put(Telephony.Sms.BODY, text)
                    put(Telephony.Sms.DATE, now)
                    put(Telephony.Sms.READ, 1)
                    if (effectiveSubId != null) {
                        put(Telephony.Sms.SUBSCRIPTION_ID, effectiveSubId)
                    }
                }
                sysId = runCatching {
                    context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                        ?.let { ContentUris.parseId(it) }
                }.getOrNull()
            }

            val contactName = lookupContactName(destTrimmed)
            dao.insertMessage(
                SmsEntity(
                    threadAddress = SenderAnalyzer.canonicalizeThreadAddress(destTrimmed),
                    sender = destTrimmed,
                    displayName = contactName,
                    body = text,
                    timestamp = now,
                    category = SmsCategory.PERSONAL,
                    isBlocked = false,
                    isOutgoing = true,
                    simSlot = simSlot,
                    reasonSummary = "Sent message",
                    isRead = true,
                    systemSmsId = sysId
                )
            )
            Unit
        }
    }

    private fun insertIntoSystemInbox(sender: String, body: String, timestamp: Long): Long? {
        return runCatching {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 0)
            }
            val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            uri?.let { ContentUris.parseId(it) }
        }.getOrNull()
    }

    fun lookupContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    companion object {
        fun isDefaultSmsApp(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
            } else {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }
        }

        fun copyOtpToClipboard(context: Context, otpCode: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OTP Verification Code", otpCode))
        }
    }
}
