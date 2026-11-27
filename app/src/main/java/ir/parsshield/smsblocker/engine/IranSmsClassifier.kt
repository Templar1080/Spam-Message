package ir.parsshield.smsblocker.engine

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Multi-layer, 100% offline Iranian SMS classification engine (v2 — Pre-Notification Radio Shield).
 *
 * Runs inside SmsDeliverReceiver in < 2ms BEFORE Android creates any notification object:
 *  - Spam, Advertisements & Phishing -> shouldBlock = true -> 0 notifications, 0 sound, 0 screen wake
 *  - OTPs, Shaparak رمز پویا, Bank Ledgers, Service Alerts & Personal SMS -> delivered immediately
 */
object IranSmsClassifier {

    private val CI_ML = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    private val CI = setOf(RegexOption.IGNORE_CASE)

    private val PROMO_CODE_EXCLUSION_REGEX = Regex(
        """(?:کد\s*(?:تخفیف|هدیه|قرعه|شانس|امتیاز|معرف|دعوت|کالا|محصول|ملک|فایل|پستی|رهگیری|مرسوله|پیگیری|اشتراک|ملی|اقتصادی)|(?:discount|promo|coupon|voucher|gift|referral|tracking)\s*code)""",
        CI
    )

    private val SHAPARAK_DYNAMIC_OTP_REGEX = Regex(
        """(?:رمز\s*پویا|رمز\s*دوم(?:\s*یک\s*بار\s*مصرف)?|پویا\s*:|ramz(?:e)?\s*(?:pouya|pooya|dovom))""",
        CI
    )

    private val EXPLICIT_PERSIAN_OTP_REGEX = Regex(
        """(?:کد\s*تا[ییئ]+د(?:یه)?|کد\s*ورود|کد\s*فعال\s*سازی|کد\s*امنیتی|کد\s*احراز\s*هویت|کد\s*اعتبار\s*سنجی|کد\s*یک\s*بار\s*مصرف|رمز\s*یک\s*بار\s*مصرف|رمز\s*ورود|رمز\s*(?:عبور\s*)?موقت|کد\s*بازیابی|کد\s*تغییر\s*رمز|کد\s*فراموشی|کد\s*ثبت\s*نام|کد\s*عضویت|کد\s*محرمانه|کد\s*تحویل|کد\s*سجام|کد\s*امضا|پنجره\s*ملی\s*خدمات\s*دولت\s*هوشمند|این\s*کد\s*را\s*در\s*اختیار)""",
        CI
    )

    private val EXPLICIT_ENGLISH_FINGLISH_OTP_REGEX = Regex(
        """(?:verification\s*code|verify\s*code|login\s*code|security\s*code|confirmation\s*code|activation\s*code|authentication\s*code|auth\s*code|access\s*code|recovery\s*code|one[-\s]*time\s*(?:password|code|pin)|\botp\b|passcode|two[-\s]*factor|\b2fa\b|\bg-\d{6}\b|(?:telegram|whatsapp|instagram|signal|apple\s*id|google|microsoft|github)\s*(?:verification\s*)?code|is\s+your\s+(?:[\w\s]{1,24}\s+)?code|do\s+not\s+share\s+this\s+code|(?:kode?|code)\s*(?:taeed|taeid|vorood|vorud|faalsazi|amniati))""",
        CI
    )

    private val WEBOTP_STANDARD_REGEX = Regex(
        """(?:^<#>|@[a-z0-9.-]+\.[a-z]{2,}\s+#\d{4,8}(?!\d))""",
        CI_ML
    )

    private val STRONG_AD_BLOCKER_FOR_GENERIC_CODE_REGEX = Regex(
        """(?:تخفیف|حراج|فروش|جشنواره|کاشت\s*مو|لیزر|ایمپلنت|بوتاکس|ویلا|پیش\s*فروش|شرط\s*بندی|کازینو|انفجار|بونوس|وام|قرعه\s*کشی|خرید\s*از|مشاوره|قسط|تومان|ریال|%)""",
        CI
    )

    private val SHORT_STRUCTURAL_OTP_REGEX = Regex(
        """(?:^|[\s:\n])(?:کد(?:\s*شما)?|رمز|code)\s*[:=-]?\s*(\d{4,8})(?!\d)""",
        CI_ML
    )

    private val TRAILING_LAGHV_REGEX = Regex("""\s*لغو\s*11\s*$""", CI)
    private val MINIMAL_BRAND_OTP_REGEX = Regex(
        """^(?:[\u0600-\u06FFA-Za-z0-9\s._-]{1,24}\s*[:=-]\s*)?(\d{4,8})$"""
    )

    private val GOOGLE_CODE_REGEX = Regex("""\bG-(\d{6})\b""", CI)
    private val WEBOTP_EXTRACT_REGEX = Regex("""@[a-z0-9.-]+\.[a-z]{2,}\s+#(\d{4,8})(?!\d)""", CI)

    private val AFTER_KEYWORD_OTP_REGEX = Regex(
        """(?:رمز\s*پویا|رمز\s*دوم|پویا|کد\s*تا[ییئ]+د(?:یه)?|کد\s*ورود|کد\s*فعال\s*سازی|کد\s*امنیتی|کد\s*احراز\s*هویت|کد\s*اعتبار\s*سنجی|کد\s*یک\s*بار\s*مصرف|رمز\s*یک\s*بار\s*مصرف|رمز\s*ورود|رمز\s*(?:عبور\s*)?موقت|کد\s*بازیابی|کد\s*ثبت\s*نام|کد\s*تحویل|کد\s*سجام|کد\s*شما|verification\s*code|login\s*code|security\s*code|confirmation\s*code|activation\s*code|auth\s*code|\botp\b|passcode|(?:kode?|code)\s*(?:taeed|taeid|vorood|vorud|faalsazi)|ramz(?:e)?\s*(?:pouya|pooya)|(?:^|[\s\n])کد|(?:^|[\s\n])رمز|(?:^|[\s\n])code)[^\d]{0,36}?(\d{4,8})(?!\d)""",
        CI_ML
    )

    private val BEFORE_KEYWORD_OTP_REGEX = Regex(
        """(?<!\d)(\d{4,8})(?!\d)[^\d]{0,32}?(?:is\s+your|verification\s*code|login\s*code|security\s*code|کد\s*تا[ییئ]+د|کد\s*ورود|کد\s*فعال\s*سازی|رمز\s*پویا)""",
        CI
    )

    private val JALALI_DATE_REGEX = Regex("""\b1[34]\d{2}[/-]\d{1,2}[/-]\d{1,2}\b""")
    private val GREGORIAN_DATE_REGEX = Regex("""\b20\d{2}[/-]\d{1,2}[/-]\d{1,2}\b""")
    private val COMMA_MONEY_REGEX = Regex("""\d{1,3}(?:,\d{3})+""")
    private val MOBILE_IN_TEXT_REGEX = Regex("""(?:\+98|0098|98|0)?9\d{9}\b""")
    private val GENERIC_4_TO_8_DIGITS_REGEX = Regex("""(?<!\d)(\d{4,8})(?!\d)""")

    private val URL_IN_SMS_REGEX = Regex(
        """(?:https?://[^\s]+|\b(?:bit\.ly|b2n\.ir|yun\.ir|cutt\.ly|zaya\.io|zil\.ink|t\.me)/[^\s]+|\b[a-z0-9-]+\.(?:xyz|top|icu|click|online|site|pw|buzz|apk|ir|com|net|org)(?:/[^\s]*)?)""",
        CI
    )
    private val APK_LINK_REGEX = Regex("""\.apk(?:\b|[/?#])""", CI)
    private val SHADY_PHISHING_DOMAIN_REGEX = Regex(
        """\b[a-z0-9-]+\.(?:xyz|top|icu|click|online|site|pw|buzz|cfd|sbs|rest)\b""",
        CI
    )

    private val SANA_OR_COURT_LURE_REGEX = Regex(
        """(?:سامانه\s*ثنا|ابلاغیه\s*(?:قضایی|ثنا|فوری|دادگاه|الکترونیکی)|قوه\s*قضاییه|شکواییه|حکم\s*جلب|دادسرا|توقیف\s*(?:اموال|حساب))""",
        CI
    )
    private val GOV_OR_FREEZE_LURE_REGEX = Regex(
        """(?:سهام\s*عدالت|یارانه\s*(?:معیشتی|نقدی)?|کالابرگ|هدیه\s*دولت|(?:حساب|کارت)\s*(?:بانکی\s*)?شما\s*مسدود|رفع\s*مسدودی\s*(?:حساب|کارت)|نقص\s*آدرس.*مرسوله|مرسوله\s*پستی.*پرداخت\s*هزینه)""",
        CI
    )

    private val BANK_TRANSACTION_ACTION_REGEX = Regex(
        """(?:برداشت|واریز|انتقال\s*وجه|کارت\s*به\s*کارت|پایا|ساتنا|خرید\s*(?:با\s*کارت|کالا|پایانه|اینترنتی|:|\d)|تراکنش\s*خرید|پرداخت\s*قبض|سررسید\s*قسط|اقساط\s*تسهیلات|چک\s*صیادی|سود\s*سپرده|افزایش\s*موجودی|کاهش\s*موجودی|کارت\s*بانکی\s*شما\s*صادر)""",
        CI
    )
    private val BANK_LEDGER_CONTEXT_REGEX = Regex(
        """(?:مانده|موجودی|حساب\s*[:\d]|شماره\s*حساب|شبا|سپرده|شاپرک|بانک\s*(?:ملت|ملی|سامان|پاسارگاد|پارسیان|تجارت|صادرات|سپه|کشاورزی|مسکن|رفاه|آینده|رسالت|گردشگری|شهر|سینا|دی|پست)|بلوبانک|بلو،\s*بانک|ویپاد)""",
        CI
    )
    private val BANK_DISQUALIFYING_AD_REGEX = Regex(
        """(?:بونوس|کازینو|انفجار|شرط\s*بندی|سایت\s*بت|تخفیف|جشنواره\s*فروش|قرعه\s*کشی|هدیه\s*ثبت\s*نام|حراج)""",
        CI
    )

    private val SERVICE_PROMO_DISQUALIFIER_REGEX = Regex(
        """(?:تخفیف|حراج|فروش\s*ویژه|جشنواره|آفر|پیشنهاد\s*(?:ویژه|شگفت)|کد\s*تخفیف|هدیه\s*ویژه|قرعه\s*کشی|شانس\s*برنده|بونوس|کازینو|انفجار|شرط\s*بندی|زیر\s*قیمت|دم\s*قسط)""",
        CI
    )
    private val SERVICE_ORDER_POST_REGEX = Regex(
        """(?:سفارش\s*شما.*(?:ثبت|ارسال|آماده|تحویل)|کد\s*تحویل\s*(?:سفارش|مرسوله)?|(?:مرسوله|بسته)\s*(?:پستی|شما|تیپاکس).*(?:تحویل|ارسال|توزیع)|کد\s*رهگیری\s*(?:مرسوله|پستی)|بارنامه)""",
        CI
    )
    private val SERVICE_TRAVEL_REGEX = Regex(
        """(?:(?:بلیط|بلیت)\s*(?:پرواز|قطار|اتوبوس)|شماره\s*پرواز|ساعت\s*حرکت|کارت\s*پرواز|تاخیر\s*پرواز|رزرو\s*هتل.*قطعی)""",
        CI
    )
    private val SERVICE_RIDE_REGEX = Regex(
        """(?:(?:راننده|سفیر).*(?:به\s*مبدا\s*رسید|در\s*مسیر)|سفر\s*شما\s*آغاز\s*شد)""",
        CI
    )
    private val SERVICE_MEDICAL_REGEX = Regex(
        """(?:نوبت\s*(?:شما|پزشکی).*(?:ثبت|تاریخ|ساعت)|یادآوری\s*نوبت|نسخه\s*الکترونیک.*ثبت|بیمه\s*(?:تامین\s*اجتماعی|سلامت).*نسخه|جواب\s*آزمایش)""",
        CI
    )
    private val SERVICE_TELECOM_UTILITY_REGEX = Regex(
        """(?:(?:درصد|حجم|مگابایت|گیگابایت).*(?:بسته\s*اینترنت|اینترنت\s*شما).*(?:مصرف|باقی|اتمام)|بسته\s*اینترنت.*فعال\s*شد|(?:قبض|صورتحساب).*(?:شناسه\s*قبض|شناسه\s*پرداخت|مبلغ\s*قابل\s*پرداخت)|قطعی\s*برق)""",
        CI
    )
    private val SERVICE_GOV_NOTICE_REGEX = Regex(
        """(?:ابلاغیه.*سامانه\s*ثنا|پلیس\s*\+?10|تعویض\s*پلاک|گذرنامه.*تحویل|سازمان\s*سنجش|انتخاب\s*واحد)""",
        CI
    )

    private val SPAM_OPT_OUT_REGEX = Regex(
        """(?:لغو\s*11|لغو\s*=?\s*\d+|لغو\s*ارسال|لغو\s*پیامک|ارسال\s*(?:عدد\s*)?11\b|\blaghv\s*\d*\b|\bunsubscribe\b|reply\s*stop)""",
        CI
    )
    private val SPAM_DISCOUNT_REGEX = Regex(
        """(?:تخفیف|%\s*تخفیف|درصد\s*تخفیف|حراج|فروش\s*ویژه|فروش\s*فوق\s*العاده|جشنواره\s*(?:فروش|تخفیف|پاییزه|زمستانه|بهاره|تابستانه|یلدا|نوروز)?|آفر\s*ویژه|افر\s*ویژه|پیشنهاد\s*شگفت|زیر\s*قیمت|کد\s*تخفیف|هدیه\s*ویژه|بن\s*خرید|خرید\s*اقساطی|اقساط\s*بدون\s*(?:بهره|کارمزد|پیش\s*پرداخت)|دم\s*قسط|پخش\s*عمده|زیر\s*قیمت\s*بازار|ارسال\s*رایگان)""",
        CI
    )
    private val SPAM_GAMBLING_REGEX = Regex(
        """(?:شرط\s*بندی|سایت\s*بت|\bbet\b.*bonus|بازی\s*انفجار|کازینو|بونوس|پیش\s*بینی\s*(?:فوتبال|ورزشی)|ضریب\s*بالا|شارژ\s*هدیه|درگاه\s*مستقیم\s*ووچر)""",
        CI
    )
    private val SPAM_BEAUTY_CLINIC_REGEX = Regex(
        """(?:کاشت\s*(?:مو|ابرو|ریش)|لیزر\s*موهای\s*زائد|لیزر\s*فول\s*بادی|بوتاکس|فیلر|ژل\s*لب|ایمپلنت\s*(?:دندان|فوری)?|لمینت\s*دندان|کامپوزیت\s*دندان|کلینیک\s*(?:زیبایی|پوست|تخصصی)|مزوتراپی|جوانسازی\s*پوست|لاغری\s*موضعی|هایفوتراپی)""",
        CI
    )
    private val SPAM_REAL_ESTATE_REGEX = Regex(
        """(?:پیش\s*فروش\s*(?:آپارتمان|واحد|برج|خودرو)|ویلا\s*(?:شمال|مدرن|جنگلی|ساحلی|استخردار)|زمین\s*(?:سنددار|شهرکی)|تهاتر\s*(?:ملک|ویلا|آپارتمان)|سرمایه\s*گذاری\s*ملکی|فایلینگ\s*املاک|اقساط\s*بلند\s*مدت.*(?:ویلا|زمین|آپارتمان))""",
        CI
    )
    private val SPAM_LOAN_CRYPTO_VISA_REGEX = Regex(
        """(?:وام\s*(?:فوری|آزاد|بدون\s*ضامن|سیم\s*کارت|طلا|خودرو)|سیگنال\s*(?:ارز\s*دیجیتال|طلا|فارکس|بورس)|سود\s*تضمینی|درآمد\s*دلاری|خرید\s*فالوور|مهاجرت\s*تضمینی|اخذ\s*ویزا|ثبت\s*شرکت\s*فوری|اخذ\s*دسته\s*چک)""",
        CI
    )
    private val SPAM_LOTTERY_SEMINAR_RETAIL_REGEX = Regex(
        """(?:قرعه\s*کشی|برنده\s*خوش\s*شانس|جایزه\s*(?:میلیونی|نقدی|بزرگ)|شانس\s*برنده\s*شدن|فرش\s*کاشان|قالیشویی|مبل\s*شویی|تور\s*(?:لحظه\s*آخری|کیش|مشهد|استانبول|آنتالیا|دبی)|باربری|اتوبار|اسباب\s*کشی|لوله\s*بازکنی|تخلیه\s*چاه|وبینار\s*رایگان|دوره\s*جامع|سمینار\s*آموزشی|افتتاحیه\s*(?:شعبه|فروشگاه|رستوران)|تعمیرگاه\s*(?:تخصصی|مجاز)|تعمیرات\s*تخصصی|ثبت\s*شرکت|ثبت\s*برند|کارت\s*بازرگانی|انجام\s*پایان\s*نامه|نگارش\s*مقاله|فال\s*و\s*تاروت|دعانویسی|سرکتاب|طلاق\s*توافقی|ارسال\s*پیامک\s*انبوه|پنل\s*پیامک)""",
        CI
    )
    private val SPAM_FINGLISH_ENGLISH_REGEX = Regex(
        """(?:\btakhfif\b|\bharaj\b|foroush\s*vijeh|kashte?\s*moo|moshavere\s*raygan|vame?\s*fori|\bcasino\b|free\s*bonus|limited\s*time\s*offer|\d+%\s*off\b|buy\s*now|flash\s*sale|promo\s*code)""",
        CI
    )
    private val SPAM_MARKETING_CTA_REGEX = Regex(
        """(?:عدد\s*\d+\s*را\s*(?:به\s*.*\s*)?ارسال|ارسال\s*(?:عدد|کلمه)\s*\S+|مشاوره\s*رایگان|همین\s*حالا\s*(?:کلیک|خرید|تماس)|لینک\s*(?:خرید|ثبت\s*نام|زیر)|ظرفیت\s*محدود|فقط\s*تا\s*پایان\s*امشب|مهلت\s*استفاده)""",
        CI
    )
    private val SHORTENED_PROMO_LINK_REGEX = Regex(
        """\b(?:yun\.ir|b2n\.ir|bit\.ly|cutt\.ly|zaya\.io|zil\.ink|t\.me)/""",
        CI
    )
    private val CARRIER_PROMO_REGEX = Regex(
        """(?:جشنواره|برنده|جایزه|امتیاز|بسته\s*پیشنهادی|هدیه|تخفیف|سرگرمی)""",
        CI
    )

    fun extractOtpCode(normalizedText: String): String? {
        GOOGLE_CODE_REGEX.find(normalizedText)?.let { return it.groupValues[1] }
        WEBOTP_EXTRACT_REGEX.find(normalizedText)?.let { return it.groupValues[1] }
        AFTER_KEYWORD_OTP_REGEX.find(normalizedText)?.let { return it.groupValues[1] }
        BEFORE_KEYWORD_OTP_REGEX.find(normalizedText)?.let { return it.groupValues[1] }

        val sanitized = normalizedText
            .replace(JALALI_DATE_REGEX, " ")
            .replace(GREGORIAN_DATE_REGEX, " ")
            .replace(COMMA_MONEY_REGEX, " ")
            .replace(MOBILE_IN_TEXT_REGEX, " ")

        return GENERIC_4_TO_8_DIGITS_REGEX.find(sanitized)?.groupValues?.get(1)
    }

    fun classify(
        sender: String,
        body: String,
        isSavedContact: Boolean = false,
        filterMode: FilterMode = FilterMode.ALLOW_OTP_AND_BANK,
        customRules: List<CustomRule> = emptyList(),
        neverBlockOtpEvenFromBlocked: Boolean = true,
        zeroToleranceCommercialPanels: Boolean = false
    ): ClassificationResult {
        val normalized = TextNormalizer.normalizeSmsText(body)
        val senderInfo = SenderAnalyzer.analyze(sender, isSavedContact)
        val signals = mutableListOf<MatchedSignal>()

        val otpEval = detectOtp(normalized, senderInfo.senderType)

        // LAYER 1: Custom User Rules & Saved Contacts
        for (rule in customRules) {
            val cleanPattern = TextNormalizer.normalizeSmsText(rule.pattern).lowercase()
            if (cleanPattern.isEmpty()) continue
            val haystack = when (rule.target) {
                RuleTarget.SENDER -> senderInfo.cleanedSender.lowercase()
                RuleTarget.BODY -> normalized.lowercase()
            }
            if (haystack.contains(cleanPattern)) {
                if (rule.action == RuleAction.ALLOW) {
                    signals += MatchedSignal(
                        code = "USER_WHITELIST",
                        label = "Matched custom allow rule \"${rule.pattern}\"",
                        weight = -100
                    )
                    return ClassificationResult(
                        category = if (otpEval.isOtp) SmsCategory.OTP else SmsCategory.PERSONAL,
                        shouldBlock = false,
                        confidence = 99,
                        spamScore = 0,
                        otpCode = otpEval.otpCode,
                        senderType = senderInfo.senderType,
                        normalizedPreview = normalized,
                        reasonSummary = "Allowed by your custom whitelist rule (${rule.pattern}).",
                        signals = signals
                    )
                } else {
                    if (neverBlockOtpEvenFromBlocked && otpEval.isOtp) {
                        signals += MatchedSignal(
                            code = "OTP_OVERRIDE_USER_BLOCK",
                            label = "OTP safety shield overrode sender block rule",
                            weight = -100
                        )
                    } else {
                        signals += MatchedSignal(
                            code = "USER_BLACKLIST",
                            label = "Matched custom block rule \"${rule.pattern}\"",
                            weight = 100
                        )
                        return ClassificationResult(
                            category = SmsCategory.SPAM_AD,
                            shouldBlock = filterMode != FilterMode.MONITOR_ONLY,
                            confidence = 99,
                            spamScore = 100,
                            otpCode = null,
                            senderType = senderInfo.senderType,
                            normalizedPreview = normalized,
                            reasonSummary = "Instantly blocked by your custom rule (${rule.pattern}).",
                            signals = signals
                        )
                    }
                }
            }
        }

        if (senderInfo.senderType == SenderType.SAVED_CONTACT) {
            signals += MatchedSignal(
                code = "SAVED_CONTACT",
                label = "Sender is in your saved phone contacts",
                weight = -90
            )
            return ClassificationResult(
                category = if (otpEval.isOtp) SmsCategory.OTP else SmsCategory.PERSONAL,
                shouldBlock = false,
                confidence = 98,
                spamScore = 0,
                otpCode = otpEval.otpCode,
                senderType = senderInfo.senderType,
                normalizedPreview = normalized,
                reasonSummary = "Allowed because sender is in your saved contacts.",
                signals = signals
            )
        }

        // LAYER 2: Iranian Phishing & Malware Scam Shield
        val hasUrl = URL_IN_SMS_REGEX.containsMatchIn(normalized)
        val hasApk = APK_LINK_REGEX.containsMatchIn(normalized)
        val hasShadyDomain = SHADY_PHISHING_DOMAIN_REGEX.containsMatchIn(normalized)

        if (hasApk) {
            signals += MatchedSignal(
                code = "MALWARE_APK_LINK",
                label = "Contains direct Android .apk file link (banking trojan vector)",
                weight = 100
            )
            return ClassificationResult(
                category = SmsCategory.PHISHING,
                shouldBlock = filterMode != FilterMode.MONITOR_ONLY,
                confidence = 99,
                spamScore = 100,
                otpCode = null,
                senderType = senderInfo.senderType,
                normalizedPreview = normalized,
                reasonSummary = "Blocked silently: Malicious .apk download link.",
                signals = signals
            )
        }

        if (
            SANA_OR_COURT_LURE_REGEX.containsMatchIn(normalized) &&
            senderInfo.cleanedSender != "ADLIRAN" &&
            (senderInfo.senderType == SenderType.IRAN_PERSONAL_MOBILE || hasUrl || hasShadyDomain)
        ) {
            signals += MatchedSignal(
                code = "FAKE_SANA_PHISHING",
                label = "Fake Sana/Judiciary lure from non-ADLIRAN sender or with link",
                weight = 100
            )
            return ClassificationResult(
                category = SmsCategory.PHISHING,
                shouldBlock = filterMode != FilterMode.MONITOR_ONLY,
                confidence = 98,
                spamScore = 100,
                otpCode = null,
                senderType = senderInfo.senderType,
                normalizedPreview = normalized,
                reasonSummary = "Blocked silently: Fake Judiciary/Sana phishing (ADLIRAN never sends links or texts from 09xx numbers).",
                signals = signals
            )
        }

        if (
            GOV_OR_FREEZE_LURE_REGEX.containsMatchIn(normalized) &&
            hasUrl &&
            senderInfo.senderType != SenderType.KNOWN_BANK_OR_GOV
        ) {
            signals += MatchedSignal(
                code = "GOV_BANK_PHISHING_LINK",
                label = "Subsidy / card-freeze / postal lure combined with external link",
                weight = 95
            )
            return ClassificationResult(
                category = SmsCategory.PHISHING,
                shouldBlock = filterMode != FilterMode.MONITOR_ONLY,
                confidence = 96,
                spamScore = 95,
                otpCode = null,
                senderType = senderInfo.senderType,
                normalizedPreview = normalized,
                reasonSummary = "Blocked silently: Suspicious subsidy, postal fee, or account-freeze message with link.",
                signals = signals
            )
        }

        // LAYER 3: OTP & Shaparak Dynamic PIN Priority Shield (NEVER BLOCK)
        if (otpEval.isOtp && otpEval.otpCode != null) {
            signals += MatchedSignal(
                code = otpEval.signalCode,
                label = otpEval.signalLabel,
                weight = -100
            )
            return ClassificationResult(
                category = SmsCategory.OTP,
                shouldBlock = false,
                confidence = otpEval.confidence,
                spamScore = 0,
                otpCode = otpEval.otpCode,
                senderType = senderInfo.senderType,
                normalizedPreview = normalized,
                reasonSummary = "Protected OTP (${otpEval.otpCode}) — delivered immediately.",
                signals = signals
            )
        }

        // LAYER 4: Legitimate Iranian Bank & Shaparak Alerts
        val hasBankAction = BANK_TRANSACTION_ACTION_REGEX.containsMatchIn(normalized)
        val hasBankContext = BANK_LEDGER_CONTEXT_REGEX.containsMatchIn(normalized) ||
            senderInfo.senderType == SenderType.KNOWN_BANK_OR_GOV
        val hasBankDisqualifier = BANK_DISQUALIFYING_AD_REGEX.containsMatchIn(normalized)

        if (hasBankAction && hasBankContext && !hasBankDisqualifier) {
            signals += MatchedSignal(
                code = "IRAN_BANK_LEDGER",
                label = "Verified Iranian bank/Shaparak transaction & balance ledger",
                weight = -85
            )
            val blockedInStrict = filterMode == FilterMode.STRICT_OTP_ONLY
            return ClassificationResult(
                category = SmsCategory.BANK_ALERT,
                shouldBlock = blockedInStrict,
                confidence = 95,
                spamScore = 0,
                otpCode = null,
                senderType = senderInfo.senderType,
                normalizedPreview = normalized,
                reasonSummary = if (blockedInStrict) {
                    "Bank alert (silenced in Strict OTP-Only mode)."
                } else {
                    "Allowed: Verified bank / Shaparak transaction alert."
                },
                signals = signals
            )
        }

        // LAYER 5: Essential Service, Delivery, Travel, Medical & Telecom Alerts
        val hasPromoDisqualifier = SERVICE_PROMO_DISQUALIFIER_REGEX.containsMatchIn(normalized)
        val isServiceAlert = !hasPromoDisqualifier && (
            SERVICE_ORDER_POST_REGEX.containsMatchIn(normalized) ||
                SERVICE_TRAVEL_REGEX.containsMatchIn(normalized) ||
                SERVICE_RIDE_REGEX.containsMatchIn(normalized) ||
                SERVICE_MEDICAL_REGEX.containsMatchIn(normalized) ||
                SERVICE_TELECOM_UTILITY_REGEX.containsMatchIn(normalized) ||
                (SERVICE_GOV_NOTICE_REGEX.containsMatchIn(normalized) && !hasUrl)
            )

        if (isServiceAlert) {
            signals += MatchedSignal(
                code = "ESSENTIAL_SERVICE_UPDATE",
                label = "Transactional service, delivery, travel, medical, or utility notification",
                weight = -75
            )
            val blockedInStrict = filterMode == FilterMode.STRICT_OTP_ONLY
            return ClassificationResult(
                category = SmsCategory.SERVICE_ALERT,
                shouldBlock = blockedInStrict,
                confidence = 91,
                spamScore = 5,
                otpCode = null,
                senderType = senderInfo.senderType,
                normalizedPreview = normalized,
                reasonSummary = if (blockedInStrict) {
                    "Service notification (silenced in Strict OTP-Only mode)."
                } else {
                    "Allowed: Non-promotional service / delivery / utility update."
                },
                signals = signals
            )
        }

        // LAYER 6: Weighted Advertisement, Commercial & Spam Scoring Engine
        var spamScore = 0

        if (SPAM_OPT_OUT_REGEX.containsMatchIn(normalized)) {
            spamScore += 60
            signals += MatchedSignal(
                "OPT_OUT_FOOTER",
                "Contains bulk promotional opt-out footer (لغو11 / unsubscribe)",
                60
            )
        }
        if (SPAM_DISCOUNT_REGEX.containsMatchIn(normalized)) {
            spamScore += 55
            signals += MatchedSignal(
                "PROMO_DISCOUNT_TERMS",
                "Promotional discount, sale, wholesale, or installment marketing offer",
                55
            )
        }
        if (SPAM_GAMBLING_REGEX.containsMatchIn(normalized)) {
            spamScore += 90
            signals += MatchedSignal(
                "GAMBLING_CASINO_SPAM",
                "Gambling, betting, or casino spam keywords",
                90
            )
        }
        if (SPAM_BEAUTY_CLINIC_REGEX.containsMatchIn(normalized)) {
            spamScore += 60
            signals += MatchedSignal(
                "BEAUTY_DENTAL_CLINIC_AD",
                "Cosmetic, laser, hair-transplant, or dental clinic advertisement",
                60
            )
        }
        if (SPAM_REAL_ESTATE_REGEX.containsMatchIn(normalized)) {
            spamScore += 60
            signals += MatchedSignal(
                "REAL_ESTATE_VILLA_AD",
                "Real estate pre-sale or northern villa advertisement",
                60
            )
        }
        if (SPAM_LOAN_CRYPTO_VISA_REGEX.containsMatchIn(normalized)) {
            spamScore += 60
            signals += MatchedSignal(
                "LOAN_CRYPTO_VISA_SPAM",
                "Instant loan, crypto signal, guaranteed profit, or visa spam",
                60
            )
        }
        if (SPAM_LOTTERY_SEMINAR_RETAIL_REGEX.containsMatchIn(normalized)) {
            spamScore += 55
            signals += MatchedSignal(
                "LOTTERY_RETAIL_PROMO",
                "Lottery, VAS trap, seminar, store opening, or local service spam",
                55
            )
        }
        if (SPAM_FINGLISH_ENGLISH_REGEX.containsMatchIn(normalized)) {
            spamScore += 55
            signals += MatchedSignal(
                "FINGLISH_ENGLISH_AD",
                "Finglish or English promotional marketing language",
                55
            )
        }
        if (SPAM_MARKETING_CTA_REGEX.containsMatchIn(normalized)) {
            spamScore += 40
            signals += MatchedSignal(
                "MARKETING_CTA",
                "Marketing call-to-action (send digit/word, free consultation, urgency)",
                40
            )
        }
        if (SHORTENED_PROMO_LINK_REGEX.containsMatchIn(normalized)) {
            spamScore += 30
            signals += MatchedSignal(
                "SHORTENED_PROMO_LINK",
                "Contains shortened marketing link (yun.ir / b2n.ir / bit.ly / t.me)",
                30
            )
        }

        if (
            zeroToleranceCommercialPanels &&
            (senderInfo.senderType == SenderType.IRAN_BULK_SHORTCODE ||
                senderInfo.senderType == SenderType.IRAN_LANDLINE_PANEL)
        ) {
            spamScore += 55
            signals += MatchedSignal(
                "COMMERCIAL_SMS_PANEL_NON_TRANSACTIONAL",
                if (senderInfo.senderType == SenderType.IRAN_BULK_SHORTCODE) {
                    "Non-OTP / non-bank message from bulk SMS panel (${senderInfo.bulkPrefix}...)"
                } else {
                    "Non-OTP / non-service message from commercial landline SMS panel"
                },
                55
            )
        } else {
            when (senderInfo.senderType) {
                SenderType.IRAN_BULK_SHORTCODE -> {
                    val bulkWeight = if (senderInfo.bulkPrefix == "5000" || senderInfo.bulkPrefix == "2000") 25 else 20
                    spamScore += bulkWeight
                    signals += MatchedSignal(
                        "BULK_SMS_SHORTCODE",
                        "Sent from Iranian bulk SMS panel (${senderInfo.bulkPrefix}...)",
                        bulkWeight
                    )
                }
                SenderType.IRAN_LANDLINE_PANEL -> {
                    spamScore += 25
                    signals += MatchedSignal(
                        "LANDLINE_SMS_PANEL",
                        "Sent from virtual landline SMS panel",
                        25
                    )
                }
                SenderType.CARRIER_SENDER -> {
                    if (zeroToleranceCommercialPanels || CARRIER_PROMO_REGEX.containsMatchIn(normalized)) {
                        spamScore += 55
                        signals += MatchedSignal(
                            "CARRIER_PROMO_BLAST",
                            "Non-quota carrier promotional / VAS marketing message",
                            55
                        )
                    }
                }
                else -> Unit
            }
        }

        val cappedScore = min(100, spamScore)

        if (cappedScore >= 50) {
            return ClassificationResult(
                category = SmsCategory.SPAM_AD,
                shouldBlock = filterMode != FilterMode.MONITOR_ONLY,
                confidence = min(99, 65 + ((cappedScore - 50) * 0.68).roundToInt()),
                spamScore = cappedScore,
                otpCode = null,
                senderType = senderInfo.senderType,
                normalizedPreview = normalized,
                reasonSummary = "Instantly blocked at radio level (0 notifications): Promotional / commercial SMS.",
                signals = signals
            )
        }

        if (
            filterMode == FilterMode.STRICT_OTP_ONLY &&
            (senderInfo.senderType == SenderType.IRAN_BULK_SHORTCODE ||
                senderInfo.senderType == SenderType.IRAN_LANDLINE_PANEL ||
                senderInfo.senderType == SenderType.CARRIER_SENDER)
        ) {
            signals += MatchedSignal(
                "STRICT_MODE_NON_OTP_SHORTCODE",
                "Strict OTP-Only mode blocks non-OTP shortcode messages",
                60
            )
            return ClassificationResult(
                category = SmsCategory.SPAM_AD,
                shouldBlock = true,
                confidence = 88,
                spamScore = maxOf(cappedScore, 60),
                otpCode = null,
                senderType = senderInfo.senderType,
                normalizedPreview = normalized,
                reasonSummary = "Blocked silently by Strict OTP-Only mode.",
                signals = signals
            )
        }

        // LAYER 7: Clean Personal / Conversational SMS
        signals += MatchedSignal(
            "CLEAN_CONVERSATIONAL",
            "Conversational message from personal sender with no spam indicators",
            -50
        )
        return ClassificationResult(
            category = SmsCategory.PERSONAL,
            shouldBlock = false,
            confidence = 90,
            spamScore = cappedScore,
            otpCode = null,
            senderType = senderInfo.senderType,
            normalizedPreview = normalized,
            reasonSummary = "Allowed: Personal conversational message.",
            signals = signals
        )
    }

    private data class OtpEval(
        val isOtp: Boolean,
        val otpCode: String? = null,
        val confidence: Int = 0,
        val signalCode: String = "",
        val signalLabel: String = ""
    )

    private fun detectOtp(normalized: String, senderType: SenderType): OtpEval {
        val hasPromoCodeTrap = PROMO_CODE_EXCLUSION_REGEX.containsMatchIn(normalized)

        if (SHAPARAK_DYNAMIC_OTP_REGEX.containsMatchIn(normalized)) {
            extractOtpCode(normalized)?.let { code ->
                return OtpEval(
                    isOtp = true,
                    otpCode = code,
                    confidence = 99,
                    signalCode = "SHAPARAK_DYNAMIC_PIN",
                    signalLabel = "Shaparak / Bank Dynamic Card PIN (رمز پویا)"
                )
            }
        }

        if (WEBOTP_STANDARD_REGEX.containsMatchIn(normalized)) {
            extractOtpCode(normalized)?.let { code ->
                return OtpEval(
                    isOtp = true,
                    otpCode = code,
                    confidence = 99,
                    signalCode = "WEBOTP_STANDARD",
                    signalLabel = "Standard Android WebOTP / SMS Retriever verification code"
                )
            }
        }

        if (EXPLICIT_PERSIAN_OTP_REGEX.containsMatchIn(normalized)) {
            extractOtpCode(normalized)?.let { code ->
                return OtpEval(
                    isOtp = true,
                    otpCode = code,
                    confidence = 99,
                    signalCode = "PERSIAN_OTP_VERIFICATION",
                    signalLabel = "Persian verification / login OTP phrase + numeric code"
                )
            }
        }

        if (!hasPromoCodeTrap && EXPLICIT_ENGLISH_FINGLISH_OTP_REGEX.containsMatchIn(normalized)) {
            extractOtpCode(normalized)?.let { code ->
                return OtpEval(
                    isOtp = true,
                    otpCode = code,
                    confidence = 98,
                    signalCode = "ENGLISH_FINGLISH_OTP",
                    signalLabel = "English / Finglish verification OTP phrase + numeric code"
                )
            }
        }

        if (
            !hasPromoCodeTrap &&
            normalized.length <= 180 &&
            !STRONG_AD_BLOCKER_FOR_GENERIC_CODE_REGEX.containsMatchIn(normalized)
        ) {
            SHORT_STRUCTURAL_OTP_REGEX.find(normalized)?.let { match ->
                return OtpEval(
                    isOtp = true,
                    otpCode = match.groupValues[1],
                    confidence = 94,
                    signalCode = "SHORT_STRUCTURAL_OTP",
                    signalLabel = "Concise login/verification code format"
                )
            }
        }

        val strippedOptOut = normalized.replace(TRAILING_LAGHV_REGEX, "").trim()
        if (
            !hasPromoCodeTrap &&
            strippedOptOut.length <= 60 &&
            !URL_IN_SMS_REGEX.containsMatchIn(strippedOptOut) &&
            !STRONG_AD_BLOCKER_FOR_GENERIC_CODE_REGEX.containsMatchIn(strippedOptOut) &&
            (senderType == SenderType.IRAN_BULK_SHORTCODE ||
                senderType == SenderType.ALPHANUMERIC_BRAND ||
                senderType == SenderType.KNOWN_BANK_OR_GOV)
        ) {
            MINIMAL_BRAND_OTP_REGEX.matchEntire(strippedOptOut)?.let { match ->
                return OtpEval(
                    isOtp = true,
                    otpCode = match.groupValues[1],
                    confidence = 92,
                    signalCode = "MINIMAL_BRAND_OTP",
                    signalLabel = "Minimal brand/shortcode verification PIN"
                )
            }
        }

        return OtpEval(isOtp = false)
    }
}
