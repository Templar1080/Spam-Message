package ir.parsshield.smsblocker.engine

enum class SmsCategory {
    OTP,
    BANK_ALERT,
    SERVICE_ALERT,
    PERSONAL,
    SPAM_AD,
    PHISHING
}

enum class FilterMode {
    ALLOW_OTP_AND_BANK,
    STRICT_OTP_ONLY,
    MONITOR_ONLY
}

enum class SenderType {
    SAVED_CONTACT,
    KNOWN_BANK_OR_GOV,
    CARRIER_SENDER,
    IRAN_BULK_SHORTCODE,
    IRAN_LANDLINE_PANEL,
    IRAN_PERSONAL_MOBILE,
    ALPHANUMERIC_BRAND,
    OTHER_NUMBER
}

enum class RuleTarget {
    SENDER,
    BODY
}

enum class RuleAction {
    ALLOW,
    BLOCK
}

data class CustomRule(
    val id: Long = 0L,
    val pattern: String,
    val target: RuleTarget,
    val action: RuleAction
)

data class MatchedSignal(
    val code: String,
    val label: String,
    val weight: Int
)

data class ClassificationResult(
    val category: SmsCategory,
    val shouldBlock: Boolean,
    val confidence: Int,
    val spamScore: Int,
    val otpCode: String?,
    val senderType: SenderType,
    val normalizedPreview: String,
    val reasonSummary: String,
    val signals: List<MatchedSignal>
)
