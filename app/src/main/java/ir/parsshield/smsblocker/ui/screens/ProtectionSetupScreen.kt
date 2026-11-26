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
import androidx.compose.material.icons.filled.Warning
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
    isNotificationListenerEnabled: Boolean,
    hasSmsPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAppInfoForRestrictedSettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
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
                text = if (fa) "تنظیمات سپر سامسونگ و دو سیم‌کارت" else "Samsung Shield & Dual-SIM Setup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = settings.shieldEnabled,
                onCheckedChange = onToggleShield
            )
        }

        // METHOD 1: Samsung One UI / Stock SMS Shield (Keep Samsung Messages + Zero Ad Notifications)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (!isDefaultSmsApp && isNotificationListenerEnabled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isNotificationListenerEnabled) Icons.Default.CheckCircle
                        else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isNotificationListenerEnabled) Color(0xFF10B981) else Color(0xFFF59E0B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (fa) {
                            if (isNotificationListenerEnabled) "روش ۱ (ویژه سامسونگ): سپر حذف نوتیف فعال است ✓"
                            else "روش ۱ (ویژه سامسونگ): حفظ پیام‌رسان گوشی + حذف آنی نوتیف تبلیغات"
                        } else {
                            if (isNotificationListenerEnabled) "Method 1 (Samsung): Notification Shield Active ✓"
                            else "Method 1 (Samsung): Keep Stock SMS + Instant Ad Canceler"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (fa) {
                        "چرا قبلاً نوتیف خاموش نشد؟ چون در گوشی‌های سامسونگ، سوییچ اصلی اعلان برنامه پیامک پیش‌فرض قفل است!\n" +
                            "راه‌حل دقیق در سامسونگ (فقط ۳ کلیک):\n" +
                            "۱) اگر در اندروید ۱۳ به بالا دسترسی اعلان پارس‌شیلد خاکستری است، دکمه ۱ را بزنید ← منوی ۳ نقطه بالا (⋮) ← «مجاز کردن تنظیمات محدودشده».\n" +
                            "۲) دکمه ۲ را بزنید و «دسترسی به اعلان‌ها» را برای پارس‌شیلد روشن کنید.\n" +
                            "۳) دکمه ۳ را بزنید و در صفحه اعلان Messages سامسونگ، به جای سوییچ قفلِ بالا، گزینه «بی‌صدا / Silent» را انتخاب کرده و تیک «پاپ‌آپ / Pop-up» را بردارید!"
                    } else {
                        "On Samsung One UI, the top 'Allow notifications' switch is locked ON for Default SMS.\n" +
                            "1) Tap Step 1 -> ⋮ (top right) -> 'Allow restricted settings' (Android 13+).\n" +
                            "2) Tap Step 2 -> turn ON Notification Access for ParsShield.\n" +
                            "3) Tap Step 3 -> choose 'Silent' & uncheck 'Pop-up' for Samsung Messages!"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (isDefaultSmsApp) {
                    OutlinedButton(
                        onClick = onOpenDefaultAppsSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (fa) "بازگرداندن پیامک پیش‌فرض به Messages سامسونگ"
                            else "Switch Default SMS Back to Samsung Messages"
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                OutlinedButton(
                    onClick = onOpenAppInfoForRestrictedSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (fa) "۱. باز کردن اطلاعات برنامه (منوی ⋮ ← رفع محدودیت)"
                        else "1. App Info (⋮ -> Allow restricted settings)"
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onOpenNotificationListenerSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (fa) {
                            if (isNotificationListenerEnabled) "۲. دسترسی به اعلان‌ها: فعال است ✓"
                            else "۲. روشن کردن «دسترسی به اعلان‌ها» برای پارس‌شیلد"
                        } else {
                            if (isNotificationListenerEnabled) "2. Notification Access: Active ✓"
                            else "2. Turn ON Notification Access for ParsShield"
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onOpenStockSmsNotificationSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (fa) "۳. گذاشتن Messages سامسونگ روی حالت «بی‌صدا / Silent»"
                        else "3. Set Samsung Messages to 'Silent' (No Pop-up)"
                    )
                }

                if (!hasSmsPermissions) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (fa) "اختیاری: اعطای دسترسی مخاطبین و دو سیم‌کارت"
                            else "Optional: Grant Contacts & Dual-SIM Permissions"
                        )
                    }
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
                        text = if (fa) "روش ۲: پارس‌شیلد به عنوان پیامک پیش‌فرض (مجهز به سیم‌کارت ۱ و ۲)"
                        else "Method 2: ParsShield as Default SMS (With SIM 1 / SIM 2)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (fa) {
                        "در این حالت اصلاً نیازی به تغییر تنظیمات نوتیف سامسونگ نیست؛ تبلیغات حتی وارد لیست پیامک‌ها هم نمی‌شوند و ارسال پیام با انتخاب سیم ۱ و سیم ۲ داخل خود پارس‌شیلد انجام می‌شود."
                    } else {
                        "Ads never enter your SMS list, and SIM 1 / SIM 2 sending + Contact Picker work directly inside ParsShield."
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
