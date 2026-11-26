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
 * MODE A — Smart Silent Relay Receiver (Keep Your Phone's Stock Dual-SIM SMS App!).
 *
 * How it achieves ZERO ad notifications WITHOUT replacing your stock SMS app:
 *  1. You keep Samsung Messages / Xiaomi Messaging / Google Messages as your Default SMS App
 *     (preserving 100% of its native Dual-SIM selector, MMS, RCS, and UI).
 *  2. You turn OFF notifications for that stock SMS app in Android Settings (1 tap inside ParsShield).
 *  3. Android broadcasts SMS_RECEIVED_ACTION to ParsShield in real time:
 *     - Advertisement / Phishing -> ParsShield posts NO notification -> 0 notifications, 0 sound!
 *     - OTP / رمز پویا / Bank / Personal -> ParsShield immediately posts the notification (with
 *       1-Tap Copy OTP, and tapping the notification opens the chat directly inside your stock SMS app!).
 */
class SmsReceivedRelayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // If ParsShield IS the Default SMS App, SmsDeliverReceiver handles SMS exclusively.
        if (SmsRepository.isDefaultSmsApp(context)) return

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
