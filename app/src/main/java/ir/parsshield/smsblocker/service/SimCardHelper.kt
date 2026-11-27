package ir.parsshield.smsblocker.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimCardInfo(
    val subscriptionId: Int,
    val slotNumber: Int, // 1 for SIM 1, 2 for SIM 2
    val carrierName: String
) {
    fun label(persianUi: Boolean): String {
        val cleanCarrier = carrierName.trim().takeIf { it.isNotEmpty() }
        return if (persianUi) {
            if (cleanCarrier != null) "سیم $slotNumber ($cleanCarrier)" else "سیم‌کارت $slotNumber"
        } else {
            if (cleanCarrier != null) "SIM $slotNumber ($cleanCarrier)" else "SIM $slotNumber"
        }
    }
}

object SimCardHelper {

    @SuppressLint("MissingPermission")
    fun getActiveSimCards(context: Context): List<SimCardInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return runCatching {
            val subManager = context.getSystemService(SubscriptionManager::class.java)
            val subs = subManager?.activeSubscriptionInfoList.orEmpty()
            subs.sortedBy { it.simSlotIndex }.map { info ->
                SimCardInfo(
                    subscriptionId = info.subscriptionId,
                    slotNumber = info.simSlotIndex + 1,
                    carrierName = info.carrierName?.toString().orEmpty()
                )
            }
        }.getOrDefault(emptyList())
    }

    fun extractSubscriptionIdFromIntent(intent: Intent): Int? {
        val extras = intent.extras ?: return null
        val candidates = listOf(
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "subscription",
            "sub_id",
            "simId"
        )
        for (key in candidates) {
            if (extras.containsKey(key)) {
                val value = extras.getInt(key, -1)
                if (value >= 0) return value
            }
        }
        return null
    }

    fun resolveSimSlotNumber(context: Context, subscriptionId: Int?): Int? {
        if (subscriptionId == null || subscriptionId < 0) return null
        val match = getActiveSimCards(context).firstOrNull { it.subscriptionId == subscriptionId }
        if (match != null) return match.slotNumber
        return if (subscriptionId in 0..1) subscriptionId + 1 else null
    }
}
