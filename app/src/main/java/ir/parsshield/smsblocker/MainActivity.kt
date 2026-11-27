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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import ir.parsshield.smsblocker.data.SmsRepository
import ir.parsshield.smsblocker.service.NotificationHelper
import ir.parsshield.smsblocker.service.SmsNotificationShieldService
import ir.parsshield.smsblocker.ui.MainScreen
import ir.parsshield.smsblocker.ui.ParsShieldViewModel
import ir.parsshield.smsblocker.ui.theme.ParsShieldTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ParsShieldViewModel by viewModels {
        ParsShieldViewModel.Factory(application)
    }

    private var isDefaultSmsApp by mutableStateOf(false)
    private var isNotificationListenerEnabled by mutableStateOf(false)
    private var hasSmsPermissions by mutableStateOf(false)

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshSystemStates()
        viewModel.refreshSimCards()
        if (hasSmsPermissions) {
            viewModel.scanDeviceInbox()
        }
    }

    private val defaultSmsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshSystemStates()
        viewModel.refreshSimCards()
        if (isDefaultSmsApp) {
            requestAllSmsAndDualSimPermissions()
            viewModel.scanDeviceInbox()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshSystemStates()
        NotificationHelper.ensureChannelsCreated(this)

        // Request POST_NOTIFICATIONS right away on Android 13+ so ParsShield can post OTP/Allowed alerts!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }

        val initialRecipient = intent?.data?.schemeSpecificPart
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }

        val initialOpenThread = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_THREAD_ADDRESS)

        setContent {
            ParsShieldTheme {
                MainScreen(
                    viewModel = viewModel,
                    isDefaultSmsApp = isDefaultSmsApp,
                    isNotificationListenerEnabled = isNotificationListenerEnabled,
                    hasSmsPermissions = hasSmsPermissions,
                    initialComposeRecipient = initialRecipient,
                    initialOpenThreadAddress = initialOpenThread,
                    onRequestPermissions = ::requestAllSmsAndDualSimPermissions,
                    onOpenAppInfoForRestrictedSettings = ::openAppInfoForRestrictedSettings,
                    onOpenNotificationListenerSettings = ::openNotificationListenerSettings,
                    onOpenStockSmsNotificationSettings = ::openStockSmsNotificationSettings,
                    onOpenDefaultAppsSettings = ::openDefaultAppsSettings,
                    onOpenSenderInStockSmsApp = ::openSenderInStockSmsApp,
                    onRequestDefaultSmsRole = ::requestDefaultSmsRole
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemStates()
        viewModel.refreshSimCards()
    }

    private fun refreshSystemStates() {
        isDefaultSmsApp = SmsRepository.isDefaultSmsApp(this)
        isNotificationListenerEnabled = SmsNotificationShieldService.isNotificationAccessGranted(this)
        hasSmsPermissions = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAllSmsAndDualSimPermissions() {
        val perms = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionsLauncher.launch(perms)
    }

    private fun openAppInfoForRestrictedSettings() {
        Toast.makeText(
            this,
            "در صفحه بازشده روی ۳ نقطه بالا (⋮) بزنید و «مجاز کردن تنظیمات محدودشده» را انتخاب کنید",
            Toast.LENGTH_LONG
        ).show()
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
    }

    private fun openNotificationListenerSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }.onFailure {
            openAppInfoForRestrictedSettings()
        }
    }

    private fun openStockSmsNotificationSettings() {
        val currentDefaultPkg = Telephony.Sms.getDefaultSmsPackage(this)
        if (currentDefaultPkg.isNullOrBlank() || currentDefaultPkg == packageName) {
            Toast.makeText(
                this,
                "ابتدا برنامه پیامک اصلی سامسونگ را به عنوان پیش‌فرض انتخاب کنید.",
                Toast.LENGTH_LONG
            ).show()
            openDefaultAppsSettings()
            return
        }

        Toast.makeText(
            this,
            "در سامسونگ گزینه «بی‌صدا / Silent» را انتخاب کنید و تیک «پاپ‌آپ / Pop-up» را بردارید!",
            Toast.LENGTH_LONG
        ).show()

        val notifIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, currentDefaultPkg)
        }
        runCatching { startActivity(notifIntent) }.onFailure {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", currentDefaultPkg, null)
                    }
                )
            }
        }
    }

    private fun openDefaultAppsSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun openSenderInStockSmsApp(sender: String) {
        val stockPkg = Telephony.Sms.getDefaultSmsPackage(this)
        val smsIntent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("smsto:${Uri.encode(sender)}")
        ).apply {
            if (!stockPkg.isNullOrBlank() && stockPkg != packageName) {
                setPackage(stockPkg)
            }
        }
        runCatching { startActivity(smsIntent) }
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
