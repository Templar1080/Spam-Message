package ir.parsshield.smsblocker.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.parsshield.smsblocker.ParsShieldApp
import ir.parsshield.smsblocker.data.RuleEntity
import ir.parsshield.smsblocker.data.ShieldSettings
import ir.parsshield.smsblocker.data.SmsEntity
import ir.parsshield.smsblocker.data.SmsRepository
import ir.parsshield.smsblocker.engine.ClassificationResult
import ir.parsshield.smsblocker.engine.FilterMode
import ir.parsshield.smsblocker.engine.IranSmsClassifier
import ir.parsshield.smsblocker.engine.RuleAction
import ir.parsshield.smsblocker.engine.RuleTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConversationThread(
    val threadAddress: String,
    val rawSender: String,
    val displayName: String?,
    val latestMessage: SmsEntity,
    val unreadCount: Int,
    val messageCount: Int
)

class ParsShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ParsShieldApp
    private val smsRepository = app.smsRepository
    private val prefs = app.preferencesRepository

    val messages: StateFlow<List<SmsEntity>> = smsRepository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<RuleEntity>> = smsRepository.allRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<ShieldSettings> = prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShieldSettings())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun scanDeviceInbox() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val imported = smsRepository.scanExistingSystemInbox(limit = 350)
                val fa = settings.value.persianUi
                Toast.makeText(
                    app,
                    if (imported > 0) {
                        if (fa) "$imported پیامک بررسی و دسته‌بندی شد"
                        else "Classified $imported SMS messages"
                    } else {
                        if (fa) "صندوق پیامک به‌روز است"
                        else "Inbox is already up to date"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun markThreadRead(threadAddress: String) {
        viewModelScope.launch {
            smsRepository.markThreadAsRead(threadAddress)
        }
    }

    fun copyOtp(code: String) {
        SmsRepository.copyOtpToClipboard(app, code)
        val fa = settings.value.persianUi
        Toast.makeText(
            app,
            if (fa) "کد کپی شد: $code" else "Copied OTP: $code",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun restoreNotSpam(entity: SmsEntity) {
        viewModelScope.launch {
            smsRepository.markNotSpamAndWhitelist(entity)
            val fa = settings.value.persianUi
            Toast.makeText(
                app,
                if (fa) "پیام بازگردانی و فرستنده مجاز شد"
                else "Restored & whitelisted ${entity.sender}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun blockSpamMessage(entity: SmsEntity) {
        viewModelScope.launch {
            smsRepository.markAsSpamAndBlockSender(entity)
            val fa = settings.value.persianUi
            Toast.makeText(
                app,
                if (fa) "فرستنده بلاک شد (کدهای تایید همچنان امن هستند)"
                else "Blocked ${entity.sender} (OTPs remain protected)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun blockThread(threadAddress: String, rawSender: String) {
        viewModelScope.launch {
            smsRepository.blockThreadSender(threadAddress, rawSender)
            val fa = settings.value.persianUi
            Toast.makeText(
                app,
                if (fa) "فرستنده بلاک شد" else "Sender blocked",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun deleteMessage(entity: SmsEntity) {
        viewModelScope.launch {
            smsRepository.deleteMessage(entity)
        }
    }

    fun clearAllBlocked() {
        viewModelScope.launch {
            smsRepository.clearAllBlocked()
            val fa = settings.value.persianUi
            Toast.makeText(
                app,
                if (fa) "صندوق هرزنامه‌ها پاک شد" else "Cleared blocked spam vault",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun addCustomRule(pattern: String, target: RuleTarget, action: RuleAction) {
        viewModelScope.launch {
            smsRepository.addRule(pattern, target, action)
        }
    }

    fun deleteCustomRule(rule: RuleEntity) {
        viewModelScope.launch {
            smsRepository.deleteRule(rule)
        }
    }

    fun sendSms(destination: String, text: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = smsRepository.sendSmsMessage(destination, text)
            val fa = settings.value.persianUi
            if (res.isSuccess) {
                Toast.makeText(
                    app,
                    if (fa) "پیامک ارسال شد" else "SMS sent",
                    Toast.LENGTH_SHORT
                ).show()
                onComplete(true)
            } else {
                Toast.makeText(
                    app,
                    if (fa) "خطا در ارسال پیامک"
                    else "Failed: ${res.exceptionOrNull()?.localizedMessage ?: "Permission required"}",
                    Toast.LENGTH_LONG
                ).show()
                onComplete(false)
            }
        }
    }

    fun setShieldEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setShieldEnabled(enabled) }
    }

    fun setFilterMode(mode: FilterMode) {
        viewModelScope.launch { prefs.setFilterMode(mode) }
    }

    fun setZeroTolerancePanels(enabled: Boolean) {
        viewModelScope.launch { prefs.setZeroTolerancePanels(enabled) }
    }

    fun setDropSpamCompletely(enabled: Boolean) {
        viewModelScope.launch { prefs.setDropSpamCompletely(enabled) }
    }

    fun setNeverBlockOtp(enabled: Boolean) {
        viewModelScope.launch { prefs.setNeverBlockOtp(enabled) }
    }

    fun setAutoCopyOtp(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoCopyOtp(enabled) }
    }

    fun toggleUiLanguage() {
        viewModelScope.launch {
            prefs.setPersianUi(!settings.value.persianUi)
        }
    }

    fun testMessageLive(sender: String, body: String): ClassificationResult {
        val currentSettings = settings.value
        val domainRules = rules.value.map { it.toDomain() }
        return IranSmsClassifier.classify(
            sender = sender,
            body = body,
            isSavedContact = false,
            filterMode = currentSettings.filterMode,
            customRules = domainRules,
            neverBlockOtpEvenFromBlocked = currentSettings.neverBlockOtpEvenFromBlocked,
            zeroToleranceCommercialPanels = currentSettings.zeroToleranceCommercialPanels
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ParsShieldViewModel(application) as T
        }
    }
}
