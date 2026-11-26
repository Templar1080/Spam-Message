package ir.parsshield.smsblocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.parsshield.smsblocker.engine.SmsCategory
import ir.parsshield.smsblocker.ui.ConversationThread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationsScreen(
    threads: List<ConversationThread>,
    persianUi: Boolean,
    isDefaultSmsApp: Boolean,
    isScanning: Boolean,
    onActivateInstantBlock: () -> Unit,
    onScanInbox: () -> Unit,
    onOpenThread: (ConversationThread) -> Unit,
    onCopyOtp: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(threads, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) threads
        else threads.filter {
            it.rawSender.lowercase().contains(q) ||
                (it.displayName?.lowercase()?.contains(q) == true) ||
                it.latestMessage.body.lowercase().contains(q) ||
                (it.latestMessage.otpCode?.contains(q) == true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Mandatory Instant Radio-Level Blocker Activation Card if not yet Default SMS App
        if (!isDefaultSmsApp) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF064E3B)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF34D399)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (persianUi) "بلاک آنی بدون حتی ۱ نوتیفیکیشن!"
                            else "Zero-Notification Instant Blocker",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (persianUi) {
                            "برای اینکه پیام‌های تبلیغاتی در کسری از ثانیه (قبل از ایجاد هرگونه نوتیفیکیشن، صدا یا ویبره) در سطح سیستم‌عامل بلاک شوند، پارس‌شیلد را به عنوان برنامه پیش‌فرض پیامک انتخاب کنید."
                        } else {
                            "Set ParsShield as your Default SMS App so advertisements are intercepted at the Android radio layer in <2ms—before any notification, sound, or vibration can ever occur."
                        },
                        color = Color(0xFFD1FAE5),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onActivateInstantBlock,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color(0xFF022C22)
                        )
                    ) {
                        Text(
                            text = if (persianUi) "فعال‌سازی بلاک آنی (انتخاب به عنوان پیامک پیش‌فرض)"
                            else "Activate Instant Blocker (Set Default SMS)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (persianUi) "گفتگوهای پاک" else "Clean Conversations",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (persianUi) "فقط پیام‌های شخصی، کد تایید، بانک و خدماتی"
                    else "Personal chats, OTPs, Shaparak & verified services",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onScanInbox,
                enabled = !isScanning,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isScanning) (if (persianUi) "در حال اسکن..." else "Scanning...")
                    else (if (persianUi) "همگام‌سازی" else "Sync SMS")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = {
                Text(
                    if (persianUi) "جستجوی شماره، متن پیامک یا کد تایید..."
                    else "Search sender, text, or OTP..."
                )
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (persianUi) "هنوز پیامکی همگام‌سازی نشده است"
                        else "No conversations yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onScanInbox, enabled = !isScanning) {
                        Text(
                            if (persianUi) "بارگذاری و پالایش پیامک‌های گوشی"
                            else "Import & Filter Existing Phone SMS"
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.threadAddress }) { thread ->
                    ThreadRowCard(
                        thread = thread,
                        persianUi = persianUi,
                        onClick = { onOpenThread(thread) },
                        onCopyOtp = onCopyOtp
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadRowCard(
    thread: ConversationThread,
    persianUi: Boolean,
    onClick: () -> Unit,
    onCopyOtp: (String) -> Unit
) {
    val msg = thread.latestMessage
    val formattedDate = remember(msg.timestamp) {
        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(msg.timestamp))
    }
    val title = thread.displayName ?: thread.rawSender
    val initial = title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (thread.unreadCount > 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = msg.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDirection = TextDirection.Content
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val catText = when (msg.category) {
                        SmsCategory.OTP -> if (persianUi) "کد تایید • امن" else "OTP • Protected"
                        SmsCategory.BANK_ALERT -> if (persianUi) "بانک / شاپرک" else "Bank / Shaparak"
                        SmsCategory.SERVICE_ALERT -> if (persianUi) "خدماتی" else "Service"
                        else -> if (persianUi) "شخصی" else "Personal"
                    }
                    Text(
                        text = catText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (thread.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${thread.unreadCount}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                if (!msg.otpCode.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AssistChip(
                        onClick = { onCopyOtp(msg.otpCode) },
                        label = {
                            Text(
                                text = if (persianUi) "کپی کد: ${msg.otpCode}"
                                else "Copy OTP: ${msg.otpCode}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}
