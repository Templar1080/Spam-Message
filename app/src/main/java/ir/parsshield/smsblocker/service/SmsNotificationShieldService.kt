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
import ir.parsshield.smsblocker.ParsShieldApp
import ir.parsshield.smsblocker.data.SmsRepository
import ir.parsshield.smsblocker.engine.IranSmsClassifier
import ir.parsshield.smsblocker.engine.SmsCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Samsung One UI / Xiaomi / Google Messages Zero-Notification Shield.
 *
 * Why Samsung One UI needed this exact hybrid engine:
 *  - On Samsung One UI, Android locks the master "Allow notifications" switch of the Default SMS
 *    app to ON, AND restricts sideloaded SMS_RECEIVED broadcasts under Auto Blocker.
 *  - However, Samsung allows setting Samsung/Google Messages to "Silent (بی‌صدا)" + no Pop-up!
 *  - When Samsung Messages is set to Silent:
 *      1. Incoming Ad/Spam -> Samsung makes 0 sound & 0 pop-up, and this service cancels the
 *         silent notification in <1ms in pure RAM -> ZERO notifications anywhere!
 *      2. Incoming OTP / رمز پویا / Bank / Personal -> This service cancels Samsung's silent card
 *         and immediately posts ParsShield's loud Alerting Notification (with 1-Tap Copy OTP and
 *         Samsung's original contentIntent so tapping opens the chat inside Samsung Messages!).
 */
class SmsNotificationShieldService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // Never intercept ParsShield's own notifications
        if (pkg == packageName) return

        // If ParsShield IS the Default SMS App, SmsDeliverReceiver handles SMS at the radio layer
        if (SmsRepository.isDefaultSmsApp(this)) return

        val defaultSmsPkg = Telephony.Sms.getDefaultSmsPackage(this)
        val isSmsApp = pkg == defaultSmsPkg || pkg in KNOWN_SMS_PACKAGES
        if (!isSmsApp) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        // If this is a group summary ("2 new messages"), clean it up if no real child cards remain
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            cleanupOrphanedGroupSummaries(pkg)
            return
        }

        val extracted = extractSmsFromNotification(notification) ?: return
        val (senderTitle, fullBody) = extracted
        if (fullBody.isBlank()) return

        val app = applicationContext as ParsShieldApp
        val settings = app.cachedSettings
        if (!settings.shieldEnabled) return

        // FAST SYNCHRONOUS (<0.8ms) RAM CLASSIFICATION:
        // Cancels spam notifications BEFORE any coroutine hop or disk I/O!
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

        if (fastEval.shouldBlock) {
            // ERASE AD NOTIFICATION IMMEDIATELY (<1ms) + erase empty group summary!
            runCatching { cancelNotification(sbn.key) }
            cleanupOrphanedGroupSummaries(pkg)

            serviceScope.launch {
                app.smsRepository.processIncomingSms(
                    sender = senderTitle,
                    body = fullBody,
                    timestamp = sbn.postTime
                )
            }
            return
        }

        // ALLOWED MESSAGE (OTP, رمز پویا, Bank, Service, or Personal):
        // Check whether the user set Samsung/Google Messages to "Silent" (IMPORTANCE_LOW or lower)
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
                if (stockAppIsSilent) {
                    runCatching { cancelNotification(sbn.key) }
                    cleanupOrphanedGroupSummaries(pkg)
                }
                return@launch
            }

            if (stockAppIsSilent) {
                // Replace Samsung's silent card with ParsShield's loud + pop-up alert (preserving
                // Samsung's exact click intent so tapping opens Samsung Messages!)
                runCatching { cancelNotification(sbn.key) }
                cleanupOrphanedGroupSummaries(pkg)
                NotificationHelper.showAllowedSmsNotification(
                    context = this@SmsNotificationShieldService,
                    entity = outcome.entity,
                    openInStockSmsApp = true,
                    originalContentIntent = originalIntent
                )
            } else if (outcome.entity.category == SmsCategory.OTP && !outcome.entity.otpCode.isNullOrBlank()) {
                // Stock app already rang, so replace/post with 1-tap Copy OTP button
                runCatching { cancelNotification(sbn.key) }
                cleanupOrphanedGroupSummaries(pkg)
                NotificationHelper.showAllowedSmsNotification(
                    context = this@SmsNotificationShieldService,
                    entity = outcome.entity,
                    openInStockSmsApp = true,
                    originalContentIntent = originalIntent
                )
            }
        }
    }

    private fun cleanupOrphanedGroupSummaries(smsPkg: String) {
        runCatching {
            val activeForPkg = activeNotifications?.filter { it.packageName == smsPkg }.orEmpty()
            val hasRealChild = activeForPkg.any {
                (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    (it.notification.flags and Notification.FLAG_ONGOING_EVENT) == 0
            }
            if (!hasRealChild) {
                activeForPkg
                    .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 }
                    .forEach { summary -> cancelNotification(summary.key) }
            }
        }
    }

    /**
     * Extracts the newest incoming SMS (sender + full body) across Samsung One UI Messages,
     * Google Messages (MessagingStyle), and Xiaomi/AOSP notifications, safely handling SpannableStrings.
     */
    private fun extractSmsFromNotification(notification: Notification): Pair<String, String>? {
        // 1. MessagingStyle (used by Samsung Messages & Google Messages on Android 8–15)
        runCatching {
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            val newest = style?.messages?.lastOrNull()
            if (newest != null) {
                val msgText = newest.text?.toString()?.trim().orEmpty()
                val msgSender = newest.person?.name?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: style.conversationTitle?.toString()?.trim()
                    ?: notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                    ?: "Unknown"
                if (msgText.isNotEmpty()) {
                    return msgSender to msgText
                }
            }
        }

        val extras: Bundle = notification.extras ?: return null

        // 2. Raw EXTRA_MESSAGES bundle array fallback (for custom OEM MessagingStyle subclasses)
        runCatching {
            @Suppress("DEPRECATION")
            val rawArr = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            val lastBundle = rawArr?.lastOrNull() as? Bundle
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

        // 3. Standard BigText / Text CharSequence fallback (safe for SpannableString!)
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
