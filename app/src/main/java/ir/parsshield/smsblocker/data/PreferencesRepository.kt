package ir.parsshield.smsblocker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.parsshield.smsblocker.engine.FilterMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "parsshield_prefs")

data class ShieldSettings(
    val shieldEnabled: Boolean = true,
    val filterMode: FilterMode = FilterMode.ALLOW_OTP_AND_BANK,
    val neverBlockOtpEvenFromBlocked: Boolean = true,
    val autoCopyOtpToClipboard: Boolean = false,
    val otpQuickNotificationBanner: Boolean = true
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SHIELD_ENABLED = booleanPreferencesKey("shield_enabled")
        val FILTER_MODE = stringPreferencesKey("filter_mode")
        val NEVER_BLOCK_OTP = booleanPreferencesKey("never_block_otp")
        val AUTO_COPY_OTP = booleanPreferencesKey("auto_copy_otp")
        val OTP_QUICK_BANNER = booleanPreferencesKey("otp_quick_banner")
    }

    val settingsFlow: Flow<ShieldSettings> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[Keys.FILTER_MODE] ?: FilterMode.ALLOW_OTP_AND_BANK.name
        val filterMode = runCatching { FilterMode.valueOf(modeStr) }
            .getOrDefault(FilterMode.ALLOW_OTP_AND_BANK)
        ShieldSettings(
            shieldEnabled = prefs[Keys.SHIELD_ENABLED] ?: true,
            filterMode = filterMode,
            neverBlockOtpEvenFromBlocked = prefs[Keys.NEVER_BLOCK_OTP] ?: true,
            autoCopyOtpToClipboard = prefs[Keys.AUTO_COPY_OTP] ?: false,
            otpQuickNotificationBanner = prefs[Keys.OTP_QUICK_BANNER] ?: true
        )
    }

    suspend fun getSnapshot(): ShieldSettings = settingsFlow.first()

    suspend fun setShieldEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHIELD_ENABLED] = enabled }
    }

    suspend fun setFilterMode(mode: FilterMode) {
        context.dataStore.edit { it[Keys.FILTER_MODE] = mode.name }
    }

    suspend fun setNeverBlockOtp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NEVER_BLOCK_OTP] = enabled }
    }

    suspend fun setAutoCopyOtp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_COPY_OTP] = enabled }
    }

    suspend fun setOtpQuickBanner(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OTP_QUICK_BANNER] = enabled }
    }
}
