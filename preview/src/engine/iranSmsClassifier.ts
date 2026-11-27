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
  weight: number;
}

export interface ClassificationResult {
  category: SmsCategory;
  shouldBlock: boolean;
  confidence: number;
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
  zeroToleranceCommercialPanels?: boolean;
}

const PERSIAN_ZERO = 0x06f0;
const ARABIC_ZERO = 0x0660;

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
    .replace(/[\u064A\u0649\u06D2]/g, '\u06CC')
    .replace(/\u0643/g, '\u06A9')
    .replace(/[\u0623\u0625\u0671]/g, '\u0627')
    .replace(/\u0624/g, '\u0648')
    .replace(/\u0629/g, '\u0647')
    .replace(/\u0640/g, '')
    .replace(/[\u064B-\u065F\u0670]/g, '')
    .replace(/[\u200C\u200B\u200D\uFEFF]/g, ' ')
    .replace(/[ \t]+/g, ' ')
    .trim();
}

/**
 * Canonicalizes Iranian phone numbers so "+989121234567", "09121234567", and "989121234567"
 * group into the exact same SMS conversation thread.
 */
const STRIP_SENDER_FORMATTING_REGEX = /[\s()\-\u200B-\u200F\u202A-\u202E\u2060-\u2069\uFEFF]/g;

export function canonicalizeThreadAddress(rawSender: string): string {
  const cleaned = normalizeDigits(rawSender).replace(STRIP_SENDER_FORMATTING_REGEX, '').trim();
  if (/[A-Za-z\u0600-\u06FF]/.test(cleaned)) return cleaned.toUpperCase();
  const noPlus = cleaned.replace(/^\+/, '');
  if (/^00989\d{9}$/.test(noPlus)) return `+98${noPlus.slice(4)}`;
  if (/^989\d{9}$/.test(noPlus)) return `+${noPlus}`;
  if (/^09\d{9}$/.test(noPlus)) return `+98${noPlus.slice(1)}`;
  if (/^9\d{9}$/.test(noPlus)) return `+98${noPlus}`;
  return cleaned;
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
  'TIPAX',
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
  const cleaned = normalizeDigits(rawSender).replace(STRIP_SENDER_FORMATTING_REGEX, '').trim();
  const upper = cleaned.toUpperCase();

  if (isSavedContact) {
    return { senderType: 'SAVED_CONTACT', cleanedSender: cleaned, bulkPrefix: null };
  }

  if (KNOWN_BANK_OR_GOV_SENDERS.has(upper)) {
    return { senderType: 'KNOWN_BANK_OR_GOV', cleanedSender: upper, bulkPrefix: null };
  }

  if (CARRIER_SENDERS.has(upper) || upper.includes('IRANCELL') || upper.includes('HAMRAHAVAL')) {
    return { senderType: 'CARRIER_SENDER', cleanedSender: upper, bulkPrefix: null };
  }

  const numericOnly = cleaned.replace(/^\+/, '');
  let national = numericOnly;
  if (numericOnly.startsWith('0098') && numericOnly.length > 6) {
    national = numericOnly.slice(4);
  } else if (numericOnly.startsWith('98') && numericOnly.length >= 6) {
    national = numericOnly.slice(2);
  } else if (numericOnly.startsWith('0') && numericOnly.length >= 10) {
    national = numericOnly.slice(1);
  }

  const bulkMatch = /^(1000|2000|3000|4000|5000|9000)\d{1,12}$/.exec(national);
  if (bulkMatch) {
    return {
      senderType: 'IRAN_BULK_SHORTCODE',
      cleanedSender: cleaned,
      bulkPrefix: bulkMatch[1],
    };
  }

  if (/^9\d{9}$/.test(national)) {
    return { senderType: 'IRAN_PERSONAL_MOBILE', cleanedSender: cleaned, bulkPrefix: null };
  }

  if (IRAN_PROVINCIAL_AREA_CODES.test(national)) {
    return { senderType: 'IRAN_LANDLINE_PANEL', cleanedSender: cleaned, bulkPrefix: null };
  }

  if (/^\d{3,8}$/.test(national)) {
    return {
      senderType: 'IRAN_BULK_SHORTCODE',
      cleanedSender: cleaned,
      bulkPrefix: national.slice(0, 4),
    };
  }

  if (/[A-Za-z\u0600-\u06FF]/.test(cleaned)) {
    return { senderType: 'ALPHANUMERIC_BRAND', cleanedSender: upper, bulkPrefix: null };
  }

  return { senderType: 'OTHER_NUMBER', cleanedSender: cleaned, bulkPrefix: null };
}

const PROMO_CODE_EXCLUSION_REGEX =
  /(?:کد\s*(?:تخفیف|هدیه|قرعه|شانس|امتیاز|معرف|دعوت|کالا|محصول|ملک|فایل|پستی|رهگیری|مرسوله|پیگیری|اشتراک|ملی|اقتصادی)|(?:discount|promo|coupon|voucher|gift|referral|tracking)\s*code)/i;

const SHAPARAK_DYNAMIC_OTP_REGEX =
  /(?:رمز\s*پویا|رمز\s*دوم(?:\s*یک\s*بار\s*مصرف)?|پویا\s*:|ramz(?:e)?\s*(?:pouya|pooya|dovom))/i;

const EXPLICIT_PERSIAN_OTP_REGEX =
  /(?:کد\s*تا[ییئ]+د(?:یه)?|کد\s*ورود|کد\s*فعال\s*سازی|کد\s*امنیتی|کد\s*احراز\s*هویت|کد\s*اعتبار\s*سنجی|کد\s*یک\s*بار\s*مصرف|رمز\s*یک\s*بار\s*مصرف|رمز\s*ورود|رمز\s*(?:عبور\s*)?موقت|کد\s*بازیابی|کد\s*تغییر\s*رمز|کد\s*فراموشی|کد\s*ثبت\s*نام|کد\s*عضویت|کد\s*محرمانه|کد\s*تحویل|کد\s*سجام|کد\s*امضا|پنجره\s*ملی\s*خدمات\s*دولت\s*هوشمند|این\s*کد\s*را\s*در\s*اختیار)/i;

const EXPLICIT_ENGLISH_FINGLISH_OTP_REGEX =
  /(?:verification\s*code|verify\s*code|login\s*code|security\s*code|confirmation\s*code|activation\s*code|authentication\s*code|auth\s*code|access\s*code|recovery\s*code|one[-\s]*time\s*(?:password|code|pin)|\botp\b|passcode|two[-\s]*factor|\b2fa\b|\bg-\d{6}\b|(?:telegram|whatsapp|instagram|signal|apple\s*id|google|microsoft|github)\s*(?:verification\s*)?code|is\s+your\s+(?:[\w\s]{1,24}\s+)?code|do\s+not\s+share\s+this\s+code|(?:kode?|code)\s*(?:taeed|taeid|vorood|vorud|faalsazi|amniati))/i;

const WEBOTP_STANDARD_REGEX = /(?:^<#>|@[a-z0-9.-]+\.[a-z]{2,}\s+#\d{4,8}\b)/im;

const STRONG_AD_BLOCKER_FOR_GENERIC_CODE_REGEX =
  /(?:تخفیف|حراج|فروش|جشنواره|کاشت\s*مو|لیزر|ایمپلنت|بوتاکس|ویلا|پیش\s*فروش|شرط\s*بندی|کازینو|انفجار|بونوس|وام|قرعه\s*کشی|خرید\s*از|مشاوره|قسط|تومان|ریال|%)/i;

export function extractOtpCode(normalizedText: string): string | null {
  const googleMatch = /\bG-(\d{6})\b/i.exec(normalizedText);
  if (googleMatch) return googleMatch[1];

  const webOtpMatch = /@[a-z0-9.-]+\.[a-z]{2,}\s+#(\d{4,8})\b/i.exec(normalizedText);
  if (webOtpMatch) return webOtpMatch[1];

  const afterKeywordRegex =
    /(?:رمز\s*پویا|رمز\s*دوم|پویا|کد\s*تا[ییئ]+د(?:یه)?|کد\s*ورود|کد\s*فعال\s*سازی|کد\s*امنیتی|کد\s*احراز\s*هویت|کد\s*اعتبار\s*سنجی|کد\s*یک\s*بار\s*مصرف|رمز\s*یک\s*بار\s*مصرف|رمز\s*ورود|رمز\s*(?:عبور\s*)?موقت|کد\s*بازیابی|کد\s*ثبت\s*نام|کد\s*تحویل|کد\s*سجام|کد\s*شما|verification\s*code|login\s*code|security\s*code|confirmation\s*code|activation\s*code|auth\s*code|\botp\b|passcode|(?:kode?|code)\s*(?:taeed|taeid|vorood|vorud|faalsazi)|ramz(?:e)?\s*(?:pouya|pooya)|(?:^|[\s\n])کد|(?:^|[\s\n])رمز|(?:^|[\s\n])code)[^\d]{0,36}?(\d{4,8})(?!\d)/im;
  const afterMatch = afterKeywordRegex.exec(normalizedText);
  if (afterMatch) return afterMatch[1];

  const beforeKeywordRegex =
    /\b(\d{4,8})\b[^\d]{0,32}?(?:is\s+your|verification\s*code|login\s*code|security\s*code|کد\s*تا[ییئ]+د|کد\s*ورود|کد\s*فعال\s*سازی|رمز\s*پویا)/i;
  const beforeMatch = beforeKeywordRegex.exec(normalizedText);
  if (beforeMatch) return beforeMatch[1];

  const sanitized = normalizedText
    .replace(/\b1[34]\d{2}[/-]\d{1,2}[/-]\d{1,2}\b/g, ' ')
    .replace(/\b20\d{2}[/-]\d{1,2}[/-]\d{1,2}\b/g, ' ')
    .replace(/\d{1,3}(?:,\d{3})+/g, ' ')
    .replace(/(?:\+98|0098|98|0)?9\d{9}\b/g, ' ');

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
    zeroToleranceCommercialPanels = true,
  } = options;

  const normalized = normalizeSmsText(body);
  const senderInfo = analyzeSender(sender, isSavedContact);
  const signals: MatchedSignal[] = [];

  const otpEvaluation = detectOtp(normalized, senderInfo.senderType);

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
            reasonSummary: `Instantly blocked by your custom rule (${rule.pattern}).`,
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
      label: 'Contains direct Android .apk file link (banking trojan vector)',
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
      reasonSummary: 'Blocked silently: Malicious .apk download link.',
      signals,
    };
  }

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
        'Blocked silently: Fake Judiciary/Sana phishing (ADLIRAN never sends links or texts from 09xx numbers).',
      signals,
    };
  }

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
      label: 'Subsidy / card-freeze / postal lure combined with external link',
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
        'Blocked silently: Suspicious subsidy, postal fee, or account-freeze phishing link.',
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
      reasonSummary: `Protected OTP (${otpEvaluation.otpCode}) — delivered immediately.`,
      signals,
    };
  }

  // --- LAYER 4: Iranian Bank & Shaparak Transaction Alerts ---
  const hasBankTransactionAction =
    /(?:برداشت|واریز|انتقال\s*وجه|کارت\s*به\s*کارت|پایا|ساتنا|خرید\s*(?:با\s*کارت|کالا|پایانه|اینترنتی|:|\d)|تراکنش\s*خرید|پرداخت\s*قبض|سررسید\s*قسط|اقساط\s*تسهیلات|چک\s*صیادی|سود\s*سپرده|افزایش\s*موجودی|کاهش\s*موجودی|کارت\s*بانکی\s*شما\s*صادر)/i.test(
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
        ? 'Bank alert (silenced in Strict OTP-Only mode).'
        : 'Allowed: Verified bank / Shaparak transaction alert.',
      signals,
    };
  }

  // --- LAYER 5: Essential Service, Delivery, Travel, Medical & Utility Alerts ---
  const hasPromoTriggers =
    /(?:تخفیف|حراج|فروش\s*ویژه|جشنواره|آفر|پیشنهاد\s*(?:ویژه|شگفت)|کد\s*تخفیف|هدیه\s*ویژه|قرعه\s*کشی|شانس\s*برنده|بونوس|کازینو|انفجار|شرط\s*بندی|زیر\s*قیمت|دم\s*قسط)/i.test(
      normalized
    );

  const isServiceAlert =
    !hasPromoTriggers &&
    (/(?:سفارش\s*شما.*(?:ثبت|ارسال|آماده|تحویل)|کد\s*تحویل\s*(?:سفارش|مرسوله)?|(?:مرسوله|بسته)\s*(?:پستی|شما|تیپاکس).*(?:تحویل|ارسال|توزیع)|کد\s*رهگیری\s*(?:مرسوله|پستی)|بارنامه)/i.test(
      normalized
    ) ||
      /(?:(?:بلیط|بلیت)\s*(?:پرواز|قطار|اتوبوس)|شماره\s*پرواز|ساعت\s*حرکت|کارت\s*پرواز|تاخیر\s*پرواز|رزرو\s*هتل.*قطعی)/i.test(
        normalized
      ) ||
      /(?:(?:راننده|سفیر).*(?:به\s*مبدا\s*رسید|در\s*مسیر)|سفر\s*شما\s*آغاز\s*شد)/i.test(
        normalized
      ) ||
      /(?:نوبت\s*(?:شما|پزشکی).*(?:ثبت|تاریخ|ساعت)|یادآوری\s*نوبت|نسخه\s*الکترونیک.*ثبت|بیمه\s*(?:تامین\s*اجتماعی|سلامت).*نسخه|جواب\s*آزمایش)/i.test(
        normalized
      ) ||
      /(?:(?:درصد|حجم|مگابایت|گیگابایت).*(?:بسته\s*اینترنت|اینترنت\s*شما).*(?:مصرف|باقی|اتمام)|بسته\s*اینترنت.*فعال\s*شد|(?:قبض|صورتحساب).*(?:شناسه\s*قبض|شناسه\s*پرداخت|مبلغ\s*قابل\s*پرداخت)|قطعی\s*برق)/i.test(
        normalized
      ) ||
      (/(?:ابلاغیه.*سامانه\s*ثنا|پلیس\s*\+?10|تعویض\s*پلاک|گذرنامه.*تحویل|سازمان\s*سنجش|انتخاب\s*واحد)/i.test(
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
      confidence: 91,
      spamScore: 5,
      otpCode: null,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary: blockedInStrict
        ? 'Service notification (silenced in Strict OTP-Only mode).'
        : 'Allowed: Non-promotional service / delivery / utility update.',
      signals,
    };
  }

  // --- LAYER 6: Weighted Advertisement, Commercial & Spam Engine ---
  let spamScore = 0;

  if (
    /(?:لغو\s*11|لغو\s*=?\s*\d+|لغو\s*ارسال|لغو\s*پیامک|ارسال\s*(?:عدد\s*)?11\b|\blaghv\s*\d*\b|\bunsubscribe\b|reply\s*stop)/i.test(
      normalized
    )
  ) {
    spamScore += 60;
    signals.push({
      code: 'OPT_OUT_FOOTER',
      label: 'Contains bulk promotional opt-out footer (لغو11 / unsubscribe)',
      weight: 60,
    });
  }

  if (
    /(?:تخفیف|%\s*تخفیف|درصد\s*تخفیف|حراج|فروش\s*ویژه|فروش\s*فوق\s*العاده|جشنواره\s*(?:فروش|تخفیف|پاییزه|زمستانه|بهاره|تابستانه|یلدا|نوروز)?|آفر\s*ویژه|افر\s*ویژه|پیشنهاد\s*شگفت|زیر\s*قیمت|کد\s*تخفیف|هدیه\s*ویژه|بن\s*خرید|خرید\s*اقساطی|اقساط\s*بدون\s*(?:بهره|کارمزد|پیش\s*پرداخت)|دم\s*قسط|پخش\s*عمده|زیر\s*قیمت\s*بازار|ارسال\s*رایگان)/i.test(
      normalized
    )
  ) {
    spamScore += 55;
    signals.push({
      code: 'PROMO_DISCOUNT_TERMS',
      label: 'Promotional discount, sale, wholesale, or installment marketing offer',
      weight: 55,
    });
  }

  if (
    /(?:شرط\s*بندی|سایت\s*بت|\bbet\b.*bonus|بازی\s*انفجار|کازینو|بونوس|پیش\s*بینی\s*(?:فوتبال|ورزشی)|ضریب\s*بالا|شارژ\s*هدیه|درگاه\s*مستقیم\s*ووچر)/i.test(
      normalized
    )
  ) {
    spamScore += 90;
    signals.push({
      code: 'GAMBLING_CASINO_SPAM',
      label: 'Gambling, betting, or casino spam keywords',
      weight: 90,
    });
  }

  if (
    /(?:کاشت\s*(?:مو|ابرو|ریش)|لیزر\s*موهای\s*زائد|لیزر\s*فول\s*بادی|بوتاکس|فیلر|ژل\s*لب|ایمپلنت\s*(?:دندان|فوری)?|لمینت\s*دندان|کامپوزیت\s*دندان|کلینیک\s*(?:زیبایی|پوست|تخصصی)|مزوتراپی|جوانسازی\s*پوست|لاغری\s*موضعی|هایفوتراپی)/i.test(
      normalized
    )
  ) {
    spamScore += 60;
    signals.push({
      code: 'BEAUTY_DENTAL_CLINIC_AD',
      label: 'Cosmetic, laser, hair-transplant, or dental clinic advertisement',
      weight: 60,
    });
  }

  if (
    /(?:پیش\s*فروش\s*(?:آپارتمان|واحد|برج|خودرو)|ویلا\s*(?:شمال|مدرن|جنگلی|ساحلی|استخردار)|زمین\s*(?:سنددار|شهرکی)|تهاتر\s*(?:ملک|ویلا|آپارتمان)|سرمایه\s*گذاری\s*ملکی|فایلینگ\s*املاک|اقساط\s*بلند\s*مدت.*(?:ویلا|زمین|آپارتمان))/i.test(
      normalized
    )
  ) {
    spamScore += 60;
    signals.push({
      code: 'REAL_ESTATE_VILLA_AD',
      label: 'Real estate pre-sale or northern villa advertisement',
      weight: 60,
    });
  }

  if (
    /(?:وام\s*(?:فوری|آزاد|بدون\s*ضامن|سیم\s*کارت|طلا|خودرو)|سیگنال\s*(?:ارز\s*دیجیتال|طلا|فارکس|بورس)|سود\s*تضمینی|درآمد\s*دلاری|خرید\s*فالوور|مهاجرت\s*تضمینی|اخذ\s*ویزا|ثبت\s*شرکت\s*فوری|اخذ\s*دسته\s*چک)/i.test(
      normalized
    )
  ) {
    spamScore += 60;
    signals.push({
      code: 'LOAN_CRYPTO_VISA_SPAM',
      label: 'Instant loan, crypto signal, guaranteed profit, or visa spam',
      weight: 60,
    });
  }

  if (
    /(?:قرعه\s*کشی|برنده\s*خوش\s*شانس|جایزه\s*(?:میلیونی|نقدی|بزرگ)|شانس\s*برنده\s*شدن|فرش\s*کاشان|قالیشویی|مبل\s*شویی|تور\s*(?:لحظه\s*آخری|کیش|مشهد|استانبول|آنتالیا)|باربری\s*شبانه\s*روزی|وبینار\s*رایگان|دوره\s*جامع|سمینار\s*آموزشی|افتتاحیه\s*(?:شعبه|فروشگاه|رستوران)|تعمیرگاه\s*(?:تخصصی|مجاز).*رایگان)/i.test(
      normalized
    )
  ) {
    spamScore += 55;
    signals.push({
      code: 'LOTTERY_RETAIL_PROMO',
      label: 'Lottery, VAS trap, seminar, store opening, or local service spam',
      weight: 55,
    });
  }

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

  if (
    /(?:عدد\s*\d+\s*را\s*(?:به\s*.*\s*)?ارسال|ارسال\s*(?:عدد|کلمه)\s*\S+|مشاوره\s*رایگان|همین\s*حالا\s*(?:کلیک|خرید|تماس)|لینک\s*(?:خرید|ثبت\s*نام|زیر)|ظرفیت\s*محدود|فقط\s*تا\s*پایان\s*امشب|مهلت\s*استفاده)/i.test(
      normalized
    )
  ) {
    spamScore += 40;
    signals.push({
      code: 'MARKETING_CTA',
      label: 'Marketing call-to-action ("send digit/word", "free consultation", urgency)',
      weight: 40,
    });
  }

  if (/\b(?:yun\.ir|b2n\.ir|bit\.ly|cutt\.ly|zaya\.io|zil\.ink|t\.me)\//i.test(normalized)) {
    spamScore += 30;
    signals.push({
      code: 'SHORTENED_PROMO_LINK',
      label: 'Contains shortened marketing link (yun.ir / b2n.ir / bit.ly / t.me)',
      weight: 30,
    });
  }

  // Zero-Tolerance Commercial Panel Shield:
  // No real personal friend texts from 1000/2000/3000/5000/9000 bulk panels or 021 landline panels!
  // Since OTPs (Layer 3), Bank ledgers (Layer 4), and Service alerts (Layer 5) are ALREADY allowed above,
  // any remaining message from commercial SMS panels is commercial/promotional traffic.
  if (
    zeroToleranceCommercialPanels &&
    (senderInfo.senderType === 'IRAN_BULK_SHORTCODE' ||
      senderInfo.senderType === 'IRAN_LANDLINE_PANEL')
  ) {
    spamScore += 55;
    signals.push({
      code: 'COMMERCIAL_SMS_PANEL_NON_TRANSACTIONAL',
      label:
        senderInfo.senderType === 'IRAN_BULK_SHORTCODE'
          ? `Non-OTP / non-bank message from bulk SMS panel (${senderInfo.bulkPrefix}...)`
          : 'Non-OTP / non-service message from commercial landline SMS panel',
      weight: 55,
    });
  } else if (senderInfo.senderType === 'IRAN_BULK_SHORTCODE') {
    const bulkWeight =
      senderInfo.bulkPrefix === '5000' || senderInfo.bulkPrefix === '2000' ? 25 : 20;
    spamScore += bulkWeight;
    signals.push({
      code: 'BULK_SMS_SHORTCODE',
      label: `Sent from Iranian bulk SMS panel (${senderInfo.bulkPrefix}...)`,
      weight: bulkWeight,
    });
  } else if (senderInfo.senderType === 'IRAN_LANDLINE_PANEL') {
    spamScore += 25;
    signals.push({
      code: 'LANDLINE_SMS_PANEL',
      label: 'Sent from virtual landline SMS panel',
      weight: 25,
    });
  } else if (senderInfo.senderType === 'CARRIER_SENDER') {
    if (
      zeroToleranceCommercialPanels ||
      /(?:جشنواره|برنده|جایزه|امتیاز|بسته\s*پیشنهادی|هدیه|تخفیف|سرگرمی)/i.test(normalized)
    ) {
      spamScore += 55;
      signals.push({
        code: 'CARRIER_PROMO_BLAST',
        label: 'Non-quota carrier promotional / VAS marketing message',
        weight: 55,
      });
    }
  }

  const cappedScore = Math.min(100, spamScore);

  if (cappedScore >= 50) {
    return {
      category: 'SPAM_AD',
      shouldBlock: filterMode !== 'MONITOR_ONLY',
      confidence: Math.min(99, 65 + Math.round((cappedScore - 50) * 0.68)),
      spamScore: cappedScore,
      otpCode: null,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary:
        'Instantly blocked at radio level (0 notifications): Detected as promotional / commercial SMS.',
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
      confidence: 88,
      spamScore: Math.max(cappedScore, 60),
      otpCode: null,
      senderType: senderInfo.senderType,
      normalizedPreview: normalized,
      reasonSummary: 'Blocked silently by Strict OTP-Only mode.',
      signals,
    };
  }

  // --- LAYER 7: Clean Personal / Conversational SMS ---
  signals.push({
    code: 'CLEAN_CONVERSATIONAL',
    label: 'Conversational message from personal sender with no spam indicators',
    weight: -50,
  });
  return {
    category: 'PERSONAL',
    shouldBlock: false,
    confidence: 90,
    spamScore: cappedScore,
    otpCode: null,
    senderType: senderInfo.senderType,
    normalizedPreview: normalized,
    reasonSummary: 'Allowed: Personal conversational message.',
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

function detectOtp(normalized: string, senderType: SenderType): OtpDetection {
  const hasPromoCodeTrap = PROMO_CODE_EXCLUSION_REGEX.test(normalized);

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

  if (
    !hasPromoCodeTrap &&
    normalized.length <= 180 &&
    !STRONG_AD_BLOCKER_FOR_GENERIC_CODE_REGEX.test(normalized)
  ) {
    const shortCodeMatch =
      /(?:^|[\s:\n])(?:کد(?:\s*شما)?|رمز|code)\s*[:=-]?\s*(\d{4,8})(?!\d)/im.exec(normalized);
    if (shortCodeMatch) {
      return {
        isOtp: true,
        otpCode: shortCodeMatch[1],
        confidence: 94,
        signalCode: 'SHORT_STRUCTURAL_OTP',
        signalLabel: 'Concise login/verification code format',
      };
    }
  }

  // Ultra-minimal brand OTP from shortcode/alphanumeric sender (e.g. "اسنپ: 482910" or pure "482910")
  const strippedOptOut = normalized.replace(/\s*لغو\s*11\s*$/i, '').trim();
  if (
    !hasPromoCodeTrap &&
    strippedOptOut.length <= 60 &&
    !URL_IN_SMS_REGEX.test(strippedOptOut) &&
    !STRONG_AD_BLOCKER_FOR_GENERIC_CODE_REGEX.test(strippedOptOut) &&
    (senderType === 'IRAN_BULK_SHORTCODE' ||
      senderType === 'ALPHANUMERIC_BRAND' ||
      senderType === 'KNOWN_BANK_OR_GOV')
  ) {
    const minimalBrandOtp =
      /^(?:[\u0600-\u06FFA-Za-z0-9\s._-]{1,24}\s*[:=-]\s*)?(\d{4,8})$/.exec(strippedOptOut);
    if (minimalBrandOtp) {
      return {
        isOtp: true,
        otpCode: minimalBrandOtp[1],
        confidence: 92,
        signalCode: 'MINIMAL_BRAND_OTP',
        signalLabel: 'Minimal brand/shortcode verification PIN',
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
