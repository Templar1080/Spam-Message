package ir.parsshield.smsblocker.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.parsshield.smsblocker.data.SmsEntity
import ir.parsshield.smsblocker.engine.SmsCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagesScreen(
    title: String,
    subtitle: String,
    messages: List<SmsEntity>,
    isBlockedVault: Boolean,
    isScanning: Boolean,
    onScanInbox: () -> Unit,
    onCopyOtp: (String) -> Unit,
    onRestoreNotSpam: (SmsEntity) -> Unit,
    onBlockSender: (SmsEntity) -> Unit,
    onDelete: (SmsEntity) -> Unit,
    onClearAllBlocked: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(messages, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) messages
        else messages.filter {
            it.sender.lowercase().contains(q) ||
                (it.displayName?.lowercase()?.contains(q) == true) ||
                it.body.lowercase().contains(q) ||
                (it.otpCode?.contains(q) == true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isBlockedVault && onClearAllBlocked != null && messages.isNotEmpty()) {
                TextButton(onClick = onClearAllBlocked) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            } else {
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
                    Text(if (isScanning) "Scanning..." else "Scan Inbox")
                }
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
            placeholder = { Text("Search sender, Persian/English text, or OTP...") }
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
                        imageVector = if (isBlockedVault) Icons.Default.Block else Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isBlockedVault) "No spam messages in quarantine"
                        else "No messages here yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isBlockedVault) {
                            "Blocked promotional & phishing SMS are stored silently here with zero notifications."
                        } else {
                            "Tap 'Scan Inbox' to classify your existing phone SMS messages offline."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isBlockedVault) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = onScanInbox, enabled = !isScanning) {
                            Text(if (isScanning) "Scanning..." else "Scan Existing Device SMS")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { msg ->
                    SmsMessageCard(
                        msg = msg,
                        isBlockedVault = isBlockedVault,
                        onCopyOtp = onCopyOtp,
                        onRestoreNotSpam = onRestoreNotSpam,
                        onBlockSender = onBlockSender,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun SmsMessageCard(
    msg: SmsEntity,
    isBlockedVault: Boolean,
    onCopyOtp: (String) -> Unit,
    onRestoreNotSpam: (SmsEntity) -> Unit,
    onBlockSender: (SmsEntity) -> Unit,
    onDelete: (SmsEntity) -> Unit
) {
    val badgeInfo = remember(msg.category) { categoryBadge(msg.category) }
    val formattedDate = remember(msg.timestamp) {
        SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(msg.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = msg.displayName ?: msg.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (msg.displayName != null) {
                        Text(
                            text = msg.sender,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(badgeInfo.second.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeInfo.first,
                        color = badgeInfo.second,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Automatically aligns RTL Persian SMS right-to-left and English/Finglish left-to-right!
            Text(
                text = msg.body,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDirection = TextDirection.Content
                )
            )

            if (!msg.otpCode.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AssistChip(
                    onClick = { onCopyOtp(msg.otpCode) },
                    label = {
                        Text(
                            text = "Copy OTP: ${msg.otpCode}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy OTP",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            if (msg.category == SmsCategory.PHISHING) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = msg.reasonSummary,
                        fontSize = 11.sp,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBlockedVault) {
                        TextButton(onClick = { onRestoreNotSpam(msg) }) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Not Spam", fontSize = 12.sp)
                        }
                    } else if (msg.category != SmsCategory.OTP) {
                        TextButton(onClick = { onBlockSender(msg) }) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Block Ad", fontSize = 12.sp)
                        }
                    }

                    IconButton(onClick = { onDelete(msg) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun categoryBadge(category: SmsCategory): Pair<String, Color> = when (category) {
    SmsCategory.OTP -> "OTP • Protected" to Color(0xFF10B981)
    SmsCategory.BANK_ALERT -> "Bank / Shaparak" to Color(0xFF38BDF8)
    SmsCategory.SERVICE_ALERT -> "Service Alert" to Color(0xFFA78BFA)
    SmsCategory.PERSONAL -> "Personal" to Color(0xFF94A3B8)
    SmsCategory.SPAM_AD -> "Blocked Ad" to Color(0xFFF59E0B)
    SmsCategory.PHISHING -> "Phishing Scam" to Color(0xFFEF4444)
}
