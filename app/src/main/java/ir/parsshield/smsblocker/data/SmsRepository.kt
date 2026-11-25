package ir.parsshield.smsblocker.data

import android.Manifest
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
import androidx.core.content.ContextCompat
import ir.parsshield.smsblocker.engine.ClassificationResult
import ir.parsshield.smsblocker.engine.IranSmsClassifier
import ir.parsshield.smsblocker.engine.RuleAction
import ir.parsshield.smsblocker.engine.RuleTarget
import ir.parsshield.smsblocker.engine.SmsCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class ProcessedSmsOutcome(
    val entity: SmsEntity,
    val result: ClassificationResult,
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
        writeAllowedToSystemProvider: Boolean = false
    ): ProcessedSmsOutcome = withContext(Dispatchers.IO) {
        val settings = preferences.getSnapshot()
        val rules = dao.getAllRulesSnapshot().map { it.toDomain() }
        val contactName = lookupContactName(sender)
        val isContact = contactName != null

        val result = IranSmsClassifier.classify(
            sender = sender,
            body = body,
            isSavedContact = isContact,
            filterMode = settings.filterMode,
            customRules = rules,
            neverBlockOtpEvenFromBlocked = settings.neverBlockOtpEvenFromBlocked
        )

        val shouldActuallyBlock = settings.shieldEnabled && result.shouldBlock

        // Deduplicate within 12 seconds so NotificationListener + BroadcastReceiver never double-record
        val existing = dao.findRecentDuplicate(
            sender = sender,
            body = body,
            minTimestamp = timestamp - 12_000L
        )
        if (existing != null) {
            return@withContext ProcessedSmsOutcome(
                entity = existing,
                result = result,
                wasDuplicate = true
            )
        }

        var insertedSystemId: Long? = null
        if (!shouldActuallyBlock && writeAllowedToSystemProvider && isDefaultSmsApp(context)) {
            insertedSystemId = insertIntoSystemInbox(sender, body, timestamp)
        }

        if (result.category == SmsCategory.OTP && result.otpCode != null && settings.autoCopyOtpToClipboard) {
            withContext(Dispatchers.Main) {
                copyOtpToClipboard(context, result.otpCode)
            }
        }

        val entity = SmsEntity(
            sender = sender,
            displayName = contactName,
            body = body,
            timestamp = timestamp,
            category = result.category,
            isBlocked = shouldActuallyBlock,
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
            wasDuplicate = false
        )
    }

    /**
     * Scans up to [limit] messages from Android's system SMS inbox so users immediately see
     * their existing Iranian OTPs, Bank alerts, and Spam organized right after installation.
     */
    suspend fun scanExistingSystemInbox(limit: Int = 250): Int = withContext(Dispatchers.IO) {
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
            Telephony.Sms.READ
        )

        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)

                var scanned = 0
                while (cursor.moveToNext() && scanned < limit) {
                    scanned++
                    val sysId = cursor.getLong(idIdx)
                    if (dao.existsBySystemId(sysId)) continue

                    val sender = cursor.getString(addrIdx)?.takeIf { it.isNotBlank() } ?: "Unknown"
                    val body = cursor.getString(bodyIdx) ?: continue
                    val timestamp = cursor.getLong(dateIdx)
                    val isRead = cursor.getInt(readIdx) == 1

                    val contactName = lookupContactName(sender)
                    val result = IranSmsClassifier.classify(
                        sender = sender,
                        body = body,
                        isSavedContact = contactName != null,
                        filterMode = settings.filterMode,
                        customRules = rules,
                        neverBlockOtpEvenFromBlocked = settings.neverBlockOtpEvenFromBlocked
                    )

                    dao.insertMessage(
                        SmsEntity(
                            sender = sender,
                            displayName = contactName,
                            body = body,
                            timestamp = timestamp,
                            category = result.category,
                            isBlocked = result.shouldBlock,
                            otpCode = result.otpCode,
                            spamScore = result.spamScore,
                            reasonSummary = result.reasonSummary,
                            isRead = isRead || result.shouldBlock,
                            systemSmsId = sysId
                        )
                    )
                    importedCount++
                }
            }
        }

        importedCount
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
                reasonSummary = "Restored by you & sender added to whitelist.",
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
                reasonSummary = "Manually marked as spam & sender blocked (OTPs remain protected)."
            )
        )
        dao.insertRule(
            RuleEntity(
                pattern = entity.sender,
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

    suspend fun sendSmsMessage(destination: String, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val parts = smsManager.divideMessage(text)
                smsManager.sendMultipartTextMessage(destination.trim(), null, parts, null, null)

                if (isDefaultSmsApp(context)) {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, destination.trim())
                        put(Telephony.Sms.BODY, text)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.READ, 1)
                    }
                    runCatching {
                        context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                    }
                }
                Unit
            }
        }

    private fun lookupContactName(sender: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        if (sender.count { it.isDigit() } < 7) return null

        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(sender)
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

    companion object {
        fun isDefaultSmsApp(context: Context): Boolean {
            return runCatching {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }.getOrDefault(false)
        }

        fun copyOtpToClipboard(context: Context, otpCode: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OTP Code", otpCode))
        }
    }
}
