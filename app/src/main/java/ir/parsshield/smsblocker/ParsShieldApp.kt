package ir.parsshield.smsblocker

import android.app.Application
import ir.parsshield.smsblocker.data.AppDatabase
import ir.parsshield.smsblocker.data.PreferencesRepository
import ir.parsshield.smsblocker.data.SmsRepository
import ir.parsshield.smsblocker.service.NotificationHelper

class ParsShieldApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var smsRepository: SmsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferencesRepository = PreferencesRepository(this)
        smsRepository = SmsRepository(this, database.smsDao(), preferencesRepository)
        NotificationHelper.ensureChannelsCreated(this)
    }
}
