package ir.parsshield.smsblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import ir.parsshield.smsblocker.data.SmsRepository

class CopyOtpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_OTP_CODE) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        SmsRepository.copyOtpToClipboard(context, code)
        Toast.makeText(context, "OTP copied: $code", Toast.LENGTH_SHORT).show()

        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }

    companion object {
        const val EXTRA_OTP_CODE = "extra_otp_code"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
