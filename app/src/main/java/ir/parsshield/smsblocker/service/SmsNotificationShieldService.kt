package ir.parsshield.smsblocker.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import ir.parsshield.smsblocker.ParsShieldApp
import ir.parsshield.smsblocker.data.SmsRepository
import ir.parsshield.smsblocker.engine.SmsCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mode A — Companion Notification Shield.
 * Lets users keep Google Messages, Samsung Messages, or Xiaomi Messaging as their default SMS app!
 * Intercepts incoming SMS notifications in real time:
 *  - Spam / Ads / Phishing -> immediately dismissed via cancelNotification(sbn.key) & saved to Blocked Vault
 *  - OTP Codes -> NEVER dismissed; optionally posts a 1-tap "Copy OTP" action helper
 *  - Bank / Service / Personal -> left completely untouched
 */
class SmsNotificationShieldService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notificationSbn = sbn ?: return
        val pkg = notificationSbn.packageName ?: return

        // Never inspect our own notifications or run duplicate logic when ParsShield is already Default SMS app
        if (pkg == packageName || SmsRepository.isDefaultSmsApp(this)) return

        val defaultSmsPkg = runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull()
        if (pkg != defaultSmsPkg && pkg !in KNOWN_OEM_SMS_PACKAGES) return

        val notification = notificationSbn.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val extracted = extractSenderAndBody(notification) ?: return
        val (sender, body) = extracted
        if (body.isBlank()) return

        val app = applicationContext as ParsShieldApp
        serviceScope.launch {
            val settings = app.preferencesRepository.getSnapshot()
            if (!settings.shieldEnabled) return@launch

            val outcome = app.smsRepository.processIncomingSms(
                sender = sender,
                body = body,
                timestamp = notificationSbn.postTime,
                writeAllowedToSystemProvider = false
            )

            if (outcome.entity.isBlocked) {
                // Instantly swipe/suppress the spam notification from the status bar
                runCatching { cancelNotification(notificationSbn.key) }
            } else if (
                !outcome.wasDuplicate &&
                outcome.entity.category == SmsCategory.OTP &&
                outcome.entity.otpCode != null &&
                settings.otpQuickNotificationBanner
            ) {
                NotificationHelper.showAllowedSmsNotification(
                    context = this@SmsNotificationShieldService,
                    entity = outcome.entity,
                    companionOtpHelperOnly = true
                )
            }
        }
    }

    private fun extractSenderAndBody(notification: Notification): Pair<String, String>? {
        // 1. Prefer NotificationCompat.MessagingStyle for modern Google/Samsung Messages threads
        val messagingStyle =
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val latestMsg = messagingStyle?.messages?.lastOrNull()
        if (latestMsg != null) {
            val text = latestMsg.text?.toString()?.trim().orEmpty()
            val personName = latestMsg.person?.name?.toString()?.trim()
            val convTitle = messagingStyle.conversationTitle?.toString()?.trim()
            val sender = personName?.takeIf { it.isNotEmpty() }
                ?: convTitle?.takeIf { it.isNotEmpty() }
                ?: notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                ?: "Unknown"
            if (text.isNotEmpty()) return sender to text
        }

        // 2. Fallback to standard notification extras (MIUI, Huawei, AOSP MMS)
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: "Unknown"
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: return null

        return title to body
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private val KNOWN_OEM_SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.miui.smspush",
            "com.xiaomi.mms",
            "com.huawei.message",
            "com.hihonor.mms",
            "com.oneplus.mms",
            "com.coloros.mms",
            "com.oppo.mms"
        )

        fun isNotificationAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, SmsNotificationShieldService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == myComponent }
        }
    }
}
