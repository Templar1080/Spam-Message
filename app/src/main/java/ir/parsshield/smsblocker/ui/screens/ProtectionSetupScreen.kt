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
    hasSmsPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestDefaultSmsRole: () -> Unit,
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
        Text(
            text = if (fa) "تنظیمات سپر بلاک آنی" else "Zero-Notification Shield Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Radio-Level Pre-Notification Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDefaultSmsApp) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    Color(0xFF7F1D1D).copy(alpha = 0.45f)
                }
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
                            imageVector = if (isDefaultSmsApp) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isDefaultSmsApp) Color(0xFF10B981) else Color(0xFFF87171)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isDefaultSmsApp) {
                                    if (fa) "بلاک آنی سطح رادیویی فعال است"
                                    else "Pre-Notification Radio Blocker Active"
                                } else {
                                    if (fa) "نیاز به انتخاب به عنوان برنامه پیش‌فرض پیامک"
                                    else "Set as Default SMS Required for Zero Notification"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (fa) {
                                    "پیام‌های تبلیغاتی در کمتر از ۲ میلی‌ثانیه مسدود می‌شوند؛ بدون نوتیفیکیشن، بدون صدا و بدون روشن شدن صفحه."
                                } else {
                                    "Spam is intercepted at SMS_DELIVER in <2ms—zero notification, zero chime, zero screen wake."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Switch(
                        checked = settings.shieldEnabled,
                        onCheckedChange = onToggleShield
                    )
                }

                if (!isDefaultSmsApp) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestDefaultSmsRole,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (fa) "انتخاب پارس‌شیلد به عنوان پیامک پیش‌فرض"
                            else "Set ParsShield as Default SMS App"
                        )
                    }
                } else if (!hasSmsPermissions) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (fa) "اعطای دسترسی مخاطبین برای تشخیص شماره‌های آشنا"
                            else "Grant Contacts Permission"
                        )
                    }
                }
            }
        }

        // Instant Silent Quarantine / Vaporize Toggles
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (fa) "حذف مطلق پیامک‌های تبلیغاتی" else "Zero-Tolerance Ad Blocking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                SettingSwitchRow(
                    title = if (fa) "مسدودسازی کامل خطوط تبلیغاتی (۵۰۰۰، ۲۰۰۰، ۳۰۰۰، ۰۲۱)"
                    else "Zero-Tolerance Commercial Panel Filter",
                    subtitle = if (fa) {
                        "هر پیامکی از پنل‌های انبوه و ثابت که کد تایید، تراکنش بانکی یا پیام خدماتی ضروری نباشد، در جا و بدون نوتیف بلاک می‌شود."
                    } else {
                        "Blocks ALL messages from 1000/2000/3000/5000/9000 & 021 panels unless they are OTPs, bank alerts, or verified services."
                    },
                    checked = settings.zeroToleranceCommercialPanels,
                    onCheckedChange = onToggleZeroTolerancePanels
                )

                SettingSwitchRow(
                    title = if (fa) "حذف آنی در حافظه (حتی در تب مسدودشده‌ها هم ذخیره نشود)"
                    else "Vaporize Spam Completely (Don't Even Save in Vault)",
                    subtitle = if (fa) {
                        "تبلیغات در لحظه دریافت کاملا نابود می‌شوند (${settings.vaporizedCount} تبلیغ تاکنون نابود شده)."
                    } else {
                        "Drops blocked ads in RAM immediately without saving (${settings.vaporizedCount} vaporized so far)."
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
                    title = if (fa) "دریافت کدهای تایید (OTP) + بانک و پیام‌های خدماتی (پیشنهادی)"
                    else "Allow OTPs + Bank & Service Alerts (Recommended)",
                    desc = if (fa) "بلاک بی‌صدای تمام تبلیغات و فیشینگ؛ نمایش فوری کد تایید، رمز پویا، شاپرک و مرسولات پستی."
                    else "Silently blocks all ads & phishing; allows OTPs, رمز پویا, Shaparak & delivery updates.",
                    onClick = { onChangeFilterMode(FilterMode.ALLOW_OTP_AND_BANK) }
                )
                FilterModeOptionRow(
                    selected = settings.filterMode == FilterMode.STRICT_OTP_ONLY,
                    title = if (fa) "حالت سخت‌گیرانه: فقط کدهای تایید و شماره‌های شخصی"
                    else "Strict: OTPs & Personal Numbers Only",
                    desc = if (fa) "حتی پیام‌های خدماتی و بانکی غیر از رمز پویا و کد تایید هم بی‌صدا می‌شوند."
                    else "Silences everything from shortcodes except login OTPs and رمز پویا.",
                    onClick = { onChangeFilterMode(FilterMode.STRICT_OTP_ONLY) }
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
                        text = if (fa) "محافظت ۱۰۰٪ از کدهای تایید و رمز پویا"
                        else "OTP & Dynamic PIN Safety",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                SettingSwitchRow(
                    title = if (fa) "هرگز کد تایید را بلاک نکن (حتی از سرشماره‌های مسدود)"
                    else "Never block OTPs even from blocked senders",
                    subtitle = if (fa) "ضروری در ایران چون دیجی‌کالا، اسنپ و بانک‌ها از سرشماره‌های مشترک ۵۰۰۰/۱۰۰۰ استفاده می‌کنند."
                    else "Essential in Iran where Digikala/Snapp/banks share 1000–5000 gateways with ads.",
                    checked = settings.neverBlockOtpEvenFromBlocked,
                    onCheckedChange = onToggleNeverBlockOtp
                )
                SettingSwitchRow(
                    title = if (fa) "کپی خودکار کد تایید و رمز پویا در کلیپ‌بورد"
                    else "Auto-copy incoming OTP to clipboard",
                    subtitle = if (fa) "به محض دریافت پیامک، کد ۴ تا ۸ رقمی آماده Paste است."
                    else "Copies 4–8 digit verification codes and رمز پویا automatically.",
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
