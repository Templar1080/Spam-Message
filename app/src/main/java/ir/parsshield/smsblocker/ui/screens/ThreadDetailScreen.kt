@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.parsshield.smsblocker.data.SmsEntity
import ir.parsshield.smsblocker.service.SimCardInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ThreadDetailScreen(
    senderTitle: String,
    rawSender: String,
    messages: List<SmsEntity>,
    persianUi: Boolean,
    isDefaultSmsApp: Boolean,
    simCards: List<SimCardInfo>,
    preferredSimSubId: Int,
    onBack: () -> Unit,
    onSendReply: (text: String, subscriptionId: Int?) -> Unit,
    onOpenInStockSmsApp: (() -> Unit)? = null,
    onCopyOtp: (String) -> Unit,
    onBlockThread: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    val chronological = remember(messages) { messages.sortedBy { it.timestamp } }

    // Default SIM for this thread: last SIM slot that received/sent in this thread, else preferred SIM
    val initialSubId = remember(simCards, preferredSimSubId, chronological) {
        val lastSlot = chronological.lastOrNull { it.simSlot != null }?.simSlot
        val byThreadSlot = lastSlot?.let { slot ->
            simCards.firstOrNull { it.slotNumber == slot }?.subscriptionId
        }
        byThreadSlot
            ?: simCards.firstOrNull { it.subscriptionId == preferredSimSubId }?.subscriptionId
            ?: simCards.firstOrNull()?.subscriptionId
            ?: -1
    }
    var selectedSubId by remember(initialSubId) { mutableIntStateOf(initialSubId) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = senderTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (senderTitle != rawSender) {
                            Text(
                                text = rawSender,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (!isDefaultSmsApp && onOpenInStockSmsApp != null) {
                        IconButton(onClick = onOpenInStockSmsApp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in Stock SMS App",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onBlockThread) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Block Sender",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Dual-SIM Selector Bar (SIM 1 / SIM 2)
                if (simCards.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        simCards.forEach { sim ->
                            FilterChip(
                                selected = selectedSubId == sim.subscriptionId,
                                onClick = { selectedSubId = sim.subscriptionId },
                                label = {
                                    Text(sim.label(persianUi), fontSize = 11.sp)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SimCard,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeSim = simCards.firstOrNull { it.subscriptionId == selectedSubId }
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        textStyle = TextStyle(fontSize = 14.sp, textDirection = TextDirection.Content),
                        placeholder = {
                            val hint = if (persianUi) {
                                if (activeSim != null) "ارسال پیامک با سیم ${activeSim.slotNumber}..."
                                else "نوشتن پیامک..."
                            } else {
                                if (activeSim != null) "Send SMS via SIM ${activeSim.slotNumber}..."
                                else "Write SMS message..."
                            }
                            Text(hint, fontSize = 13.sp)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val trimmed = replyText.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendReply(trimmed, selectedSubId.takeIf { it >= 0 })
                                replyText = ""
                            }
                        },
                        enabled = replyText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (replyText.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chronological, key = { it.id }) { msg ->
                val timeStr = remember(msg.timestamp) {
                    SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(msg.timestamp))
                }
                val isOut = msg.isOutgoing
                val simSuffix = msg.simSlot?.let {
                    if (persianUi) " • سیم $it" else " • SIM $it"
                }.orEmpty()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isOut) Alignment.End else Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 310.dp)
                            .background(
                                color = if (isOut) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (isOut) 18.dp else 4.dp,
                                    bottomEnd = if (isOut) 4.dp else 18.dp
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            SelectionContainer {
                                Text(
                                    text = msg.body,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        color = if (isOut) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                        textDirection = TextDirection.Content
                                    )
                                )
                            }

                            if (!msg.otpCode.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                AssistChip(
                                    onClick = { onCopyOtp(msg.otpCode) },
                                    label = {
                                        Text(
                                            text = if (persianUi) "کپی: ${msg.otpCode}"
                                            else "Copy: ${msg.otpCode}",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$timeStr$simSuffix",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
