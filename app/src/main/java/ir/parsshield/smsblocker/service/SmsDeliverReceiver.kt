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
 * OS-level Default SMS Receiver (SMS_DELIVER_ACTION).
 * Reconstructs multi-part UTF-16 Persian SMS segments before classifying,
 * silently quarantines spam/phishing with zero sound or lock-screen wake,
 * and delivers instant 1-tap OTP notifications for verification codes.
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
                    timestamp = timestamp,
                    writeAllowedToSystemProvider = true
                )
                if (!outcome.wasDuplicate && !outcome.entity.isBlocked) {
                    NotificationHelper.showAllowedSmsNotification(
                        context = context,
                        entity = outcome.entity,
                        companionOtpHelperOnly = false
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
