package ir.parsshield.smsblocker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    val zeroToleranceCommercialPanels: Boolean = true,
    val dropSpamCompletely: Boolean = false,
    val neverBlockOtpEvenFromBlocked: Boolean = true,
    val autoCopyOtpToClipboard: Boolean = true,
    val persianUi: Boolean = true,
    val vaporizedCount: Int = 0
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SHIELD_ENABLED = booleanPreferencesKey("shield_enabled")
        val FILTER_MODE = stringPreferencesKey("filter_mode")
        val ZERO_TOLERANCE_PANELS = booleanPreferencesKey("zero_tolerance_panels")
        val DROP_SPAM_COMPLETELY = booleanPreferencesKey("drop_spam_completely")
        val NEVER_BLOCK_OTP = booleanPreferencesKey("never_block_otp")
        val AUTO_COPY_OTP = booleanPreferencesKey("auto_copy_otp")
        val PERSIAN_UI = booleanPreferencesKey("persian_ui")
        val VAPORIZED_COUNT = intPreferencesKey("vaporized_count")
    }

    val settingsFlow: Flow<ShieldSettings> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[Keys.FILTER_MODE] ?: FilterMode.ALLOW_OTP_AND_BANK.name
        val filterMode = runCatching { FilterMode.valueOf(modeStr) }
            .getOrDefault(FilterMode.ALLOW_OTP_AND_BANK)
        ShieldSettings(
            shieldEnabled = prefs[Keys.SHIELD_ENABLED] ?: true,
            filterMode = filterMode,
            zeroToleranceCommercialPanels = prefs[Keys.ZERO_TOLERANCE_PANELS] ?: true,
            dropSpamCompletely = prefs[Keys.DROP_SPAM_COMPLETELY] ?: false,
            neverBlockOtpEvenFromBlocked = prefs[Keys.NEVER_BLOCK_OTP] ?: true,
            autoCopyOtpToClipboard = prefs[Keys.AUTO_COPY_OTP] ?: true,
            persianUi = prefs[Keys.PERSIAN_UI] ?: true,
            vaporizedCount = prefs[Keys.VAPORIZED_COUNT] ?: 0
        )
    }

    suspend fun getSnapshot(): ShieldSettings = settingsFlow.first()

    suspend fun setShieldEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHIELD_ENABLED] = enabled }
    }

    suspend fun setFilterMode(mode: FilterMode) {
        context.dataStore.edit { it[Keys.FILTER_MODE] = mode.name }
    }

    suspend fun setZeroTolerancePanels(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ZERO_TOLERANCE_PANELS] = enabled }
    }

    suspend fun setDropSpamCompletely(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DROP_SPAM_COMPLETELY] = enabled }
    }

    suspend fun setNeverBlockOtp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NEVER_BLOCK_OTP] = enabled }
    }

    suspend fun setAutoCopyOtp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_COPY_OTP] = enabled }
    }

    suspend fun setPersianUi(persian: Boolean) {
        context.dataStore.edit { it[Keys.PERSIAN_UI] = persian }
    }

    suspend fun incrementVaporizedCount() {
        context.dataStore.edit { prefs ->
            val curr = prefs[Keys.VAPORIZED_COUNT] ?: 0
            prefs[Keys.VAPORIZED_COUNT] = curr + 1
        }
    }
}
