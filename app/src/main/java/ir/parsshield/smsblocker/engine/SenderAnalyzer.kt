package ir.parsshield.smsblocker.engine

data class SenderAnalysis(
    val senderType: SenderType,
    val cleanedSender: String,
    val bulkPrefix: String? = null
)

object SenderAnalyzer {

    private val KNOWN_BANK_OR_GOV_SENDERS = setOf(
        "SHAPARAK", "BMI", "BANKMELLI", "SADAD", "MELLAT", "BANKMELLAT",
        "BLU", "BLUBANK", "SAMAN", "BANKSAMAN", "BANK-SAMAN", "PASARGAD",
        "BPI", "PARSIAN", "TEJARAT", "SADERAT", "BSI", "SEPAH", "KESHAVARZI",
        "MASKAN", "REFAH", "AYANDEH", "RESALAT", "GARDESHGARI", "KARAFARIN",
        "EGHTESAD", "SINA", "SHAHR", "DEY", "POSTBANK", "WEPOD", "ASANPARDKHT",
        "ADLIRAN", "NAJA", "POLICE", "POST", "TIPAX", "DOLAT", "MYGOV", "SANJESH", "TAMIN",
        "GOOGLE", "TELEGRAM", "APPLE", "MICROSOFT", "WHATSAPP", "INSTAGRAM",
        "FACEBOOK", "SIGNAL", "AUTHMSG", "VERIFY", "GITHUB"
    )

    private val CARRIER_SENDERS = setOf(
        "HAMRAHAVAL", "MCI", "IRANCELL", "MTNIRANCELL", "RIGHTEL",
        "SHATEL", "SAMANTEL", "9990", "8999", "8080", "700", "4444", "9920"
    )

    private val STRIP_FORMATTING_REGEX = Regex("""[\s()-]""")
    private val BULK_SHORTCODE_REGEX = Regex("""^(1000|2000|3000|5000|9000)\d{2,11}$""")
    private val PERSONAL_MOBILE_REGEX = Regex("""^9\d{9}$""")
    private val PROVINCIAL_LANDLINE_REGEX = Regex(
        """^(?:21|26|31|51|71|41|11|13|61|35|25|81|86|44|45|23|24|28|34|38|54|56|58|66|74|76|77|83|84|87|17)\d{6,8}$"""
    )
    private val HAS_ASCII_LETTERS_REGEX = Regex("""[A-Za-z]""")

    private val CANON_0098_MOBILE = Regex("""^00989\d{9}$""")
    private val CANON_98_MOBILE = Regex("""^989\d{9}$""")
    private val CANON_09_MOBILE = Regex("""^09\d{9}$""")
    private val CANON_9_MOBILE = Regex("""^9\d{9}$""")

    /**
     * Merges "+989121234567", "09121234567", and "00989121234567" into a single canonical
     * conversation thread key ("+989121234567").
     */
    fun canonicalizeThreadAddress(rawSender: String): String {
        val cleaned = TextNormalizer.normalizeDigits(rawSender)
            .replace(STRIP_FORMATTING_REGEX, "")
            .trim()
        if (HAS_ASCII_LETTERS_REGEX.containsMatchIn(cleaned)) {
            return cleaned.uppercase()
        }
        val noPlus = cleaned.removePrefix("+")
        return when {
            CANON_0098_MOBILE.matches(noPlus) -> "+98${noPlus.substring(4)}"
            CANON_98_MOBILE.matches(noPlus) -> "+$noPlus"
            CANON_09_MOBILE.matches(noPlus) -> "+98${noPlus.substring(1)}"
            CANON_9_MOBILE.matches(noPlus) -> "+98$noPlus"
            else -> cleaned
        }
    }

    fun analyze(rawSender: String, isSavedContact: Boolean = false): SenderAnalysis {
        val cleaned = TextNormalizer.normalizeDigits(rawSender)
            .replace(STRIP_FORMATTING_REGEX, "")
            .trim()
        val upper = cleaned.uppercase()

        if (isSavedContact) {
            return SenderAnalysis(SenderType.SAVED_CONTACT, cleaned)
        }

        if (upper in KNOWN_BANK_OR_GOV_SENDERS) {
            return SenderAnalysis(SenderType.KNOWN_BANK_OR_GOV, upper)
        }

        if (upper in CARRIER_SENDERS) {
            return SenderAnalysis(SenderType.CARRIER_SENDER, upper)
        }

        val numericOnly = cleaned.removePrefix("+")
        val national = when {
            numericOnly.startsWith("0098") && numericOnly.length > 6 -> numericOnly.substring(4)
            numericOnly.startsWith("98") && numericOnly.length >= 8 -> numericOnly.substring(2)
            numericOnly.startsWith("0") && numericOnly.length >= 10 -> numericOnly.substring(1)
            else -> numericOnly
        }

        BULK_SHORTCODE_REGEX.matchEntire(national)?.let { match ->
            return SenderAnalysis(
                senderType = SenderType.IRAN_BULK_SHORTCODE,
                cleanedSender = cleaned,
                bulkPrefix = match.groupValues[1]
            )
        }

        if (PERSONAL_MOBILE_REGEX.matches(national)) {
            return SenderAnalysis(SenderType.IRAN_PERSONAL_MOBILE, cleaned)
        }

        if (PROVINCIAL_LANDLINE_REGEX.matches(national)) {
            return SenderAnalysis(SenderType.IRAN_LANDLINE_PANEL, cleaned)
        }

        if (HAS_ASCII_LETTERS_REGEX.containsMatchIn(cleaned)) {
            return SenderAnalysis(SenderType.ALPHANUMERIC_BRAND, upper)
        }

        return SenderAnalysis(SenderType.OTHER_NUMBER, cleaned)
    }
}
