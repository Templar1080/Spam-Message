package ir.parsshield.smsblocker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import ir.parsshield.smsblocker.service.SimCardInfo

@Composable
fun ProtectionSetupScreen(
    settings: ShieldSettings,
    simCards: List<SimCardInfo>,
    isDefaultSmsApp: Boolean,
    hasSmsPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenStockSmsNotificationSettings: () -> Unit,
    onOpenDefaultAppsSettings: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
    onSelectPreferredSim: (Int) -> Unit,
    onToggleShield: (Boolean) -> Unit,
    onChangeFilterMode: (FilterMode) -> Unit,
    onToggleZeroTolerancePanels: (Boolean) -> Unit,
    onToggleDropSpamCompletely: (Boolean) -> Unit,
    onToggleNeverBlockOtp: (Boolean) -> Unit,
    onToggleAutoCopyOtp: (Boolean) -> Unit
) {
    val fa = settings.persianUi

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (fa) "تنظیمات سپر و دو سیم‌کارت" else "Shield & Dual-SIM Setup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = settings.shieldEnabled,
                onCheckedChange = onToggleShield
            )
        }

        // METHOD 1: Keep Stock Dual-SIM SMS App + Smart Silent Relay (NO Default SMS Change Needed!)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (!isDefaultSmsApp && hasSmsPermissions) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (fa) "روش ۱ (پیشنهادی): حفظ پیام‌رسان اصلی گوشی + رله نوتیف هوشمند"
                        else "Method 1 (Recommended): Keep Stock SMS App + Smart Relay",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (fa) {
                        "برنامه پیامک فابریک گوشی خودتان (Samsung / Xiaomi / Google) را به عنوان پیش‌فرض نگه دارید تا ارسال پیام و تغییر سیم‌کارت دقیقاً مثل همیشه باشد!\n" +
                            "فقط نوتیف برنامه پیامک اصلی گوشی را خاموش کنید؛ پارس‌شیلد برای تبلیغات هیچ نوتیفی نمی‌دهد (صفر نوتیف!) و فقط کدهای تایید، بانک و پیام‌های شخصی را اعلام می‌کند."
                    } else {
                        "Keep Samsung/Xiaomi/Google Messages as your Default SMS App for native Dual-SIM sending! Just mute notifications on your stock SMS app—ParsShield stays 100% silent on ads and notifies you ONLY for OTPs, bank alerts & personal messages."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (isDefaultSmsApp) {
                    OutlinedButton(
                        onClick = onOpenDefaultAppsSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (fa) "بازگرداندن پیامک پیش‌فرض به برنامه اصلی گوشی"
                            else "Switch Default SMS Back to Phone's Stock App"
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (!hasSmsPermissions) {
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (fa) "گام ۱: اعطای دسترسی پیامک و دو سیم‌کارت"
                            else "Step 1: Grant SMS & Dual-SIM Permissions"
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Button(
                    onClick = onOpenStockSmsNotificationSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (fa) "گام ۲: خاموش کردن نوتیف پیامک اصلی گوشی (۱ کلیک)"
                        else "Step 2: Mute Stock SMS App Notifications (1 Tap)"
                    )
                }
            }
        }

        // METHOD 2: ParsShield as Full Dual-SIM Default SMS App
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDefaultSmsApp) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDefaultSmsApp) Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isDefaultSmsApp) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (fa) "روش ۲: پارس‌شیلد به عنوان پیامک پیش‌فرض (حالا مجهز به دو سیم‌کارت!)"
                        else "Method 2: ParsShield as Default SMS (Now with Dual-SIM!)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (fa) {
                        "تبلیغات حتی وارد دیتابیس پیامک گوشی هم نمی‌شوند و ارسال پیامک جدید، انتخاب از مخاطبین و سوئیچ بین سیم‌کارت ۱ و سیم‌کارت ۲ مستقیماً داخل پارس‌شیلد فعال است."
                    } else {
                        "Ads never even enter your phone's SMS inbox, and full SMS sending + Contact Picker + SIM 1 / SIM 2 switching are built right into ParsShield."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (!isDefaultSmsApp) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRequestDefaultSmsRole,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (fa) "انتخاب پارس‌شیلد به عنوان پیامک پیش‌فرض"
                            else "Set ParsShield as Default SMS App"
                        )
                    }
                }
            }
        }

        // Default Outgoing SIM Card Selector (SIM 1 / SIM 2)
        if (simCards.isNotEmpty()) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (fa) "سیم‌کارت پیش‌فرض ارسال پیامک"
                            else "Preferred SIM Card for Sending SMS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        simCards.forEach { sim ->
                            FilterChip(
                                selected = settings.preferredSimSubId == sim.subscriptionId ||
                                    (settings.preferredSimSubId == -1 && sim == simCards.first()),
                                onClick = { onSelectPreferredSim(sim.subscriptionId) },
                                label = { Text(sim.label(fa), fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SimCard,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Zero-Tolerance Ad Controls
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (fa) "تنظیمات حذف تبلیغات" else "Ad Blocking Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                SettingSwitchRow(
                    title = if (fa) "مسدودسازی کامل خطوط تبلیغاتی (۵۰۰۰، ۲۰۰۰، ۳۰۰۰، ۰۲۱)"
                    else "Zero-Tolerance Commercial Panel Filter",
                    subtitle = if (fa) {
                        "هر پیامکی از پنل‌های انبوه که کد تایید، بانک یا خدماتی نباشد در جا بلاک می‌شود."
                    } else {
                        "Silences all 1000–9000 & 021 panel messages unless OTP, bank, or verified service."
                    },
                    checked = settings.zeroToleranceCommercialPanels,
                    onCheckedChange = onToggleZeroTolerancePanels
                )

                SettingSwitchRow(
                    title = if (fa) "حذف آنی در حافظه (حتی در تب مسدودشده‌ها هم ذخیره نشود)"
                    else "Vaporize Spam Completely",
                    subtitle = if (fa) {
                        "تبلیغات بدون ذخیره نابود می‌شوند (${settings.vaporizedCount} تبلیغ تاکنون نابود شده)."
                    } else {
                        "Drops blocked ads in RAM without saving (${settings.vaporizedCount} vaporized)."
                    },
                    checked = settings.dropSpamCompletely,
                    onCheckedChange = onToggleDropSpamCompletely
                )
            }
        }

        // Filtering Mode Selection
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (fa) "حالت فیلترینگ" else "Filtering Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                FilterModeOptionRow(
                    selected = settings.filterMode == FilterMode.ALLOW_OTP_AND_BANK,
                    title = if (fa) "کدهای تایید (OTP) + بانک و پیام‌های خدماتی (پیشنهادی)"
                    else "Allow OTPs + Bank & Service Alerts (Recommended)",
                    desc = if (fa) "بلاک بی‌صدای تبلیغات و فیشینگ؛ اعلام فوری کد تایید، رمز پویا و شاپرک."
                    else "Silences ads & phishing; allows OTPs, رمز پویا, Shaparak & delivery updates.",
                    onClick = { onChangeFilterMode(FilterMode.ALLOW_OTP_AND_BANK) }
                )
                FilterModeOptionRow(
                    selected = settings.filterMode == FilterMode.STRICT_OTP_ONLY,
                    title = if (fa) "سخت‌گیرانه: فقط کدهای تایید و شماره‌های شخصی"
                    else "Strict: OTPs & Personal Numbers Only",
                    desc = if (fa) "بی‌صدا کردن تمام سرشماره‌ها به جز کد تایید و رمز پویا."
                    else "Silences all shortcodes except login OTPs and رمز پویا.",
                    onClick = { onChangeFilterMode(FilterMode.STRICT_OTP_ONLY) }
                )
            }
        }

        // OTP Safety Guardrails
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (fa) "محافظت ۱۰۰٪ از کدهای تایید و رمز پویا"
                        else "OTP & Dynamic PIN Safety",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                SettingSwitchRow(
                    title = if (fa) "هرگز کد تایید را بلاک نکن (حتی از سرشماره‌های مسدود)"
                    else "Never block OTPs even from blocked senders",
                    subtitle = if (fa) "محافظت از کدهای تایید روی سرشماره‌های مشترک ۵۰۰۰/۱۰۰۰."
                    else "Protects OTPs on shared 1000–5000 gateways.",
                    checked = settings.neverBlockOtpEvenFromBlocked,
                    onCheckedChange = onToggleNeverBlockOtp
                )
                SettingSwitchRow(
                    title = if (fa) "کپی خودکار کد تایید و رمز پویا در کلیپ‌بورد"
                    else "Auto-copy incoming OTP to clipboard",
                    subtitle = if (fa) "به محض دریافت پیامک، کد آماده Paste است."
                    else "Copies verification codes automatically.",
                    checked = settings.autoCopyOtpToClipboard,
                    onCheckedChange = onToggleAutoCopyOtp
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
