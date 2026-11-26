package ir.parsshield.smsblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ir.parsshield.smsblocker.ParsShieldApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * OS-Level Pre-Notification SMS Interceptor (SMS_DELIVER_ACTION).
 *
 * When ParsShield is set as Default SMS App:
 *  1. Android Telephony delivers incoming SMS ONLY to this receiver BEFORE any UI or notification exists.
 *  2. Stock SMS apps (Google Messages / Samsung Messages / Xiaomi Messaging) never receive SMS_DELIVER
 *     and never play a chime or vibrate.
 *  3. IranSmsClassifier evaluates the message in < 2ms:
 *     - Advertisement / Spam / Phishing -> silently blocked/dropped; NotificationManager is NEVER called!
 *     - OTP / رمز پویا / Bank / Personal -> saved & notified immediately.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return

        val sender = parts.firstOrNull()?.displayOriginatingAddress?.takeIf { it.isNotBlank() }
            ?: "Unknown"
        val fullBody = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        if (fullBody.isBlank()) return

        val pendingResult = goAsync()
        val app = context.applicationContext as ParsShieldApp

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val outcome = app.smsRepository.processIncomingSms(
                    sender = sender,
                    body = fullBody,
                    timestamp = timestamp
                )
                // HARD ZERO-NOTIFICATION GUARANTEE:
                // Only non-blocked messages ever reach NotificationHelper.
                if (!outcome.wasDuplicate && !outcome.isBlocked && outcome.entity != null) {
                    NotificationHelper.showAllowedSmsNotification(
                        context = context,
                        entity = outcome.entity
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
