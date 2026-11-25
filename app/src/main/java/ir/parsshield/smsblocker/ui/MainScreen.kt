@file:OptIn(ExperimentalMaterial3Api::class)

package ir.parsshield.smsblocker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.parsshield.smsblocker.engine.SmsCategory
import ir.parsshield.smsblocker.ui.screens.ComposeSmsDialog
import ir.parsshield.smsblocker.ui.screens.LiveTesterScreen
import ir.parsshield.smsblocker.ui.screens.MessagesScreen
import ir.parsshield.smsblocker.ui.screens.ProtectionSetupScreen
import ir.parsshield.smsblocker.ui.screens.RulesScreen

@Composable
fun MainScreen(
    viewModel: ParsShieldViewModel,
    isDefaultSmsApp: Boolean,
    isNotificationShieldActive: Boolean,
    hasSmsPermissions: Boolean,
    initialComposeRecipient: String?,
    onRequestPermissions: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    onOpenAppInfoSettings: () -> Unit
) {
    val allMessages by viewModel.messages.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showLiveTester by rememberSaveable { mutableStateOf(false) }
    var composeDialogOpen by rememberSaveable { mutableStateOf(!initialComposeRecipient.isNullOrBlank()) }

    val allowedMessages = remember(allMessages) { allMessages.filter { !it.isBlocked } }
    val otpAndBankMessages = remember(allMessages) {
        allMessages.filter {
            !it.isBlocked && (it.category == SmsCategory.OTP || it.category == SmsCategory.BANK_ALERT)
        }
    }
    val blockedMessages = remember(allMessages) { allMessages.filter { it.isBlocked } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ParsShield",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${blockedMessages.size} ads blocked • ${otpAndBankMessages.size} OTP/Bank safe",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showLiveTester = !showLiveTester }) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "Test SMS Classifier",
                            tint = if (showLiveTester) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (isDefaultSmsApp && !showLiveTester && selectedTab == 0) {
                FloatingActionButton(onClick = { composeDialogOpen = true }) {
                    Icon(Icons.Default.Send, contentDescription = "Compose SMS")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = !showLiveTester && selectedTab == 0,
                    onClick = {
                        showLiveTester = false
                        selectedTab = 0
                    },
                    icon = { Icon(Icons.Default.Inbox, contentDescription = null) },
                    label = { Text("Inbox") }
                )
                NavigationBarItem(
                    selected = !showLiveTester && selectedTab == 1,
                    onClick = {
                        showLiveTester = false
                        selectedTab = 1
                    },
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    label = { Text("OTPs & Bank") }
                )
                NavigationBarItem(
                    selected = !showLiveTester && selectedTab == 2,
                    onClick = {
                        showLiveTester = false
                        selectedTab = 2
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (blockedMessages.isNotEmpty()) {
                                    Badge { Text("${blockedMessages.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null)
                        }
                    },
                    label = { Text("Blocked") }
                )
                NavigationBarItem(
                    selected = !showLiveTester && selectedTab == 3,
                    onClick = {
                        showLiveTester = false
                        selectedTab = 3
                    },
                    icon = { Icon(Icons.Default.Rule, contentDescription = null) },
                    label = { Text("Rules") }
                )
                NavigationBarItem(
                    selected = !showLiveTester && selectedTab == 4,
                    onClick = {
                        showLiveTester = false
                        selectedTab = 4
                    },
                    icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                    label = { Text("Shield") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showLiveTester) {
                LiveTesterScreen(onClassify = viewModel::testMessageLive)
            } else {
                when (selectedTab) {
                    0 -> MessagesScreen(
                        title = "Clean Inbox",
                        subtitle = "Verified personal, OTP, bank & service SMS",
                        messages = allowedMessages,
                        isBlockedVault = false,
                        isScanning = isScanning,
                        onScanInbox = viewModel::scanDeviceInbox,
                        onCopyOtp = viewModel::copyOtp,
                        onRestoreNotSpam = viewModel::restoreNotSpam,
                        onBlockSender = viewModel::blockSpamMessage,
                        onDelete = viewModel::deleteMessage
                    )

                    1 -> MessagesScreen(
                        title = "OTPs & Bank Alerts",
                        subtitle = "Verification codes, رمز پویا & Shaparak receipts",
                        messages = otpAndBankMessages,
                        isBlockedVault = false,
                        isScanning = isScanning,
                        onScanInbox = viewModel::scanDeviceInbox,
                        onCopyOtp = viewModel::copyOtp,
                        onRestoreNotSpam = viewModel::restoreNotSpam,
                        onBlockSender = viewModel::blockSpamMessage,
                        onDelete = viewModel::deleteMessage
                    )

                    2 -> MessagesScreen(
                        title = "Blocked Spam & Phishing",
                        subtitle = "Silently quarantined ads, clinics, betting & fake ثنا scams",
                        messages = blockedMessages,
                        isBlockedVault = true,
                        isScanning = isScanning,
                        onScanInbox = viewModel::scanDeviceInbox,
                        onCopyOtp = viewModel::copyOtp,
                        onRestoreNotSpam = viewModel::restoreNotSpam,
                        onBlockSender = viewModel::blockSpamMessage,
                        onDelete = viewModel::deleteMessage,
                        onClearAllBlocked = viewModel::clearAllBlocked
                    )

                    3 -> RulesScreen(
                        rules = rules,
                        onAddRule = viewModel::addCustomRule,
                        onDeleteRule = viewModel::deleteCustomRule
                    )

                    4 -> ProtectionSetupScreen(
                        settings = settings,
                        isDefaultSmsApp = isDefaultSmsApp,
                        isNotificationShieldActive = isNotificationShieldActive,
                        hasSmsPermissions = hasSmsPermissions,
                        onRequestPermissions = onRequestPermissions,
                        onOpenNotificationAccess = onOpenNotificationAccess,
                        onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                        onOpenAppInfoSettings = onOpenAppInfoSettings,
                        onToggleShield = viewModel::setShieldEnabled,
                        onChangeFilterMode = viewModel::setFilterMode,
                        onToggleNeverBlockOtp = viewModel::setNeverBlockOtp,
                        onToggleAutoCopyOtp = viewModel::setAutoCopyOtp,
                        onToggleOtpBanner = viewModel::setOtpQuickBanner
                    )
                }
            }
        }
    }

    if (composeDialogOpen) {
        ComposeSmsDialog(
            initialRecipient = initialComposeRecipient.orEmpty(),
            onDismiss = { composeDialogOpen = false },
            onSend = { dest, text ->
                viewModel.sendSms(dest, text) { success ->
                    if (success) composeDialogOpen = false
                }
            }
        )
    }
}
