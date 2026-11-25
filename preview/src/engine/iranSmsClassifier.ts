export type SmsCategory =
  | 'OTP'
  | 'BANK_ALERT'
  | 'SERVICE_ALERT'
  | 'PERSONAL'
  | 'SPAM_AD'
  | 'PHISHING';

export type FilterMode = 'ALLOW_OTP_AND_BANK' | 'STRICT_OTP_ONLY' | 'MONITOR_ONLY';

export type SenderType =
  | 'SAVED_CONTACT'
  | 'KNOWN_BANK_OR_GOV'
  | 'CARRIER_SENDER'
  | 'IRAN_BULK_SHORTCODE'
  | 'IRAN_LANDLINE_PANEL'
  | 'IRAN_PERSONAL_MOBILE'
  | 'ALPHANUMERIC_BRAND'
  | 'OTHER_NUMBER';

export interface CustomRule {
  id: string;
  pattern: string;
  target: 'SENDER' | 'BODY';
  action: 'ALLOW' | 'BLOCK';
}

export interface MatchedSignal {
  code: string;
  label: string;
  weight: number; // positive = spam risk, negative = trust/safety signal
}

export interface ClassificationResult {
  category: SmsCategory;
  shouldBlock: boolean;
  confidence: number; // 0..100
  spamScore: number;
  otpCode: string | null;
  senderType: SenderType;
  normalizedPreview: string;
  reasonSummary: string;
  signals: MatchedSignal[];
}

export interface ClassifyOptions {
  sender: string;
  body: string;
  isSavedContact?: boolean;
  filterMode?: FilterMode;
  customRules?: CustomRule[];
  neverBlockOtpEvenFromBlocked?: boolean;
}

const PERSIAN_ZERO = 0x06f0;
const ARABIC_ZERO = 0x0660;

/**
 * Normalizes Persian/Arabic numerals, Arabic Yeh/Kaf variants, Kashida (tatweel),
 * diacritics, and zero-width characters used by Iranian SMS spammers to evade filters.
 */
export function normalizeDigits(input: string): string {
  let out = '';
  for (let i = 0; i < input.length; i++) {
    const code = input.charCodeAt(i);
    if (code >= PERSIAN_ZERO && code <= PERSIAN_ZERO + 9) {
      out += String(code - PERSIAN_ZERO);
    } else if (code >= ARABIC_ZERO && code <= ARABIC_ZERO + 9) {
      out += String(code - ARABIC_ZERO);
    } else {
      out += input[i];
    }
  }
  return out;
}

export function normalizeSmsText(input: string): string {
  const digitsNormalized = normalizeDigits(input);
  return digitsNormalized
    .replace(/[\u064A\u0649\u06D2]/g, '\u06CC') // Arabic/Urdu Yeh -> Persian Yeh ی
    .replace(/\u0643/g, '\u06A9') // Arabic Kaf ك -> Persian Kaf ک
    .replace(/[\u0623\u0625\u0671]/g, '\u0627') // Alef variants أ إ ٱ -> ا
    .replace(/\u0624/g, '\u0648') // Waw with hamza ؤ -> و
    .replace(/\u0629/g, '\u0647') // Teh marbuta ة -> ه
    .replace(/\u0640/g, '') // Strip Kashida/Tatweel ـ (e.g. تـخـفـیـف -> تخفیف)
    .replace(/[\u064B-\u065F\u0670]/g, '') // Strip Arabic harakat/diacritics
    .replace(/[\u200C\u200B\u200D\uFEFF]/g, ' ') // ZWNJ & zero-width chars -> space
    .replace(/[ \t]+/g, ' ')
    .trim();
}

const KNOWN_BANK_OR_GOV_SENDERS = new Set([
  'SHAPARAK',
  'BMI',
  'BANKMELLI',
  'SADAD',
  'MELLAT',
  'BANKMELLAT',
  'BLU',
  'BLUBANK',
  'SAMAN',
  'BANKSAMAN',
  'BANK-SAMAN',
  'PASARGAD',
  'BPI',
  'PARSIAN',
  'TEJARAT',
  'SADERAT',
  'BSI',
  'SEPAH',
  'KESHAVARZI',
  'MASKAN',
  'REFAH',
  'AYANDEH',
  'RESALAT',
  'GARDESHGARI',
  'KARAFARIN',
  'EGHTESAD',
  'SINA',
  'SHAHR',
  'DEY',
  'POSTBANK',
  'WEPOD',
  'ASANPARDKHT',
  'ADLIRAN',
  'NAJA',
  'POLICE',
  'POST',
  'DOLAT',
  'MYGOV',
  'SANJESH',
  'TAMIN',
  'GOOGLE',
  'TELEGRAM',
  'APPLE',
  'MICROSOFT',
  'WHATSAPP',
  'INSTAGRAM',
  'FACEBOOK',
  'SIGNAL',
  'AUTHMSG',
  'VERIFY',
  'GITHUB',
]);

const CARRIER_SENDERS = new Set([
  'HAMRAHAVAL',
  'MCI',
  'IRANCELL',
  'MTNIRANCELL',
  'RIGHTEL',
  'SHATEL',
  'SAMANTEL',
  '9990',
  '8999',
  '8080',
  '700',
  '4444',
  '9920',
]);

const IRAN_PROVINCIAL_AREA_CODES =
  /^(?:21|26|31|51|71|41|11|13|61|35|25|81|86|44|45|23|24|28|34|38|54|56|58|66|74|76|77|83|84|87|17)\d{6,8}$/;

export interface SenderAnalysis {
  senderType: SenderType;
  cleanedSender: string;
  bulkPrefix: string | null;
}

export function analyzeSender(rawSender: string, isSavedContact = false): SenderAnalysis {
  const cleaned = normalizeDigits(rawSender).replace(/[\s()-]/g, '').trim();
  const upper = cleaned.toUpperCase();

  if (isSavedContact) {
    return { senderType: 'SAVED_CONTACT', cleanedSender: cleaned, bulkPrefix: null };
  }

  if (KNOWN_BANK_OR_GOV_SENDERS.has(upper)) {
    return { senderType: 'KNOWN_BANK_OR_GOV', cleanedSender: upper, bulkPrefix: null };
  }

  if (CARRIER_SENDERS.has(upper)) {
    return { senderType: 'CARRIER_SENDER', cleanedSender: upper, bulkPrefix: null };
  }

  // Strip Iran country prefix (+98, 0098, 98) when analyzing numeric senders
  const numericOnly = cleaned.replace(/^\+/, '');
  let national = numericOnly;
  if (numericOnly.startsWith('0098') && numericOnly.length > 6) {
    national = numericOnly.slice(4);
  } else if (numericOnly.startsWith('98') && numericOnly.length >= 8) {
    national = numericOnly.slice(2);
  } else if (numericOnly.startsWith('0') && numericOnly.length >= 10) {
    national = numericOnly.slice(1);
  }

  // Iranian bulk SMS shortcodes: 1000..., 2000..., 3000..., 5000..., 9000...
  const bulkMatch = /^(1000|2000|3000|5000|9000)\d{2,11}$/.exec(national);
  if (bulkMatch) {
    return {
      senderType: 'IRAN_BULK_SHORTCODE',
      cleanedSender: cleaned,
      bulkPrefix: bulkMatch[1],
    };
  }

  // Iranian personal mobile SIM: 09xx xxx xxxx (national: 9xxxxxxxxx, 10 digits)
  if (/^9\d{9}$/.test(national)) {
    return { senderType: 'IRAN_PERSONAL_MOBILE', cleanedSender: cleaned, bulkPrefix: null };
  }

  // Iranian landline virtual SMS panels: 021..., 026..., 031..., etc.
  if (IRAN_PROVINCIAL_AREA_CODES.test(national)) {
    return { senderType: 'IRAN_LANDLINE_PANEL', cleanedSender: cleaned, bulkPrefix: null };
  }

  if (/[A-Z]/i.test(cleaned)) {
    return { senderType: 'ALPHANUMERIC_BRAND', cleanedSender: upper, bulkPrefix: null };
  }

  return { senderType: 'OTHER_NUMBER', cleanedSender: cleaned, bulkPrefix: null };
}

// Promotional "code" phrases that are NOT login/verification OTPs
const PROMO_CODE_EXCLUSION_REGEX =
  /(?:کد\s*(?:تخفیف|هدیه|قرعه|شانس|امتیاز|معرف|دعوت|کالا|محصول|ملک|فایل|پستی|رهگیری|مرسوله|پیگیری|اشتراک|ملی|اقتصادی)|(?:discount|promo|coupon|voucher|gift|referral|tracking)\s*code)/i;

// Explicit high-confidence OTP phrases (Persian, English, Finglish)
const SHAPARAK_DYNAMIC_OTP_REGEX =
  /(?:رمز\s*پویا|رمز\s*دوم(?:\s*یک\s*بار\s*مصرف)?|پویا\s*:|ramz(?:e)?\s*(?:pouya|pooya|dovom))/i;

const EXPLICIT_PERSIAN_OTP_REGEX =
  /(?:کد\s*تا[ییئ]+د|کد\s*ورود|کد\s*فعال\s*سازی|کد\s*امنیتی|کد\s*احراز\s*هویت|کد\s*اعتبار\s*سنجی|کد\s*یک\s*بار\s*مصرف|رمز\s*یک\s*بار\s*مصرف|رمز\s*ورود|رمز\s*موقت|کد\s*بازیابی|کد\s*تغییر\s*رمز|کد\s*فراموشی|کد\s*ثبت\s*نام|کد\s*عضویت|کد\s*محرمانه|کد\s*تحویل|پنجره\s*ملی\s*خدمات\s*دولت\s*هوشمند|این\s*کد\s*را\s*در\s*اختیار)/i;

const EXPLICIT_ENGLISH_FINGLISH_OTP_REGEX =
  /(?:verification\s*code|verify\s*code|login\s*code|security\s*code|confirmation\s*code|activation\s*code|authentication\s*code|auth\s*code|access\s*code|recovery\s*code|one[-\s]*time\s*(?:password|code|pin)|\botp\b|passcode|two[-\s]*factor|\b2fa\b|\bg-\d{6}\b|(?:telegram|whatsapp|instagram|signal|apple\s*id|google|microsoft|github)\s*(?:verification\s*)?code|is\s+your\s+(?:[\w\s]{1,24}\s+)?code|do\s+not\s+share\s+this\s+code|(?:kode?|code)\s*(?:taeed|taeid|vorood|vorud|faalsazi|amniati))/i;

const WEBOTP_STANDARD_REGEX = /(?:^<#>|@[a-z0-9.-]+\.[a-z]{2,}\s+#\d{4,8}\b)/im;

const STRONG_AD_BLOCKER_FOR_GENERIC_CODE_REGEX =
  /(?:تخفیف|حراج|فروش\s*ویژه|جشنواره|کاشت\s*مو|لیزر|ایمپلنت|بوتاکس|ویلا|پیش\s*فروش|شرط\s*بندی|کازینو|انفجار|بونوس|وام\s*فوری|قرعه\s*کشی|خرید\s*از\s*لینک)/i;

/**
 * Extracts a 4-8 digit OTP code from normalized text while avoiding dates, money amounts,
 * card fragments, or 5000xxxx bulk shortcode numbers.
 */
export function extractOtpCode(normalizedText: string): string | null {
  // 1. Google G-123456 format
  const googleMatch = /\bG-(\d{6})\b/i.exec(normalizedText);
  if (googleMatch) return googleMatch[1];

  // 2. WebOTP #123456 at end of message
  const webOtpMatch = /@[a-z0-9.-]+\.[a-z]{2,}\s+#(\d{4,8})\b/i.exec(normalizedText);
  if (webOtpMatch) return webOtpMatch[1];

  // 3. Code directly following OTP keyword (supports multi-line OTPs where code is on next line)
  const afterKeywordRegex =
    /(?:رمز\s*پویا|رمز\s*دوم|پویا|کد\s*تا[ییئ]+د|کد\s*ورود|کد\s*فعال\s*سازی|کد\s*امنیتی|کد\s*احراز\s*هویت|کد\s*اعتبار\s*سنجی|کد\s*یک\s*بار\s*مصرف|رمز\s*یک\s*بار\s*مصرف|رمز\s*ورود|رمز\s*موقت|کد\s*بازیابی|کد\s*ثبت\s*نام|کد\s*تحویل|کد\s*شما|verification\s*code|login\s*code|security\s*code|confirmation\s*code|activation\s*code|auth\s*code|\botp\b|passcode|(?:kode?|code)\s*(?:taeed|taeid|vorood|vorud|faalsazi)|ramz(?:e)?\s*(?:pouya|pooya)|(?:^|[\s\n])کد|(?:^|[\s\n])code)[^\d]{0,36}?(\d{4,8})(?!\d)/im;
  const afterMatch = afterKeywordRegex.exec(normalizedText);
  if (afterMatch) return afterMatch[1];

  // 4. Code preceding OTP keyword (e.g. "482910 is your verification code" or "482910 کد ورود شما")
  const beforeKeywordRegex =
    /\b(\d{4,8})\b[^\d]{0,32}?(?:is\s+your|verification\s*code|login\s*code|security\s*code|کد\s*تا[ییئ]+د|کد\s*ورود|کد\s*فعال\s*سازی|رمز\s*پویا)/i;
  const beforeMatch = beforeKeywordRegex.exec(normalizedText);
  if (beforeMatch) return beforeMatch[1];

  // 5. Fallback: find standalone 4-8 digit token that is not part of a date (1405/09/05),
  // comma-separated Rial amount (2,500,000), or phone/shortcode
  const sanitized = normalizedText
    .replace(/\b1[34]\d{2}[/-]\d{1,2}[/-]\d{1,2}\b/g, ' ') // Jalali dates
    .replace(/\b20\d{2}[/-]\d{1,2}[/-]\d{1,2}\b/g, ' ') // Gregorian dates
    .replace(/\d{1,3}(?:,\d{3})+/g, ' ') // Comma-formatted money
    .replace(/(?:\+98|0098|98|0)?9\d{9}\b/g, ' '); // Mobile numbers

  const genericDigits = /\b(\d{4,8})\b/.exec(sanitized);
  return genericDigits ? genericDigits[1] : null;
}

const URL_IN_SMS_REGEX =
  /(?:https?:\/\/[^\s]+|\b(?:bit\.ly|b2n\.ir|yun\.ir|cutt\.ly|zaya\.io|zil\.ink|t\.me)\/[^\s]+|\b[a-z0-9-]+\.(?:xyz|top|icu|click|online|site|pw|buzz|apk|ir|com|net|org)(?:\/[^\s]*)?)/i;

const APK_LINK_REGEX = /\.apk(?:\b|[/?#])/i;

const SHADY_PHISHING_DOMAIN_REGEX =
  /\b[a-z0-9-]+\.(?:xyz|top|icu|click|online|site|pw|buzz|cfd|sbs|rest)\b/i;

export function classifyIranSms(options: ClassifyOptions): ClassificationResult {
  const {
    sender,
    body,
    isSavedContact = false,
    filterMode = 'ALLOW_OTP_AND_BANK',
    customRules = [],
    neverBlockOtpEvenFromBlocked = true,
  } = options;

  const normalized = normalizeSmsText(body);
  const senderInfo = analyzeSender(sender, isSavedContact);
  const signals: MatchedSignal[] = [];

  // Pre-evaluate OTP presence so user block rules never accidentally swallow an OTP if protected
  const otpEvaluation = detectOtp(normalized);

  // --- LAYER 1: Custom User Allow / Block Rules & Contacts ---
  for (const rule of customRules) {
    const cleanPattern = normalizeSmsText(rule.pattern).toLowerCase();
    if (!cleanPattern) continue;
    const haystack =
      rule.target === 'SENDER'
        ? senderInfo.cleanedSender.toLowerCase()
        : normalized.toLowerCase();

    if (haystack.includes(cleanPattern)) {
      if (rule.action === 'ALLOW') {
        signals.push({
          code: 'USER_WHITELIST',
          label: `Matched custom allow rule "${rule.pattern}"`,
          weight: -100,
        });
        return {
          category: otpEvaluation.isOtp ? 'OTP' : 'PERSONAL',
          shouldBlock: false,
          confidence: 99,
          spamScore: 0,
          otpCode: otpEvaluation.otpCode,
          senderType: senderInfo.senderType,
          normalizedPreview: normalized,
          reasonSummary: `Allowed by your custom whitelist rule (${rule.pattern}).`,
          signals,
        };
      } else {
        if (neverBlockOtpEvenFromBlocked && otpEvaluation.isOtp) {
          signals.push({
            code: 'OTP_OVERRIDE_USER_BLOCK',
            label: 'OTP safety shield overrode sender block rule',
            weight: -100,
          });
        } else {
          signals.push({
            code: 'USER_BLACKLIST',
            label: `Matched custom block rule "${rule.pattern}"`,
            weight: 100,
          });
          return {
            category: 'SPAM_AD',
            shouldBlock: filterMode !== 'MONITOR_ONLY',
            confidence: 99,
            spamScore: 100,
            otpCode: null,
            senderType: senderInfo.senderType,
            normalizedPreview: normalized,
            reasonSummary: `Blocked by your custom rule (${rule.pattern}).`,
            signals,
          };
        }
      }
    }
  }

  if (senderInfo.senderType === 'SAVED_CONTACT') {
    signals.push({
      code: 'SAVED_CONTACT',
      label: 'Sender is in your saved phone contacts',
      weight: -90,
    });
    return {
      category: otpEvaluation.isOtp ? 'OTP' : 'PERSONAL',
      shouldBlock: false,
      confidence: 98,
      spamScore: 0,
      otpCode: otpEvaluation.otpCode,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary: 'Allowed because sender is in your saved contacts.',
      signals,
    };
  }

  // --- LAYER 2: High-Risk Iranian Phishing & Malware Scam Detector ---
  const hasUrl = URL_IN_SMS_REGEX.test(normalized);
  const hasApk = APK_LINK_REGEX.test(normalized);
  const hasShadyDomain = SHADY_PHISHING_DOMAIN_REGEX.test(normalized);

  if (hasApk) {
    signals.push({
      code: 'MALWARE_APK_LINK',
      label: 'Contains direct Android .apk file link (common banking trojan vector)',
      weight: 100,
    });
    return {
      category: 'PHISHING',
      shouldBlock: filterMode !== 'MONITOR_ONLY',
      confidence: 99,
      spamScore: 100,
      otpCode: null,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary: 'Blocked: SMS contains a malicious .apk download link.',
      signals,
    };
  }

  // Fake Judiciary / Sana (ابلاغیه ثنا) scam: real ADLIRAN never sends links or uses 09xx numbers
  const mentionsSanaOrCourt =
    /(?:سامانه\s*ثنا|ابلاغیه\s*(?:قضایی|ثنا|فوری|دادگاه|الکترونیکی)|قوه\s*قضاییه|شکواییه|حکم\s*جلب|دادسرا|توقیف\s*(?:اموال|حساب))/i.test(
      normalized
    );
  if (
    mentionsSanaOrCourt &&
    senderInfo.cleanedSender !== 'ADLIRAN' &&
    (senderInfo.senderType === 'IRAN_PERSONAL_MOBILE' || hasUrl || hasShadyDomain)
  ) {
    signals.push({
      code: 'FAKE_SANA_PHISHING',
      label: 'Fake Sana/Judiciary lure from non-ADLIRAN sender or with link',
      weight: 100,
    });
    return {
      category: 'PHISHING',
      shouldBlock: filterMode !== 'MONITOR_ONLY',
      confidence: 98,
      spamScore: 100,
      otpCode: null,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary:
        'Blocked Phishing: Fake Judiciary/Sana notice (official ADLIRAN never sends links or texts from 09xx numbers).',
      signals,
    };
  }

  // Fake Gov Subsidy / Edalat Shares / Kalabarg / Post fee / Bank freeze phishing with link
  const mentionsGovOrFreezeLure =
    /(?:سهام\s*عدالت|یارانه\s*(?:معیشتی|نقدی)?|کالابرگ|هدیه\s*دولت|(?:حساب|کارت)\s*(?:بانکی\s*)?شما\s*مسدود|رفع\s*مسدودی\s*(?:حساب|کارت)|نقص\s*آدرس.*مرسوله|مرسوله\s*پستی.*پرداخت\s*هزینه)/i.test(
      normalized
    );
  if (
    mentionsGovOrFreezeLure &&
    hasUrl &&
    senderInfo.senderType !== 'KNOWN_BANK_OR_GOV'
  ) {
    signals.push({
      code: 'GOV_BANK_PHISHING_LINK',
      label: 'Government subsidy / card-freeze / postal lure combined with external link',
      weight: 95,
    });
    return {
      category: 'PHISHING',
      shouldBlock: filterMode !== 'MONITOR_ONLY',
      confidence: 96,
      spamScore: 95,
      otpCode: null,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary:
        'Blocked Phishing: Suspicious subsidy, postal fee, or account-freeze message with an external link.',
      signals,
    };
  }

  // --- LAYER 3: OTP & Shaparak Dynamic PIN Priority Shield (NEVER BLOCK!) ---
  if (otpEvaluation.isOtp && otpEvaluation.otpCode) {
    signals.push({
      code: otpEvaluation.signalCode,
      label: otpEvaluation.signalLabel,
      weight: -100,
    });
    return {
      category: 'OTP',
      shouldBlock: false,
      confidence: otpEvaluation.confidence,
      spamScore: 0,
      otpCode: otpEvaluation.otpCode,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary: `Protected OTP (${otpEvaluation.otpCode}) — verification codes are never blocked.`,
      signals,
    };
  }

  // --- LAYER 4: Iranian Bank & Shaparak Transaction Alerts ---
  const hasBankTransactionAction =
    /(?:برداشت|واریز|انتقال\s*وجه|کارت\s*به\s*کارت|پایا|ساتنا|خرید\s*(?:با\s*کارت|کالا|پایانه|اینترنتی|:|\d)|تراکنش\s*خرید|پرداخت\s*قبض|سررسید\s*قسط|اقساط\s*تسهیلات|چک\s*صیادی|سود\s*سپرده|افزایش\s*موجودی|کاهش\s*موجودی)/i.test(
      normalized
    );
  const hasBankLedgerContext =
    /(?:مانده|موجودی|حساب\s*[:\d]|شماره\s*حساب|شبا|سپرده|شاپرک|بانک\s*(?:ملت|ملی|سامان|پاسارگاد|پارسیان|تجارت|صادرات|سپه|کشاورزی|مسکن|رفاه|آینده|رسالت|گردشگری|شهر|سینا|دی|پست)|بلوبانک|بلو،\s*بانک|ویپاد)/i.test(
      normalized
    ) || senderInfo.senderType === 'KNOWN_BANK_OR_GOV';
  const hasBankDisqualifyingAdTerms =
    /(?:بونوس|کازینو|انفجار|شرط\s*بندی|سایت\s*بت|تخفیف|جشنواره\s*فروش|قرعه\s*کشی|هدیه\s*ثبت\s*نام|حراج)/i.test(
      normalized
    );

  if (hasBankTransactionAction && hasBankLedgerContext && !hasBankDisqualifyingAdTerms) {
    signals.push({
      code: 'IRAN_BANK_LEDGER',
      label: 'Verified Iranian bank/Shaparak transaction & balance ledger',
      weight: -85,
    });
    const blockedInStrict = filterMode === 'STRICT_OTP_ONLY';
    return {
      category: 'BANK_ALERT',
      shouldBlock: blockedInStrict,
      confidence: 95,
      spamScore: 0,
      otpCode: null,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary: blockedInStrict
        ? 'Bank transaction alert (filtered because Strict OTP-Only mode is enabled).'
        : 'Allowed: Legitimate bank / Shaparak transaction alert.',
      signals,
    };
  }

  // --- LAYER 5: Essential Service, Delivery, Travel, Medical & Telecom Alerts ---
  const hasPromoTriggers =
    /(?:تخفیف|حراج|فروش\s*ویژه|جشنواره|آفر|پیشنهاد\s*(?:ویژه|شگفت)|کد\s*تخفیف|هدیه\s*ویژه|قرعه\s*کشی|شانس\s*برنده|بونوس|کازینو|انفجار|شرط\s*بندی|زیر\s*قیمت|دم\s*قسط)/i.test(
      normalized
    );

  const isServiceAlert =
    !hasPromoTriggers &&
    (/(?:سفارش\s*شما.*(?:ثبت|ارسال|آماده|تحویل)|کد\s*تحویل\s*(?:سفارش|مرسوله)?|(?:مرسوله|بسته)\s*(?:پستی|شما).*(?:تحویل|ارسال|توزیع)|کد\s*رهگیری\s*(?:مرسوله|پستی))/i.test(
      normalized
    ) ||
      /(?:(?:بلیط|بلیت)\s*(?:پرواز|قطار|اتوبوس)|شماره\s*پرواز|ساعت\s*حرکت|کارت\s*پرواز|رزرو\s*هتل.*قطعی)/i.test(
        normalized
      ) ||
      /(?:(?:راننده|سفیر).*(?:به\s*مبدا\s*رسید|در\s*مسیر)|سفر\s*شما\s*آغاز\s*شد)/i.test(
        normalized
      ) ||
      /(?:نوبت\s*(?:شما|پزشکی).*(?:ثبت|تاریخ|ساعت)|یادآوری\s*نوبت|نسخه\s*الکترونیک.*ثبت|بیمه\s*(?:تامین\s*اجتماعی|سلامت).*نسخه)/i.test(
        normalized
      ) ||
      /(?:(?:درصد|حجم|مگابایت|گیگابایت).*(?:بسته\s*اینترنت|اینترنت\s*شما).*(?:مصرف|باقی|اتمام)|بسته\s*اینترنت.*فعال\s*شد|(?:قبض|صورتحساب).*(?:شناسه\s*قبض|شناسه\s*پرداخت|مبلغ\s*قابل\s*پرداخت))/i.test(
        normalized
      ) ||
      (/(?:ابلاغیه.*سامانه\s*ثنا|پلیس\s*\+?10|تعویض\s*پلاک|گذرنامه.*تحویل)/i.test(
        normalized
      ) &&
        !hasUrl));

  if (isServiceAlert) {
    signals.push({
      code: 'ESSENTIAL_SERVICE_UPDATE',
      label: 'Transactional service, delivery, travel, medical, or utility notification',
      weight: -75,
    });
    const blockedInStrict = filterMode === 'STRICT_OTP_ONLY';
    return {
      category: 'SERVICE_ALERT',
      shouldBlock: blockedInStrict,
      confidence: 90,
      spamScore: 5,
      otpCode: null,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary: blockedInStrict
        ? 'Service notification (filtered because Strict OTP-Only mode is enabled).'
        : 'Allowed: Non-promotional service / delivery / utility update.',
      signals,
    };
  }

  // --- LAYER 6: Weighted Iranian Advertisement & Spam Scoring Engine ---
  let spamScore = 0;

  // 6a. Opt-out / Unsubscribe footer (classic Iranian bulk ad marker: لغو11 / لغو ارسال)
  if (
    /(?:لغو\s*11|لغو\s*=?\s*\d+|لغو\s*ارسال|لغو\s*پیامک|ارسال\s*(?:عدد\s*)?11\b|\blaghv\s*\d*\b|\bunsubscribe\b|reply\s*stop)/i.test(
      normalized
    )
  ) {
    spamScore += 55;
    signals.push({
      code: 'OPT_OUT_FOOTER',
      label: 'Contains bulk promotional opt-out footer (e.g. لغو11 / unsubscribe)',
      weight: 55,
    });
  }

  // 6b. Direct discount / sale / campaign language
  if (
    /(?:تخفیف|%\s*تخفیف|درصد\s*تخفیف|حراج|فروش\s*ویژه|فروش\s*فوق\s*العاده|جشنواره\s*(?:فروش|تخفیف|پاییزه|زمستانه|بهاره|تابستانه|یلدا|نوروز)?|آفر\s*ویژه|افر\s*ویژه|پیشنهاد\s*شگفت|زیر\s*قیمت|کد\s*تخفیف|هدیه\s*ویژه|بن\s*خرید|خرید\s*اقساطی|اقساط\s*بدون\s*(?:بهره|کارمزد|پیش\s*پرداخت)|دم\s*قسط)/i.test(
      normalized
    )
  ) {
    spamScore += 50;
    signals.push({
      code: 'PROMO_DISCOUNT_TERMS',
      label: 'Promotional discount, sale, or installment marketing offer',
      weight: 50,
    });
  }

  // 6c. Gambling / Betting / Casino (high-severity Iranian SMS spam)
  if (
    /(?:شرط\s*بندی|سایت\s*بت|\bbet\b.*bonus|بازی\s*انفجار|کازینو|بونوس|پیش\s*بینی\s*(?:فوتبال|ورزشی)|ضریب\s*بالا|شارژ\s*هدیه|درگاه\s*مستقیم\s*ووچر)/i.test(
      normalized
    )
  ) {
    spamScore += 85;
    signals.push({
      code: 'GAMBLING_CASINO_SPAM',
      label: 'Gambling, betting, or casino spam keywords',
      weight: 85,
    });
  }

  // 6d. Beauty clinics / Hair transplant / Laser / Dental ads
  if (
    /(?:کاشت\s*(?:مو|ابرو|ریش)|لیزر\s*موهای\s*زائد|لیزر\s*فول\s*بادی|بوتاکس|فیلر|ژل\s*لب|ایمپلنت\s*(?:دندان|فوری)?|لمینت\s*دندان|کامپوزیت\s*دندان|کلینیک\s*(?:زیبایی|پوست|تخصصی)|مزوتراپی|جوانسازی\s*پوست|لاغری\s*موضعی|هایفوتراپی)/i.test(
      normalized
    )
  ) {
    spamScore += 55;
    signals.push({
      code: 'BEAUTY_DENTAL_CLINIC_AD',
      label: 'Cosmetic, laser, hair-transplant, or dental clinic advertisement',
      weight: 55,
    });
  }

  // 6e. Real Estate / North Villa / Pre-sale apartment spam
  if (
    /(?:پیش\s*فروش\s*(?:آپارتمان|واحد|برج)|ویلا\s*(?:شمال|مدرن|جنگلی|ساحلی|استخردار)|زمین\s*(?:سنددار|شهرکی)|تهاتر\s*(?:ملک|ویلا|آپارتمان)|سرمایه\s*گذاری\s*ملکی|فایلینگ\s*املاک|اقساط\s*بلند\s*مدت.*(?:ویلا|زمین|آپارتمان))/i.test(
      normalized
    )
  ) {
    spamScore += 55;
    signals.push({
      code: 'REAL_ESTATE_VILLA_AD',
      label: 'Real estate pre-sale or northern villa advertisement',
      weight: 55,
    });
  }

  // 6f. Easy Loan / Crypto / Forex / Visa / Follower spam
  if (
    /(?:وام\s*(?:فوری|آزاد|بدون\s*ضامن|سیم\s*کارت|طلا|خودرو)|سیگنال\s*(?:ارز\s*دیجیتال|طلا|فارکس|بورس)|سود\s*تضمینی|درآمد\s*دلاری|خرید\s*فالوور|مهاجرت\s*تضمینی|اخذ\s*ویزا|ثبت\s*شرکت\s*فوری|اخذ\s*دسته\s*چک)/i.test(
      normalized
    )
  ) {
    spamScore += 55;
    signals.push({
      code: 'LOAN_CRYPTO_VISA_SPAM',
      label: 'Instant loan, crypto signal, guaranteed profit, or visa spam',
      weight: 55,
    });
  }

  // 6g. Carrier Lottery / VAS traps, seminars/courses, openings & local service spam
  if (
    /(?:قرعه\s*کشی|برنده\s*خوش\s*شانس|جایزه\s*(?:میلیونی|نقدی|بزرگ)|شانس\s*برنده\s*شدن|فرش\s*کاشان|قالیشویی|مبل\s*شویی|تور\s*(?:لحظه\s*آخری|کیش|مشهد|استانبول|آنتالیا)|باربری\s*شبانه\s*روزی|وبینار\s*رایگان|دوره\s*جامع|سمینار\s*آموزشی|افتتاحیه\s*(?:شعبه|فروشگاه|رستوران)|تعمیرگاه\s*(?:تخصصی|مجاز).*رایگان)/i.test(
      normalized
    )
  ) {
    spamScore += 50;
    signals.push({
      code: 'LOTTERY_RETAIL_PROMO',
      label: 'Lottery, VAS trap, seminar, store opening, or local service spam',
      weight: 50,
    });
  }

  // 6h. Finglish & English promotional spam
  if (
    /(?:\btakhfif\b|\bharaj\b|foroush\s*vijeh|kashte?\s*moo|moshavere\s*raygan|vame?\s*fori|\bcasino\b|free\s*bonus|limited\s*time\s*offer|\d+%\s*off\b|buy\s*now|flash\s*sale|promo\s*code)/i.test(
      normalized
    )
  ) {
    spamScore += 55;
    signals.push({
      code: 'FINGLISH_ENGLISH_AD',
      label: 'Finglish or English promotional marketing language',
      weight: 55,
    });
  }

  // 6i. Call-to-action spam triggers (send digit X, free consultation, urgency, link shorteners)
  if (
    /(?:عدد\s*\d+\s*را\s*(?:به\s*.*\s*)?ارسال|ارسال\s*عدد\s*\d+|مشاوره\s*رایگان|همین\s*حالا\s*(?:کلیک|خرید|تماس)|لینک\s*(?:خرید|ثبت\s*نام|زیر)|ظرفیت\s*محدود|فقط\s*تا\s*پایان\s*امشب|مهلت\s*استفاده)/i.test(
      normalized
    )
  ) {
    spamScore += 35;
    signals.push({
      code: 'MARKETING_CTA',
      label: 'Marketing call-to-action ("send digit X", "free consultation", urgency)',
      weight: 35,
    });
  }

  // 6j. Shortened marketing URLs (yun.ir, b2n.ir, bit.ly, etc.)
  if (/\b(?:yun\.ir|b2n\.ir|bit\.ly|cutt\.ly|zaya\.io|zil\.ink|t\.me)\//i.test(normalized)) {
    spamScore += 25;
    signals.push({
      code: 'SHORTENED_PROMO_LINK',
      label: 'Contains shortened marketing link (yun.ir / b2n.ir / bit.ly / t.me)',
      weight: 25,
    });
  }

  // 6k. Sender context weights (Iranian bulk SMS shortcodes & landline panels)
  if (senderInfo.senderType === 'IRAN_BULK_SHORTCODE') {
    const bulkWeight =
      senderInfo.bulkPrefix === '5000' || senderInfo.bulkPrefix === '2000' ? 25 : 20;
    spamScore += bulkWeight;
    signals.push({
      code: 'BULK_SMS_SHORTCODE',
      label: `Sent from Iranian bulk SMS panel (${senderInfo.bulkPrefix}...)`,
      weight: bulkWeight,
    });

    // Soft clinic/business ads from bulk panels containing callback numbers
    if (
      /(?:مشاوره|رزرو|تماس|آدرس|کلینیک|فروشگاه|گالری|مزون|آموزشگاه)/i.test(normalized) &&
      /(?:\b021\d{6,8}\b|\b09\d{9}\b)/.test(normalized)
    ) {
      spamScore += 30;
      signals.push({
        code: 'BULK_CALLBACK_BUSINESS_AD',
        label: 'Bulk panel message advertising business callback number',
        weight: 30,
      });
    }
  } else if (senderInfo.senderType === 'IRAN_LANDLINE_PANEL') {
    spamScore += 25;
    signals.push({
      code: 'LANDLINE_SMS_PANEL',
      label: 'Sent from virtual landline SMS panel (021 / provincial prefix)',
      weight: 25,
    });
    if (/(?:مشاوره|رزرو|تماس|کلینیک|فروشگاه|گالری|مزون|آموزشگاه|خدمات)/i.test(normalized)) {
      spamScore += 30;
      signals.push({
        code: 'LANDLINE_BUSINESS_PROMO',
        label: 'Landline panel promoting commercial services',
        weight: 30,
      });
    }
  } else if (senderInfo.senderType === 'CARRIER_SENDER') {
    if (/(?:جشنواره|برنده|جایزه|امتیاز|بسته\s*پیشنهادی|هدیه|تخفیف|سرگرمی)/i.test(normalized)) {
      spamScore += 45;
      signals.push({
        code: 'CARRIER_PROMO_BLAST',
        label: 'Mobile carrier promotional / VAS marketing blast',
        weight: 45,
      });
    }
  }

  const cappedScore = Math.min(100, spamScore);

  if (cappedScore >= 50) {
    return {
      category: 'SPAM_AD',
      shouldBlock: filterMode !== 'MONITOR_ONLY',
      confidence: Math.min(99, 60 + Math.round((cappedScore - 50) * 0.78)),
      spamScore: cappedScore,
      otpCode: null,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary: 'Blocked: Detected as advertisement / promotional spam.',
      signals,
    };
  }

  if (
    filterMode === 'STRICT_OTP_ONLY' &&
    (senderInfo.senderType === 'IRAN_BULK_SHORTCODE' ||
      senderInfo.senderType === 'IRAN_LANDLINE_PANEL' ||
      senderInfo.senderType === 'CARRIER_SENDER')
  ) {
    signals.push({
      code: 'STRICT_MODE_NON_OTP_SHORTCODE',
      label: 'Strict OTP-Only mode blocks non-OTP shortcode messages',
      weight: 60,
    });
    return {
      category: 'SPAM_AD',
      shouldBlock: true,
      confidence: 85,
      spamScore: Math.max(cappedScore, 60),
      otpCode: null,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary: 'Blocked by Strict OTP-Only mode (non-OTP message from bulk/carrier line).',
      signals,
    };
  }

  // --- LAYER 7: Clean Personal / Conversational SMS ---
  signals.push({
    code: 'CLEAN_CONVERSATIONAL',
    label: 'Conversational message with no spam or phishing indicators',
    weight: -50,
  });
  return {
    category: 'PERSONAL',
    shouldBlock: false,
    confidence: 88,
    spamScore: cappedScore,
    otpCode: null,
    senderType: senderInfo.senderType,
    normalizedPreview: normalized,
    reasonSummary: 'Allowed: Personal or normal conversational message.',
    signals,
  };
}

interface OtpDetection {
  isOtp: boolean;
  otpCode: string | null;
  confidence: number;
  signalCode: string;
  signalLabel: string;
}

function detectOtp(normalized: string): OtpDetection {
  // Reject promotional "discount/lottery/gift code" traps unless a genuine OTP phrase is also present
  const hasPromoCodeTrap = PROMO_CODE_EXCLUSION_REGEX.test(normalized);

  // 1. Shaparak Dynamic Card OTP (رمز پویا / رمز دوم)
  if (SHAPARAK_DYNAMIC_OTP_REGEX.test(normalized)) {
    const code = extractOtpCode(normalized);
    if (code) {
      return {
        isOtp: true,
        otpCode: code,
        confidence: 99,
        signalCode: 'SHAPARAK_DYNAMIC_PIN',
        signalLabel: 'Shaparak / Bank Dynamic Card PIN (رمز پویا)',
      };
    }
  }

  // 2. Android WebOTP / SMS Retriever hash standard
  if (WEBOTP_STANDARD_REGEX.test(normalized)) {
    const code = extractOtpCode(normalized);
    if (code) {
      return {
        isOtp: true,
        otpCode: code,
        confidence: 99,
        signalCode: 'WEBOTP_STANDARD',
        signalLabel: 'Standard Android WebOTP / SMS Retriever verification code',
      };
    }
  }

  // 3. Explicit Persian OTP phrase (کد تایید / کد ورود / کد فعالسازی / کد امنیتی...)
  if (EXPLICIT_PERSIAN_OTP_REGEX.test(normalized)) {
    const code = extractOtpCode(normalized);
    if (code) {
      return {
        isOtp: true,
        otpCode: code,
        confidence: 99,
        signalCode: 'PERSIAN_OTP_VERIFICATION',
        signalLabel: 'Persian verification / login OTP phrase + numeric code',
      };
    }
  }

  // 4. Explicit English or Finglish OTP phrase
  if (!hasPromoCodeTrap && EXPLICIT_ENGLISH_FINGLISH_OTP_REGEX.test(normalized)) {
    const code = extractOtpCode(normalized);
    if (code) {
      return {
        isOtp: true,
        otpCode: code,
        confidence: 98,
        signalCode: 'ENGLISH_FINGLISH_OTP',
        signalLabel: 'English / Finglish verification OTP phrase + numeric code',
      };
    }
  }

  // 5. Concise generic "کد: 12345" / "کد شما: 12345" / "Code: 12345" without promo indicators
  if (
    !hasPromoCodeTrap &&
    normalized.length <= 180 &&
    !STRONG_AD_BLOCKER_FOR_GENERIC_CODE_REGEX.test(normalized)
  ) {
    const shortCodeMatch =
      /(?:^|[\s:\n])(?:کد(?:\s*شما)?|code)\s*[:=-]?\s*(\d{4,8})(?!\d)/im.exec(normalized);
    if (shortCodeMatch) {
      return {
        isOtp: true,
        otpCode: shortCodeMatch[1],
        confidence: 93,
        signalCode: 'SHORT_STRUCTURAL_OTP',
        signalLabel: 'Concise login/verification code format',
      };
    }
  }

  return {
    isOtp: false,
    otpCode: null,
    confidence: 0,
    signalCode: '',
    signalLabel: '',
  };
}
