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
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import ir.parsshield.smsblocker.MainActivity
import ir.parsshield.smsblocker.R
import ir.parsshield.smsblocker.data.SmsEntity
import ir.parsshield.smsblocker.engine.SmsCategory

/**
 * Posts notifications ONLY for verified non-spam messages (OTPs, Bank alerts, Service & Personal).
 * Blocked advertisements and phishing NEVER reach this class — zero notification is ever created.
 */
object NotificationHelper {

    const val CHANNEL_OTP = "parsshield_otp_codes"
    const val CHANNEL_MESSAGES = "parsshield_allowed_sms"

    fun ensureChannelsCreated(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val otpChannel = NotificationChannel(
            CHANNEL_OTP,
            "OTP & Verification Codes (کد تایید و رمز پویا)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Instant alerts for OTP login codes and Shaparak Dynamic PINs"
            enableVibration(true)
        }

        val msgChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Personal, Bank & Service SMS",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for verified personal, bank, and service messages"
        }

        manager.createNotificationChannel(otpChannel)
        manager.createNotificationChannel(msgChannel)
    }

    fun showAllowedSmsNotification(context: Context, entity: SmsEntity) {
        // Double-safety guard: never post a notification for any blocked entity
        if (entity.isBlocked) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannelsCreated(context)

        val notificationId = (entity.id.takeIf { it > 0 } ?: System.currentTimeMillis()).toInt()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_THREAD_ADDRESS, entity.threadAddress)
        }
        val openPending = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isOtp = entity.category == SmsCategory.OTP && !entity.otpCode.isNullOrBlank()
        val channelId = if (isOtp) CHANNEL_OTP else CHANNEL_MESSAGES
        val senderLabel = entity.displayName ?: entity.sender

        val title = when {
            isOtp -> "کد تایید: ${entity.otpCode} • $senderLabel"
            entity.category == SmsCategory.BANK_ALERT -> "بانک • $senderLabel"
            else -> senderLabel
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(title)
            .setContentText(entity.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(entity.body))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

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
                "کپی کد ${entity.otpCode}",
                copyPending
            )
        } else {
            // Inline Reply action for personal mobile senders
            if (entity.category == SmsCategory.PERSONAL) {
                val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
                    .setLabel("پاسخ پیامک / Reply...")
                    .build()

                val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_DIRECT_REPLY
                    putExtra(NotificationActionReceiver.EXTRA_SENDER, entity.sender)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                }
                // FLAG_MUTABLE is required by Android ONLY for RemoteInput PendingIntents
                val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val replyPending = PendingIntent.getBroadcast(
                    context,
                    notificationId + 20_000,
                    replyIntent,
                    replyFlags
                )
                val replyAction = NotificationCompat.Action.Builder(
                    R.drawable.ic_shield_notification,
                    "پاسخ / Reply",
                    replyPending
                ).addRemoteInput(remoteInput).build()

                builder.addAction(replyAction)
            }

            // 1-Tap "Block Ad" action in case an unknown 09xx sender sent a disguised ad
            val blockIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_BLOCK_SENDER
                putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, entity.id)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val blockPending = PendingIntent.getBroadcast(
                context,
                notificationId + 30_000,
                blockIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                R.drawable.ic_shield_notification,
                "بلاک تبلیغ / Block",
                blockPending
            )
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    const val EXTRA_OPEN_THREAD_ADDRESS = "extra_open_thread_address"
}
