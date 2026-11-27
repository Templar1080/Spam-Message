package ir.parsshield.smsblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import ir.parsshield.smsblocker.ParsShieldApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val app = context.applicationContext as ParsShieldApp
        val pendingResult = goAsync()

        when (intent.action) {
            ACTION_DIRECT_REPLY -> {
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_TEXT_REPLY)
                    ?.toString()
                    ?.trim()
                val sender = intent.getStringExtra(EXTRA_SENDER)
                if (!replyText.isNullOrEmpty() && !sender.isNullOrEmpty()) {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            app.smsRepository.sendSmsMessage(sender, replyText)
                            if (notificationId != -1) {
                                NotificationManagerCompat.from(context).cancel(notificationId)
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                    return
                }
            }

            ACTION_BLOCK_SENDER -> {
                val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
                if (messageId > 0L) {
                    if (notificationId != -1) {
                        NotificationManagerCompat.from(context).cancel(notificationId)
                    }
                    Toast.makeText(context, "فرستنده بلاک شد / Sender blocked", Toast.LENGTH_SHORT).show()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            app.smsRepository.blockSenderById(messageId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                    return
                }
            }
        }

        pendingResult.finish()
    }

    companion object {
        const val ACTION_DIRECT_REPLY = "ir.parsshield.smsblocker.ACTION_DIRECT_REPLY"
        const val ACTION_BLOCK_SENDER = "ir.parsshield.smsblocker.ACTION_BLOCK_SENDER"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
