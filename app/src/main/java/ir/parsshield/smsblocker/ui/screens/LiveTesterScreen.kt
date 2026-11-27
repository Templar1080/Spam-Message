package ir.parsshield.smsblocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.parsshield.smsblocker.engine.ClassificationResult

private data class SamplePreset(
    val label: String,
    val sender: String,
    val body: String
)

private val PRESETS = listOf(
    SamplePreset(
        "Digikala OTP + لغو11",
        "9850004912",
        "دیجی‌کالا\nکد تایید ورود شما: ۴۸۲۹۱۰\nلغو11"
    ),
    SamplePreset(
        "Shaparak رمز پویا",
        "9820004000",
        "بانک ملت\nرمز پویا: ۸۴۹۲۰۱۵\nمبلغ: ۲,۵۰۰,۰۰۰ ریال\nکارت: ۵۱۰۴"
    ),
    SamplePreset(
        "Clinic Ad",
        "+982122446688",
        "جشنواره کلینیک زیبایی! کاشت مو و لیزر موهای زائد با ۴۰٪ تخفیف. مشاوره رایگان"
    ),
    SamplePreset(
        "Discount Code Trap",
        "9850002910",
        "۵۰ درصد تخفیف ویژه! کد تخفیف: 5040 مهلت تا امشب لغو11"
    ),
    SamplePreset(
        "Fake ثنا Phishing",
        "+989198765432",
        "ابلاغیه فوری سامانه ثنا قوه قضاییه: جهت مشاهده شکواییه وارد لینک شوید http://sana-adl.xyz"
    ),
    SamplePreset(
        "Bank Saman Alert",
        "SAMAN",
        "بانک سامان\nبرداشت: ۱,۲۵۰,۰۰۰ ریال\nمانده: ۴۵,۸۰۰,۰۰۰ ریال"
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveTesterScreen(
    onClassify: (String, String) -> ClassificationResult
) {
    var sender by remember { mutableStateOf(PRESETS[0].sender) }
    var body by remember { mutableStateOf(PRESETS[0].body) }

    val result = remember(sender, body) { onClassify(sender, body) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "On-Device SMS Analyzer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Paste any Persian, Finglish, or English SMS to inspect how ParsShield classifies it offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PRESETS.forEach { preset ->
                AssistChip(
                    onClick = {
                        sender = preset.sender
                        body = preset.body
                    },
                    label = { Text(preset.label, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = sender,
            onValueChange = { sender = it },
            label = { Text("Sender Number / ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("SMS Message Body") },
            minLines = 4,
            textStyle = TextStyle(fontSize = 14.sp, textDirection = TextDirection.Content),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        val verdictColor = if (result.shouldBlock) Color(0xFFEF4444) else Color(0xFF10B981)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = verdictColor.copy(alpha = 0.12f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (result.shouldBlock) "BLOCKED (${result.category})"
                        else "ALLOWED (${result.category})",
                        color = verdictColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${result.confidence}% confidence",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = result.reasonSummary,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (result.otpCode != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Extracted OTP Code: ${result.otpCode}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Matched Signals (${result.senderType}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                result.signals.forEach { sig ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = sig.label,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (sig.weight > 0) "+${sig.weight}" else "${sig.weight}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (sig.weight > 0) Color(0xFFF59E0B) else Color(0xFF10B981)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}
