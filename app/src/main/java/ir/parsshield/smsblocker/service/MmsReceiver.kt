package ir.parsshield.smsblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Required manifest component so Android's RoleManager recognizes ParsShield as a valid Default SMS App.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MMS is virtually unused on Iranian mobile carriers; stub satisfies RoleManager.ROLE_SMS
    }
}
