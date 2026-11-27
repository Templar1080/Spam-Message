package ir.parsshield.smsblocker.service

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.parsshield.smsblocker.ParsShieldApp
import ir.parsshield.smsblocker.data.SmsRepository
import ir.parsshield.smsblocker.engine.IranSmsClassifier
import ir.parsshield.smsblocker.engine.SmsCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Samsung One UI / Xiaomi / Google Messages Notification Shield (v2.2.1).
 *
 * STRICT SAFETY INVARIANTS:
 *  1. NEVER touches notifications from non-SMS apps (WhatsApp, Telegram, Instagram, System, etc.).
 *  2. NEVER cancels FLAG_GROUP_SUMMARY or multi-thread InboxStyle summary notifications (because
 *     canceling a group summary on Android AOSP cascades and closes ALL child notifications!).
 *  3. NEVER calls cancelNotification() on allowed messages (Personal, OTP, Bank, Service).
 *     cancelNotification(sbn.key) is ONLY called when fastEval.shouldBlock == true!
 */
class SmsNotificationShieldService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // 1. Never touch ParsShield's own notifications
        if (pkg == packageName) return

        // 2. If ParsShield is Default SMS App, SmsDeliverReceiver handles SMS at the radio layer
        if (SmsRepository.isDefaultSmsApp(this)) return

        // 3. Strictly inspect ONLY the user's SMS app
        val defaultSmsPkg = Telephony.Sms.getDefaultSmsPackage(this)
        val isSmsApp = pkg == defaultSmsPkg || pkg in KNOWN_SMS_PACKAGES
        if (!isSmsApp) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        // 4. NEVER cancel or classify group summary / InboxStyle multi-thread summary notifications!
        // Canceling a summary notification on Samsung/Android closes ALL unread SMS notifications!
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val template = notification.extras?.getString(Notification.EXTRA_TEMPLATE)
        if (template == "android.app.Notification\$InboxStyle") return

        val extracted = extractSmsFromNotification(notification) ?: return
        val (senderTitle, fullBody) = extracted
        if (fullBody.isBlank()) return

        val app = applicationContext as ParsShieldApp
        val settings = app.cachedSettings
        if (!settings.shieldEnabled) return

        val isKnownContact = app.smsRepository.isKnownContactByNumberOrName(senderTitle)
        val fastEval = IranSmsClassifier.classify(
            sender = senderTitle,
            body = fullBody,
            isSavedContact = isKnownContact,
            filterMode = settings.filterMode,
            customRules = app.cachedRules,
            neverBlockOtpEvenFromBlocked = settings.neverBlockOtpEvenFromBlocked,
            zeroToleranceCommercialPanels = settings.zeroToleranceCommercialPanels
        )

        // ONLY SPAM / AD / PHISHING IS EVER CANCELED!
        if (fastEval.shouldBlock) {
            runCatching { cancelNotification(sbn.key) }

            serviceScope.launch {
                app.smsRepository.processIncomingSms(
                    sender = senderTitle,
                    body = fullBody,
                    timestamp = sbn.postTime
                )
            }
            return
        }

        // ALLOWED MESSAGE (Personal, OTP, رمز پویا, Bank, Service):
        // NEVER cancel sbn.key! Leave Samsung Messages' own notification 100% intact!
        val ranking = Ranking()
        val hasRanking = runCatching { currentRanking?.getRanking(sbn.key, ranking) == true }
            .getOrDefault(false)
        val ch = if (hasRanking) ranking.channel else null
        val channelSilent = ch != null && ch.sound == null && !ch.shouldVibrate()
        val stockAppIsSilent = hasRanking &&
            (ranking.importance <= NotificationManager.IMPORTANCE_LOW || channelSilent)

        val originalIntent = notification.contentIntent

        serviceScope.launch {
            val outcome = app.smsRepository.processIncomingSms(
                sender = senderTitle,
                body = fullBody,
                timestamp = sbn.postTime
            )
            if (outcome.wasDuplicate || outcome.isBlocked || outcome.entity == null) {
                return@launch
            }

            val canPostParsShieldNotif = NotificationManagerCompat
                .from(this@SmsNotificationShieldService)
                .areNotificationsEnabled()

            if (!canPostParsShieldNotif) return@launch

            // If the user set Samsung Messages to Silent, ring & pop up via ParsShield for allowed SMS!
            // Or if it's an OTP, show the 1-Tap "Copy OTP" action banner alongside it.
            if (stockAppIsSilent ||
                (outcome.entity.category == SmsCategory.OTP && !outcome.entity.otpCode.isNullOrBlank())
            ) {
                NotificationHelper.showAllowedSmsNotification(
                    context = this@SmsNotificationShieldService,
                    entity = outcome.entity,
                    openInStockSmsApp = true,
                    originalContentIntent = originalIntent
                )
            }
        }
    }

    private fun extractSmsFromNotification(notification: Notification): Pair<String, String>? {
        val extras: Bundle = notification.extras ?: return null

        // Multi-line summary notification ("3 new messages") — never cancel!
        if (extras.containsKey(Notification.EXTRA_TEXT_LINES)) return null

        // 1. MessagingStyle (Samsung Messages & Google Messages single-conversation notification)
        runCatching {
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (style != null) {
                val distinctSenders = style.messages
                    .mapNotNull { it.person?.name?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    .distinct()
                if (distinctSenders.size > 1) return null

                val newest = style.messages.lastOrNull()
                if (newest != null) {
                    val msgText = newest.text?.toString()?.trim().orEmpty()
                    val msgSender = newest.person?.name?.toString()?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: style.conversationTitle?.toString()?.trim()
                        ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                        ?: "Unknown"
                    if (msgText.isNotEmpty()) {
                        return msgSender to msgText
                    }
                }
            }
        }

        // 2. Raw EXTRA_MESSAGES bundle array fallback
        runCatching {
            @Suppress("DEPRECATION")
            val rawArr = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (rawArr != null && rawArr.isNotEmpty()) {
                val bundles = rawArr.mapNotNull { it as? Bundle }
                val senders = bundles
                    .mapNotNull { it.getCharSequence("sender")?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    .distinct()
                if (senders.size > 1) return null

                val lastBundle = bundles.lastOrNull()
                if (lastBundle != null) {
                    val text = lastBundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                    val sender = lastBundle.getCharSequence("sender")?.toString()?.trim()
                        ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                        ?: "Unknown"
                    if (text.isNotEmpty()) {
                        return sender to text
                    }
                }
            }
        }

        // 3. Standard single-notification BigText / Text fallback
        val sender = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: "Unknown"

        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: ""

        if (body.isEmpty()) return null
        return sender to body
    }

    companion object {
        private val KNOWN_SMS_PACKAGES = setOf(
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.miui.smsextra"
        )

        fun isNotificationAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            val expected = ComponentName(context, SmsNotificationShieldService::class.java)
                .flattenToString()
            return flat.split(':').any { it == expected }
        }
    }
}
