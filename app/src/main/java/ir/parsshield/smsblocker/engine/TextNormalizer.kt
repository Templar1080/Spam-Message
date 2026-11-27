package ir.parsshield.smsblocker.engine

/**
 * Fast, allocation-light Persian/Arabic/Finglish SMS normalizer.
 * Defeats common Iranian spam evasion tricks:
 *  - Persian (۰-۹) and Arabic-Indic (٠-٩) numerals -> ASCII 0-9
 *  - Arabic Yeh (ي / ى) & Kaf (ك) -> standard Persian ی & ک
 *  - Kashida / Tatweel stretching (تـخـفـیـف -> تخفیف)
 *  - Arabic diacritics (harakat) and zero-width joiners (ZWNJ)
 */
object TextNormalizer {

    private const val PERSIAN_ZERO = '\u06F0'
    private const val ARABIC_ZERO = '\u0660'

    fun normalizeDigits(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in PERSIAN_ZERO..'\u06F9' -> sb.append(('0'.code + (ch.code - PERSIAN_ZERO.code)).toChar())
                in ARABIC_ZERO..'\u0669' -> sb.append(('0'.code + (ch.code - ARABIC_ZERO.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizeSmsText(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        var lastWasSpace = false

        for (ch in input) {
            val mapped: Char? = when (ch) {
                in PERSIAN_ZERO..'\u06F9' -> ('0'.code + (ch.code - PERSIAN_ZERO.code)).toChar()
                in ARABIC_ZERO..'\u0669' -> ('0'.code + (ch.code - ARABIC_ZERO.code)).toChar()
                '\u064A', '\u0649', '\u06D2' -> '\u06CC' // Arabic/Urdu Yeh -> Persian ی
                '\u0643' -> '\u06A9' // Arabic Kaf -> Persian ک
                '\u0623', '\u0625', '\u0671' -> '\u0627' // أ إ ٱ -> ا
                '\u0624' -> '\u0648' // ؤ -> و
                '\u0629' -> '\u0647' // ة -> ه
                '\u0640' -> null // Strip Kashida ـ
                in '\u064B'..'\u065F', '\u0670' -> null // Strip Arabic diacritics
                '\u200C', '\u200B', '\u200D', '\uFEFF', '\t', ' ' -> ' '
                else -> ch
            }

            if (mapped == null) continue

            if (mapped == ' ') {
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else {
                sb.append(mapped)
                lastWasSpace = false
            }
        }

        return sb.toString().trim()
    }
}
