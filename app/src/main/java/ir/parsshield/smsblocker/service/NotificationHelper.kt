package ir.parsshield.smsblocker.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.parsshield.smsblocker.MainActivity
import ir.parsshield.smsblocker.R
import ir.parsshield.smsblocker.data.SmsEntity
import ir.parsshield.smsblocker.engine.SmsCategory

object NotificationHelper {

    const val CHANNEL_OTP = "parsshield_otp_codes"
    const val CHANNEL_MESSAGES = "parsshield_allowed_sms"

    fun ensureChannelsCreated(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val otpChannel = NotificationChannel(
            CHANNEL_OTP,
            "OTP & Verification Codes",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-priority alerts for OTP login codes and Shaparak Dynamic PINs"
            enableVibration(true)
        }

        val msgChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Personal, Bank & Service SMS",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for verified personal, bank, and service messages"
        }

        manager.createNotificationChannel(otpChannel)
        manager.createNotificationChannel(msgChannel)
    }

    fun showAllowedSmsNotification(
        context: Context,
        entity: SmsEntity,
        companionOtpHelperOnly: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannelsCreated(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            entity.id.toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isOtp = entity.category == SmsCategory.OTP && !entity.otpCode.isNullOrBlank()
        val channelId = if (isOtp) CHANNEL_OTP else CHANNEL_MESSAGES
        val senderLabel = entity.displayName ?: entity.sender

        val title = when {
            companionOtpHelperOnly && isOtp -> "OTP Code: ${entity.otpCode} ($senderLabel)"
            isOtp -> "OTP ${entity.otpCode} • $senderLabel"
            entity.category == SmsCategory.BANK_ALERT -> "Bank • $senderLabel"
            else -> senderLabel
        }

        val notificationId = (entity.id.takeIf { it > 0 } ?: System.currentTimeMillis()).toInt()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(title)
            .setContentText(entity.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(entity.body))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(
                if (isOtp) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        if (companionOtpHelperOnly) {
            builder.setSilent(true)
        }

        if (isOtp && entity.otpCode != null) {
            val copyIntent = Intent(context, CopyOtpReceiver::class.java).apply {
                putExtra(CopyOtpReceiver.EXTRA_OTP_CODE, entity.otpCode)
                putExtra(CopyOtpReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val copyPending = PendingIntent.getBroadcast(
                context,
                notificationId + 10_000,
                copyIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                R.drawable.ic_shield_notification,
                "Copy ${entity.otpCode}",
                copyPending
            )
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
