package ir.parsshield.smsblocker.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import ir.parsshield.smsblocker.ParsShieldApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles quick-reply SMS during incoming phone calls (RESPOND_VIA_MESSAGE),
 * required by Android's RoleManager.ROLE_SMS specification.
 */
class HeadlessSmsSendService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val uri = intent.data
            val recipient = uri?.schemeSpecificPart?.substringBefore('?')
            val message = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!recipient.isNullOrBlank() && !message.isNullOrBlank()) {
                val app = applicationContext as ParsShieldApp
                scope.launch {
                    app.smsRepository.sendSmsMessage(recipient, message)
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
