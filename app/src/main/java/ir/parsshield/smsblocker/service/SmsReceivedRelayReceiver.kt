package ir.parsshield.smsblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ir.parsshield.smsblocker.ParsShieldApp
import ir.parsshield.smsblocker.data.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fallback SMS_RECEIVED Relay Receiver (used only when neither Default SMS nor Notification Shield
 * is active). Yields immediately when SmsNotificationShieldService is active so notifications are
 * never duplicated.
 */
class SmsReceivedRelayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (SmsRepository.isDefaultSmsApp(context)) return
        if (SmsNotificationShieldService.isNotificationAccessGranted(context)) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return

        val sender = parts.firstOrNull()?.displayOriginatingAddress?.takeIf { it.isNotBlank() }
            ?: "Unknown"
        val fullBody = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        if (fullBody.isBlank()) return

        val subId = SimCardHelper.extractSubscriptionIdFromIntent(intent)
        val simSlot = SimCardHelper.resolveSimSlotNumber(context, subId)

        val pendingResult = goAsync()
        val app = context.applicationContext as ParsShieldApp

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val outcome = app.smsRepository.processIncomingSms(
                    sender = sender,
                    body = fullBody,
                    timestamp = timestamp,
                    simSlot = simSlot
                )
                if (!outcome.wasDuplicate && !outcome.isBlocked && outcome.entity != null) {
                    NotificationHelper.showAllowedSmsNotification(
                        context = context,
                        entity = outcome.entity,
                        openInStockSmsApp = true
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
