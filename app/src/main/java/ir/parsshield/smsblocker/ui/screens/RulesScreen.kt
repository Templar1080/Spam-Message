@file:OptIn(ExperimentalMaterial3Api::class)

package ir.parsshield.smsblocker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.parsshield.smsblocker.data.RuleEntity
import ir.parsshield.smsblocker.engine.RuleAction
import ir.parsshield.smsblocker.engine.RuleTarget

@Composable
fun RulesScreen(
    rules: List<RuleEntity>,
    onAddRule: (String, RuleTarget, RuleAction) -> Unit,
    onDeleteRule: (RuleEntity) -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(RuleTarget.SENDER) }
    var action by remember { mutableStateOf(RuleAction.BLOCK) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Custom Filter Rules",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Add custom blocked or trusted senders & Persian/English phrases. OTP codes remain protected even from blocked bulk gateways.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = action == RuleAction.BLOCK,
                        onClick = { action = RuleAction.BLOCK },
                        label = { Text("Block") }
                    )
                    FilterChip(
                        selected = action == RuleAction.ALLOW,
                        onClick = { action = RuleAction.ALLOW },
                        label = { Text("Always Allow (Whitelist)") }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = target == RuleTarget.SENDER,
                        onClick = { target = RuleTarget.SENDER },
                        label = { Text("Sender Number / ID") }
                    )
                    FilterChip(
                        selected = target == RuleTarget.BODY,
                        onClick = { target = RuleTarget.BODY },
                        label = { Text("Message Keyword") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            if (target == RuleTarget.SENDER) "e.g. 50004912 or +9821..."
                            else "e.g. کاشت مو or تور لحظه آخری"
                        )
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (pattern.isNotBlank()) {
                            onAddRule(pattern, target, action)
                            pattern = ""
                        }
                    },
                    enabled = pattern.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Rule")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rules, key = { it.id }) { rule ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rule.pattern,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${if (rule.action == RuleAction.ALLOW) "ALLOW" else "BLOCK"} • Matches ${rule.target.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (rule.action == RuleAction.ALLOW) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }
                        IconButton(onClick = { onDeleteRule(rule) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove rule")
                        }
                    }
                }
            }
        }
    }
}
