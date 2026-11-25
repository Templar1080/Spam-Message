package ir.parsshield.smsblocker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.parsshield.smsblocker.data.ShieldSettings
import ir.parsshield.smsblocker.engine.FilterMode

@Composable
fun ProtectionSetupScreen(
    settings: ShieldSettings,
    isDefaultSmsApp: Boolean,
    isNotificationShieldActive: Boolean,
    hasSmsPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    onOpenAppInfoSettings: () -> Unit,
    onToggleShield: (Boolean) -> Unit,
    onChangeFilterMode: (FilterMode) -> Unit,
    onToggleNeverBlockOtp: (Boolean) -> Unit,
    onToggleAutoCopyOtp: (Boolean) -> Unit,
    onToggleOtpBanner: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Protection & Setup",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Master Shield Switch + Zero-Internet Privacy Guarantee
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Spam & Ad Shield Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "100% offline • Zero internet permission requested",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Switch(
                        checked = settings.shieldEnabled,
                        onCheckedChange = onToggleShield
                    )
                }
            }
        }

        // Two Android Blocking Modes
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Choose How Android Blocks Spam",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Option A lets you keep Google/Samsung Messages. Option B makes ParsShield your Default SMS app for complete silent blocking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option A: Companion Notification Shield
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Option A: Notification Shield", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isNotificationShieldActive) "Active — dismissing spam notifications automatically"
                            else "Keep your current SMS app & auto-dismiss spam alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isNotificationShieldActive) Color(0xFF10B981)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isNotificationShieldActive) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                    } else {
                        Button(onClick = onOpenNotificationAccess) {
                            Text("Enable")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android 13+ APK tip: If Notification Access says 'Restricted setting', tap 'Open App Info' -> ⋮ (top right) -> 'Allow restricted settings'.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onOpenAppInfoSettings,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("Open App Info", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Option B: Full Default SMS App Role
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Option B: Full Default SMS Blocker", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isDefaultSmsApp) "Active — OS-level silent spam quarantine"
                            else "Zero chime, zero lock-screen wake on spam",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDefaultSmsApp) Color(0xFF10B981)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isDefaultSmsApp) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                    } else {
                        OutlinedButton(onClick = onRequestDefaultSmsRole) {
                            Text("Set Default")
                        }
                    }
                }

                if (!hasSmsPermissions) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant SMS & Contacts Read Permission")
                    }
                }
            }
        }

        // Filtering Mode Selection
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Filtering Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                FilterModeOptionRow(
                    selected = settings.filterMode == FilterMode.ALLOW_OTP_AND_BANK,
                    title = "Allow OTPs + Bank & Service Alerts (Recommended)",
                    desc = "Blocks ads, clinics, villas, betting & phishing; allows OTPs, Shaparak/bank alerts, postal & service SMS.",
                    onClick = { onChangeFilterMode(FilterMode.ALLOW_OTP_AND_BANK) }
                )
                FilterModeOptionRow(
                    selected = settings.filterMode == FilterMode.STRICT_OTP_ONLY,
                    title = "Strict: OTPs & Personal Numbers Only",
                    desc = "Silences all shortcode/panel messages except login OTPs and Shaparak رمز پویا.",
                    onClick = { onChangeFilterMode(FilterMode.STRICT_OTP_ONLY) }
                )
                FilterModeOptionRow(
                    selected = settings.filterMode == FilterMode.MONITOR_ONLY,
                    title = "Monitor & Categorize Only",
                    desc = "Tags spam without blocking notifications.",
                    onClick = { onChangeFilterMode(FilterMode.MONITOR_ONLY) }
                )
            }
        }

        // OTP Safety & Convenience Toggles
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OTP Protection & Convenience",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                SettingSwitchRow(
                    title = "Never block OTPs even from blocked senders",
                    subtitle = "Essential in Iran because Digikala/Snapp/banks share 1000-5000 gateways with ad panels.",
                    checked = settings.neverBlockOtpEvenFromBlocked,
                    onCheckedChange = onToggleNeverBlockOtp
                )
                SettingSwitchRow(
                    title = "Auto-copy incoming OTP to clipboard",
                    subtitle = "Automatically copies 4–8 digit verification codes and رمز پویا as soon as they arrive.",
                    checked = settings.autoCopyOtpToClipboard,
                    onCheckedChange = onToggleAutoCopyOtp
                )
                SettingSwitchRow(
                    title = "1-Tap 'Copy OTP' notification button",
                    subtitle = "Adds a quick Copy button in the notification shade.",
                    checked = settings.otpQuickNotificationBanner,
                    onCheckedChange = onToggleOtpBanner
                )
            }
        }

        // Iranian Carrier USSD Tip Card
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Bonus Tip for Iran Mobile Carriers",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dialing *800# on MCI (Hamrah-e Aval), Irancell, or Rightel lets you cancel paid VAS subscriptions, while ParsShield filters the remaining 5000/021/09xx promotional blasts without ever breaking your OTPs!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

@Composable
private fun FilterModeOptionRow(
    selected: Boolean,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 6.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
