package ir.parsshield.smsblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import ir.parsshield.smsblocker.data.SmsRepository
import ir.parsshield.smsblocker.service.SmsNotificationShieldService
import ir.parsshield.smsblocker.ui.MainScreen
import ir.parsshield.smsblocker.ui.ParsShieldViewModel
import ir.parsshield.smsblocker.ui.theme.ParsShieldTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ParsShieldViewModel by viewModels {
        ParsShieldViewModel.Factory(application)
    }

    private var isDefaultSmsApp by mutableStateOf(false)
    private var isNotificationShieldActive by mutableStateOf(false)
    private var hasSmsPermissions by mutableStateOf(false)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshSystemStates()
        if (hasSmsPermissions) {
            viewModel.scanDeviceInbox()
        }
    }

    private val defaultSmsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshSystemStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshSystemStates()

        val initialRecipient = intent?.data?.schemeSpecificPart
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }

        setContent {
            ParsShieldTheme {
                MainScreen(
                    viewModel = viewModel,
                    isDefaultSmsApp = isDefaultSmsApp,
                    isNotificationShieldActive = isNotificationShieldActive,
                    hasSmsPermissions = hasSmsPermissions,
                    initialComposeRecipient = initialRecipient,
                    onRequestPermissions = ::requestSmsAndNotificationPermissions,
                    onOpenNotificationAccess = ::openNotificationListenerSettings,
                    onRequestDefaultSmsRole = ::requestDefaultSmsRole,
                    onOpenAppInfoSettings = ::openAppInfoSettings
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemStates()
    }

    private fun refreshSystemStates() {
        isDefaultSmsApp = SmsRepository.isDefaultSmsApp(this)
        isNotificationShieldActive = SmsNotificationShieldService.isNotificationAccessGranted(this)
        hasSmsPermissions = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsAndNotificationPermissions() {
        val perms = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionsLauncher.launch(perms)
    }

    private fun openNotificationListenerSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun openAppInfoSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }
    }

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                defaultSmsRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                return
            }
        }
        val legacyIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        }
        defaultSmsRoleLauncher.launch(legacyIntent)
    }
}
