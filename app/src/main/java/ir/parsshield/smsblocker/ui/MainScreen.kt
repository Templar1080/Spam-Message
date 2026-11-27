@file:OptIn(ExperimentalMaterial3Api::class)

package ir.parsshield.smsblocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.parsshield.smsblocker.engine.SmsCategory
import ir.parsshield.smsblocker.ui.screens.ComposeSmsDialog
import ir.parsshield.smsblocker.ui.screens.ConversationsScreen
import ir.parsshield.smsblocker.ui.screens.LiveTesterScreen
import ir.parsshield.smsblocker.ui.screens.MessagesScreen
import ir.parsshield.smsblocker.ui.screens.ProtectionSetupScreen
import ir.parsshield.smsblocker.ui.screens.RulesScreen
import ir.parsshield.smsblocker.ui.screens.ThreadDetailScreen

@Composable
fun MainScreen(
    viewModel: ParsShieldViewModel,
    isDefaultSmsApp: Boolean,
    isNotificationListenerEnabled: Boolean,
    hasSmsPermissions: Boolean,
    initialComposeRecipient: String?,
    initialOpenThreadAddress: String?,
    onRequestPermissions: () -> Unit,
    onOpenAppInfoForRestrictedSettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenStockSmsNotificationSettings: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
    onOpenSenderInStockSmsApp: (String) -> Unit,
    onRequestDefaultSmsRole: () -> Unit
) {
    val allMessages by viewModel.messages.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val simCards by viewModel.simCards.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val fa = settings.persianUi
    val layoutDir = if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showLiveTester by rememberSaveable { mutableStateOf(false) }
    var openThreadAddress by rememberSaveable { mutableStateOf(initialOpenThreadAddress) }
    var composeDialogOpen by rememberSaveable { mutableStateOf(!initialComposeRecipient.isNullOrBlank()) }

    val allowedMessages = remember(allMessages) { allMessages.filter { !it.isBlocked } }
    val otpAndBankMessages = remember(allMessages) {
        allMessages.filter {
            !it.isBlocked && !it.isOutgoing &&
                (it.category == SmsCategory.OTP || it.category == SmsCategory.BANK_ALERT)
        }
    }
    val blockedMessages = remember(allMessages) { allMessages.filter { it.isBlocked } }

    val threads = remember(allowedMessages) {
        allowedMessages
            .groupBy { it.threadAddress }
            .map { (addr, msgs) ->
                val sorted = msgs.sortedByDescending { it.timestamp }
                val latest = sorted.first()
                val name = msgs.firstNotNullOfOrNull { it.displayName }
                val unread = msgs.count { !it.isRead && !it.isOutgoing }
                ConversationThread(
                    threadAddress = addr,
                    rawSender = latest.sender,
                    displayName = name,
                    latestMessage = latest,
                    unreadCount = unread,
                    messageCount = msgs.size
                )
            }
            .sortedByDescending { it.latestMessage.timestamp }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        val activeThreadAddr = openThreadAddress
        if (activeThreadAddr != null) {
            val threadMsgs = remember(allowedMessages, activeThreadAddr) {
                allowedMessages.filter { it.threadAddress == activeThreadAddr }
            }
            LaunchedEffect(activeThreadAddr) {
                viewModel.markThreadRead(activeThreadAddr)
            }
            BackHandler { openThreadAddress = null }

            val rawSender = threadMsgs.firstOrNull()?.sender ?: activeThreadAddr
            val title = threadMsgs.firstNotNullOfOrNull { it.displayName } ?: rawSender

            ThreadDetailScreen(
                senderTitle = title,
                rawSender = rawSender,
                messages = threadMsgs,
                persianUi = fa,
                isDefaultSmsApp = isDefaultSmsApp,
                simCards = simCards,
                preferredSimSubId = settings.preferredSimSubId,
                onBack = { openThreadAddress = null },
                onSendReply = { text, subId ->
                    if (!hasSmsPermissions && !isDefaultSmsApp) {
                        onRequestPermissions()
                    } else {
                        viewModel.sendSms(rawSender, text, subId) {}
                    }
                },
                onOpenInStockSmsApp = { onOpenSenderInStockSmsApp(rawSender) },
                onCopyOtp = viewModel::copyOtp,
                onBlockThread = {
                    viewModel.blockThread(activeThreadAddr, rawSender)
                    openThreadAddress = null
                }
            )
        } else {
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
                                        text = if (fa) "پارس‌شیلد" else "ParsShield",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    val totalSilenced = blockedMessages.size + settings.vaporizedCount
                                    Text(
                                        text = if (fa) {
                                            "$totalSilenced تبلیغ بدون نوتیف بلاک شد • ${otpAndBankMessages.size} کد/بانک امن"
                                        } else {
                                            "$totalSilenced ads silenced • ${otpAndBankMessages.size} OTP/Bank safe"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            TextButton(onClick = viewModel::toggleUiLanguage) {
                                Text(
                                    text = if (fa) "EN" else "فارسی",
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                    if (!showLiveTester && selectedTab == 0) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                if (!hasSmsPermissions && !isDefaultSmsApp) {
                                    onRequestPermissions()
                                }
                                viewModel.refreshSimCards()
                                composeDialogOpen = true
                            },
                            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            text = { Text(if (fa) "پیام جدید" else "New SMS") }
                        )
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
                            label = { Text(if (fa) "گفتگوها" else "Inbox") }
                        )
                        NavigationBarItem(
                            selected = !showLiveTester && selectedTab == 1,
                            onClick = {
                                showLiveTester = false
                                selectedTab = 1
                            },
                            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            label = { Text(if (fa) "کد و بانک" else "OTP/Bank") }
                        )
                        NavigationBarItem(
                            selected = !showLiveTester && selectedTab == 2,
                            onClick = {
                                showLiveTester = false
                                selectedTab = 2
                            },
                            icon = { Icon(Icons.Default.Block, contentDescription = null) },
                            label = { Text(if (fa) "بلاک‌شده" else "Blocked") }
                        )
                        NavigationBarItem(
                            selected = !showLiveTester && selectedTab == 3,
                            onClick = {
                                showLiveTester = false
                                selectedTab = 3
                            },
                            icon = { Icon(Icons.Default.Rule, contentDescription = null) },
                            label = { Text(if (fa) "قوانین" else "Rules") }
                        )
                        NavigationBarItem(
                            selected = !showLiveTester && selectedTab == 4,
                            onClick = {
                                showLiveTester = false
                                selectedTab = 4
                            },
                            icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                            label = { Text(if (fa) "سپر" else "Shield") }
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
                            0 -> ConversationsScreen(
                                threads = threads,
                                persianUi = fa,
                                isDefaultSmsApp = isDefaultSmsApp,
                                isNotificationListenerEnabled = isNotificationListenerEnabled,
                                isScanning = isScanning,
                                onOpenAppInfoForRestrictedSettings = onOpenAppInfoForRestrictedSettings,
                                onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                                onOpenStockSmsNotificationSettings = onOpenStockSmsNotificationSettings,
                                onScanInbox = viewModel::scanDeviceInbox,
                                onOpenThread = { openThreadAddress = it.threadAddress },
                                onCopyOtp = viewModel::copyOtp
                            )

                            1 -> MessagesScreen(
                                title = if (fa) "کدهای تایید (OTP) و بانک" else "OTPs & Bank Alerts",
                                subtitle = if (fa) "کد ورود، رمز پویا و تراکنش‌های شاپرک"
                                else "Verification codes, رمز پویا & Shaparak receipts",
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
                                title = if (fa) "قرنطینه بی‌صدای تبلیغات" else "Silent Blocked Vault",
                                subtitle = if (fa) "بلاک‌شده بدون هیچ نوتیفیکیشن یا صدا"
                                else "Silenced with zero notifications or sound",
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
                                simCards = simCards,
                                isDefaultSmsApp = isDefaultSmsApp,
                                isNotificationListenerEnabled = isNotificationListenerEnabled,
                                hasSmsPermissions = hasSmsPermissions,
                                onRequestPermissions = onRequestPermissions,
                                onOpenAppInfoForRestrictedSettings = onOpenAppInfoForRestrictedSettings,
                                onOpenNotificationListenerSettings = onOpenNotificationListenerSettings,
                                onOpenStockSmsNotificationSettings = onOpenStockSmsNotificationSettings,
                                onOpenDefaultAppsSettings = onOpenDefaultAppsSettings,
                                onRequestDefaultSmsRole = onRequestDefaultSmsRole,
                                onSelectPreferredSim = viewModel::setPreferredSim,
                                onToggleShield = viewModel::setShieldEnabled,
                                onChangeFilterMode = viewModel::setFilterMode,
                                onToggleZeroTolerancePanels = viewModel::setZeroTolerancePanels,
                                onToggleDropSpamCompletely = viewModel::setDropSpamCompletely,
                                onToggleNeverBlockOtp = viewModel::setNeverBlockOtp,
                                onToggleAutoCopyOtp = viewModel::setAutoCopyOtp
                            )
                        }
                    }
                }
            }

            if (composeDialogOpen) {
                ComposeSmsDialog(
                    initialRecipient = initialComposeRecipient.orEmpty(),
                    persianUi = fa,
                    simCards = simCards,
                    preferredSimSubId = settings.preferredSimSubId,
                    onDismiss = { composeDialogOpen = false },
                    onSend = { dest, text, subId ->
                        viewModel.sendSms(dest, text, subId) { success ->
                            if (success) composeDialogOpen = false
                        }
                    }
                )
            }
        }
    }
}
