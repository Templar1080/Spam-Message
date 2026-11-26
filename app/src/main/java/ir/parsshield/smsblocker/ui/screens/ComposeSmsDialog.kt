package ir.parsshield.smsblocker.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.parsshield.smsblocker.service.SimCardInfo

@Composable
fun ComposeSmsDialog(
    initialRecipient: String = "",
    persianUi: Boolean = true,
    simCards: List<SimCardInfo> = emptyList(),
    preferredSimSubId: Int = -1,
    onDismiss: () -> Unit,
    onSend: (destination: String, text: String, subscriptionId: Int?) -> Unit
) {
    val context = LocalContext.current
    var recipient by remember { mutableStateOf(initialRecipient) }
    var contactLabel by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }

    val defaultSubId = remember(simCards, preferredSimSubId) {
        simCards.firstOrNull { it.subscriptionId == preferredSimSubId }?.subscriptionId
            ?: simCards.firstOrNull()?.subscriptionId
            ?: -1
    }
    var selectedSubId by remember(defaultSubId) { mutableIntStateOf(defaultSubId) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.data ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val number = cursor.getString(0).orEmpty().trim()
                        val name = cursor.getString(1)
                        if (number.isNotEmpty()) {
                            recipient = number
                            contactLabel = name
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (persianUi) "ارسال پیامک جدید" else "New SMS Message",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = {
                        recipient = it
                        contactLabel = null
                    },
                    label = {
                        Text(
                            contactLabel ?: if (persianUi) "شماره گیرنده (مثلاً 0912...)"
                            else "Recipient (+989...)"
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val pickIntent = Intent(
                                    Intent.ACTION_PICK,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                )
                                runCatching { contactPickerLauncher.launch(pickIntent) }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contacts,
                                contentDescription = "Pick Contact"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (simCards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (persianUi) "انتخاب سیم‌کارت ارسال:" else "Send via SIM card:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        simCards.forEach { sim ->
                            FilterChip(
                                selected = selectedSubId == sim.subscriptionId,
                                onClick = { selectedSubId = sim.subscriptionId },
                                label = {
                                    Text(sim.label(persianUi), fontSize = 12.sp)
                                },
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

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (persianUi) "متن پیامک..." else "Message") },
                    minLines = 3,
                    textStyle = TextStyle(fontSize = 14.sp, textDirection = TextDirection.Content),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSend(
                        recipient,
                        text,
                        selectedSubId.takeIf { it >= 0 }
                    )
                },
                enabled = recipient.isNotBlank() && text.isNotBlank()
            ) {
                val activeSim = simCards.firstOrNull { it.subscriptionId == selectedSubId }
                val btnLabel = if (persianUi) {
                    if (activeSim != null && simCards.size > 1) "ارسال با سیم ${activeSim.slotNumber}"
                    else "ارسال پیامک"
                } else {
                    if (activeSim != null && simCards.size > 1) "Send (SIM ${activeSim.slotNumber})"
                    else "Send"
                }
                Text(btnLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (persianUi) "انصراف" else "Cancel")
            }
        }
    )
}
