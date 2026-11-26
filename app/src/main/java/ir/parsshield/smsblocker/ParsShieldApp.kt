package ir.parsshield.smsblocker

import android.app.Application
import ir.parsshield.smsblocker.data.AppDatabase
import ir.parsshield.smsblocker.data.PreferencesRepository
import ir.parsshield.smsblocker.data.ShieldSettings
import ir.parsshield.smsblocker.data.SmsRepository
import ir.parsshield.smsblocker.engine.CustomRule
import ir.parsshield.smsblocker.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ParsShieldApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var smsRepository: SmsRepository
        private set

    @Volatile
    var cachedSettings: ShieldSettings = ShieldSettings()
        private set

    @Volatile
    var cachedRules: List<CustomRule> = emptyList()
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferencesRepository = PreferencesRepository(this)
        smsRepository = SmsRepository(this, database.smsDao(), preferencesRepository)
        NotificationHelper.ensureChannelsCreated(this)

        appScope.launch {
            preferencesRepository.settingsFlow.collectLatest { settings ->
                cachedSettings = settings
            }
        }
        appScope.launch {
            smsRepository.allRules.collectLatest { ruleEntities ->
                cachedRules = ruleEntities.map { it.toDomain() }
            }
        }
    }
}
