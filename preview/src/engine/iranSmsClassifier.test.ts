import { describe, expect, it } from 'vitest';
import {
  canonicalizeThreadAddress,
  classifyIranSms,
  normalizeSmsText,
} from './iranSmsClassifier';

describe('IranSmsClassifier — Normalization & Thread Canonicalization', () => {
  it('normalizes Persian/Arabic numerals, Arabic Yeh/Kaf, ZWNJ, and Kashida obfuscation', () => {
    const raw = 'تـخـفـيـف ويـژه ۵۰٪ كـاشـت مـو\u200Cها ۰۹۱۲۳۴۵۶۷۸۹';
    expect(normalizeSmsText(raw)).toBe('تخفیف ویژه 50٪ کاشت مو ها 09123456789');
  });

  it('merges +98912, 0912, and 0098912 into the exact same thread address', () => {
    expect(canonicalizeThreadAddress('09121234567')).toBe('+989121234567');
    expect(canonicalizeThreadAddress('+98 912 123 4567')).toBe('+989121234567');
    expect(canonicalizeThreadAddress('00989121234567')).toBe('+989121234567');
  });
});

describe('IranSmsClassifier — ZERO False Positives on OTPs (Persian, Finglish, English, Shaparak)', () => {
  it('protects Digikala Persian OTP with Persian numerals', () => {
    const res = classifyIranSms({
      sender: '9810004321',
      body: 'کد تایید شما در دیجی‌کالا: ۴۸۲۹۱۰\nاین کد را در اختیار دیگران قرار ندهید.',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('482910');
  });

  it('protects OTP even when bulk SMS gateway automatically appends لغو11 footer!', () => {
    const res = classifyIranSms({
      sender: '985000491234',
      body: 'فروشگاه آنلاین بانی‌مد\nکد ورود شما: ۷۳۹۱۵\nلغو11',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('73915');
  });

  it('protects Shaparak Dynamic Card PIN (رمز پویا) and extracts PIN instead of card or amount', () => {
    const res = classifyIranSms({
      sender: '9820004000',
      body: 'بانک ملت\nرمز پویا: ۸۴۹۲۰۱۵\nمبلغ: ۲,۵۰۰,۰۰۰ ریال\nکارت: ۵۱۰۴\nتاریخ: ۱۴۰۵/۰۹/۰۵',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('8492015');
  });

  it('protects BluBank / Saman dynamic second password (رمز دوم یکبار مصرف)', () => {
    const res = classifyIranSms({
      sender: 'BLU',
      body: 'رمز دوم یکبار مصرف خرید اینترنتی: 629401\nمبلغ: 850,000 ریال',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('629401');
  });

  it('protects Snapp short structural OTP with Android SMS Retriever <#> hash', () => {
    const res = classifyIranSms({
      sender: '9810005544',
      body: '<#> کد ورود اسنپ: ۱۹۴۸۲\nFA+9qCX9VSu',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('19482');
  });

  it('protects Divar concise "کد شما: 839201" OTP', () => {
    const res = classifyIranSms({
      sender: '9830008415',
      body: 'دیوار\nکد شما: ۸۳۹۲۰۱',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('839201');
  });

  it('protects Iranian Government Smart Window (پنجره ملی خدمات دولت هوشمند) OTP', () => {
    const res = classifyIranSms({
      sender: 'MYGOV',
      body: 'پنجره ملی خدمات دولت هوشمند\nکد احراز هویت: ۵۵۱۲۰۹',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('551209');
  });

  it('protects Nobitex / Crypto exchange 2FA Persian security code with تأیید spelling', () => {
    const res = classifyIranSms({
      sender: '9890001234',
      body: 'نوبیتکس\nکد تأیید دو عاملی: 309481\nدر صورت عدم درخواست، نادیده بگیرید.',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('309481');
  });

  it('protects Finglish OTP ("Kode taeed shoma: 49201")', () => {
    const res = classifyIranSms({
      sender: '98500012345',
      body: 'Kode taeed shoma: 49201 - Azki.com',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('49201');
  });

  it('protects Finglish Shaparak Ramze Pouya', () => {
    const res = classifyIranSms({
      sender: 'MELLAT',
      body: 'Bank Mellat\nRamze pouya: 908172\nMablagh: 1,200,000 Rial',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('908172');
  });

  it('protects Google G-XXXXXX verification SMS', () => {
    const res = classifyIranSms({
      sender: 'GOOGLE',
      body: 'G-748291 is your Google verification code.',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('748291');
  });

  it('protects Telegram login code SMS', () => {
    const res = classifyIranSms({
      sender: '+447781470659',
      body: 'Telegram code: 59301\nYou can also tap on this link to log in.\nDo not give this code to anyone.',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('59301');
  });

  it('protects WhatsApp code with WebOTP format', () => {
    const res = classifyIranSms({
      sender: 'WHATSAPP',
      body: '<#> Your WhatsApp code: 401928\nDon’t share this code with others\n4sgLq1p5sV6',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('401928');
  });

  it('protects multi-line OTP where numeric code is on the next line', () => {
    const res = classifyIranSms({
      sender: '98300077',
      body: 'همراه من\nرمز یکبار مصرف شما جهت ورود:\n۷۳۹۱۰۴\nمهلت استفاده ۲ دقیقه',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('739104');
  });

  it('protects ultra-minimal brand OTP without the word کد ("اسنپ: ۴۸۲۹۱۰")', () => {
    const res = classifyIranSms({
      sender: '9810005544',
      body: 'اسنپ: ۴۸۲۹۱۰\nلغو11',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('482910');
  });

  it('protects Digikala courier handoff code (کد تحویل سفارش) as OTP so it can be copied in 1 tap', () => {
    const res = classifyIranSms({
      sender: '9810004321',
      body: 'دیجی‌کالا\nکد تحویل سفارش شما به مامور ارسال: ۴۹۲۰۱',
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('49201');
  });

  it('protects OTP even if user previously blocked that shared 5000xxxx gateway number', () => {
    const res = classifyIranSms({
      sender: '50004444',
      body: 'کد تایید ورود به حساب کاربری: 991204',
      customRules: [{ id: '1', pattern: '50004444', target: 'SENDER', action: 'BLOCK' }],
      neverBlockOtpEvenFromBlocked: true,
    });
    expect(res.category).toBe('OTP');
    expect(res.shouldBlock).toBe(false);
    expect(res.otpCode).toBe('991204');
  });
});

describe('IranSmsClassifier — Promo Code Traps (Must NOT be mistaken for OTPs!)', () => {
  it('blocks discount code spam ("کد تخفیف: 5040") instead of treating it as OTP', () => {
    const res = classifyIranSms({
      sender: '9850002910',
      body: '۵۰ درصد تخفیف خرید اول اسنپ‌مارکت!\nکد تخفیف: 5040\nمهلت استفاده تا امشب\nلغو11',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
    expect(res.otpCode).toBeNull();
  });

  it('blocks lottery code spam ("کد قرعه‌کشی شما: 88219")', () => {
    const res = classifyIranSms({
      sender: '9820009911',
      body: 'مشترک گرامی کد قرعه‌کشی شما 88219 می‌باشد! جهت شانس برنده شدن جایزه میلیونی عدد ۱ را ارسال کنید. لغو11',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks English promo code ad ("Use promo code 2026 for 40% off!")', () => {
    const res = classifyIranSms({
      sender: '50008812',
      body: 'Flash Sale! Use promo code 2026 for 40% off all shoes. Buy now! Reply STOP to unsubscribe',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });
});

describe('IranSmsClassifier — Legitimate Iranian Bank & Service Alerts', () => {
  it('allows Bank Saman withdrawal & balance SMS', () => {
    const res = classifyIranSms({
      sender: 'SAMAN',
      body: 'بانک سامان\nبرداشت: ۱,۲۵۰,۰۰۰ ریال\nحساب: ۸۲۱-۸۰۰-۱۲۳۴۵۶۷-۱\nمانده: ۴۵,۸۰۰,۰۰۰ ریال\n۱۴۰۵/۰۹/۰۵ ۱۸:۴۲',
    });
    expect(res.category).toBe('BANK_ALERT');
    expect(res.shouldBlock).toBe(false);
  });

  it('allows compact Bank Sepah POS card purchase (خرید: ... مانده: ...)', () => {
    const res = classifyIranSms({
      sender: '98300094',
      body: 'بانک سپه\nکارت603769******1234\nخرید:450,000ریال\nمانده:18,200,000ریال\n1405/09/05',
    });
    expect(res.category).toBe('BANK_ALERT');
    expect(res.shouldBlock).toBe(false);
  });

  it('allows BluBank deposit notification from bulk service line', () => {
    const res = classifyIranSms({
      sender: '981000258',
      body: 'بلو، بانک ولی دوست‌داشتنی\nواریز به حساب: ۵,۰۰۰,۰۰۰ ریال\nموجودی: ۱۲,۴۰۰,۰۰۰ ریال',
    });
    expect(res.category).toBe('BANK_ALERT');
    expect(res.shouldBlock).toBe(false);
  });

  it('allows legitimate Post tracking notification', () => {
    const res = classifyIranSms({
      sender: 'POST',
      body: 'مرسوله پستی شما با کد رهگیری مرسوله ۲۹۸۴۱۰۰۹۲۳۴۵ تحویل مامور توزیع گردید.',
    });
    expect(res.category).toBe('SERVICE_ALERT');
    expect(res.shouldBlock).toBe(false);
  });

  it('allows legitimate ADLIRAN Sana notice (no links, official sender)', () => {
    const res = classifyIranSms({
      sender: 'ADLIRAN',
      body: 'ابلاغیه شماره ۱۴۰۵۹۱۲۳۴۵۶ در سامانه ثنا ثبت شد. جهت مشاهده به درگاه قوه قضاییه مراجعه فرمایید.',
    });
    expect(res.category).toBe('SERVICE_ALERT');
    expect(res.shouldBlock).toBe(false);
  });

  it('allows doctor appointment confirmation from clinic landline panel', () => {
    const res = classifyIranSms({
      sender: '02188776655',
      body: 'بیمار گرامی، نوبت شما در مطب دکتر احمدی برای تاریخ ۱۴۰۵/۰۹/۱۰ ساعت ۱۷:۳۰ ثبت شد.',
    });
    expect(res.category).toBe('SERVICE_ALERT');
    expect(res.shouldBlock).toBe(false);
  });

  it('allows Irancell 85% internet package usage warning (while blocking carrier promos)', () => {
    const res = classifyIranSms({
      sender: 'IRANCELL',
      body: 'مشترک گرامی، ۸۵ درصد از حجم بسته اینترنت شما مصرف شده است.',
    });
    expect(res.category).toBe('SERVICE_ALERT');
    expect(res.shouldBlock).toBe(false);
  });
});

describe('IranSmsClassifier — Blocks Real Iranian Spam, Ads & Phishing', () => {
  it('blocks beauty/laser/hair-transplant clinic spam from 021 landline panel', () => {
    const res = classifyIranSms({
      sender: '+982122446688',
      body: 'جشنواره پاییزه کلینیک زیبایی ونک! کاشت مو و ابرو + لیزر موهای زائد با ۴۰٪ تخفیف. مشاوره رایگان: 02122446688',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks dental implant spam from 5000xxxx shortcode', () => {
    const res = classifyIranSms({
      sender: '98500041122',
      body: 'ایمپلنت فوری دندان و لمینت دندان با اقساط بدون بهره! جهت مشاوره رایگان عدد ۳ را ارسال کنید.',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks Northern Villa / Real Estate spam sent from a personal 0912 SIM blaster', () => {
    const res = classifyIranSms({
      sender: '+989124445566',
      body: 'فروش فوری ویلا شمال استخردار با سند تک برگ! اقساط بلندمدت بدون بهره. تهاتر ملک و خودرو.',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks gambling / Enfejar / casino bonus spam disguising as "واریز هدیه"', () => {
    const res = classifyIranSms({
      sender: '+989351112233',
      body: 'واریز هدیه ثبت نام ۵۰۰ هزار تومانی + ۱۰۰٪ بونوس بازی انفجار در معتبرترین کازینو آنلاین! yun.ir/bet99',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks Kashida-obfuscated spam ("تـخـفـيـف ويـژه وام فـوری")', () => {
    const res = classifyIranSms({
      sender: '09908887766',
      body: 'وام فـوری بـدون ضـامـن روی سـیـم‌کـارت و طـلا در ۲۴ سـاعـت! جـهت مـشـاوره رایـگـان تـمـاس بـگـیـریـد.',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks Finglish promotional spam', () => {
    const res = classifyIranSms({
      sender: '50009182',
      body: 'Foroush vijeh Black Friday! Ta 60% takhfif rooye tamame mahsoolat. Kharid: b2n.ir/shop1 - laghv 11',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks MCI / Hamrah-e Aval promotional lottery blast', () => {
    const res = classifyIranSms({
      sender: 'HAMRAHAVAL',
      body: 'همراه اولی عزیز! در جشنواره بزرگ پاییزه برنده جایزه میلیونی شوید! ارسال عدد ۱ به ۹۹۹۰',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks soft business ad from 5000 panel with callback number', () => {
    const res = classifyIranSms({
      sender: '500028374',
      body: 'گالری مبل و دکوراسیون کلاسیک تهران\nزیبایی خانه خود را به ما بسپارید\nتماس و مشاوره: 02177665544',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks Fake Sana Judiciary Phishing from 09xx number with link!', () => {
    const res = classifyIranSms({
      sender: '+989198765432',
      body: 'ابلاغیه فوری سامانه ثنا قوه قضاییه: شکواییه جدید علیه شما ثبت شد. جهت مشاهده پرونده وارد لینک شوید: http://adl-sana-ir.xyz/case',
    });
    expect(res.category).toBe('PHISHING');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks Fake Subsidy / Kalabarg .apk malware SMS', () => {
    const res = classifyIranSms({
      sender: '09012345678',
      body: 'سرپرست محترم خانوار، کالابرگ و یارانه معیشتی شما فعال شد. نصب برنامه رسمی: https://yaraneh-gov.top/app.apk',
    });
    expect(res.category).toBe('PHISHING');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks Fake Bank Card Freeze phishing with external URL', () => {
    const res = classifyIranSms({
      sender: '09381112244',
      body: 'مشتری گرامی حساب بانکی شما مسدود گردید. جهت رفع مسدودی حساب فورا به آدرس زیر مراجعه کنید: https://shaparak-check.online',
    });
    expect(res.category).toBe('PHISHING');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks free trading webinar / course spam from bulk panel', () => {
    const res = classifyIranSms({
      sender: '500049911',
      body: 'وبینار رایگان نوسان‌گیری طلا و دلار با استاد کریمی! ظرفیت محدود، ثبت نام در: yun.ir/goldweb',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });

  it('blocks sneaky commercial ad from 5000/021 panel even when it avoids discount/لغو11 words', () => {
    const res = classifyIranSms({
      sender: '98500012345',
      body: 'گروه صنعتی پارس استیل، تولید کننده انواع ورق و پروفیل صنعتی در تهران. تلفن: ۸۸۷۷۶۶۵۵',
    });
    expect(res.category).toBe('SPAM_AD');
    expect(res.shouldBlock).toBe(true);
  });
});

describe('IranSmsClassifier — Personal Conversational Messages (Never Blocked)', () => {
  it('allows normal Persian conversation from unknown mobile number', () => {
    const res = classifyIranSms({
      sender: '+989121234567',
      body: 'سلام علی جان خوبی؟ امشب ساعت ۸ میای خونه ما؟ شام قرمه‌سبزی داریم.',
    });
    expect(res.category).toBe('PERSONAL');
    expect(res.shouldBlock).toBe(false);
  });

  it('allows Finglish conversation from friend', () => {
    const res = classifyIranSms({
      sender: '09359876543',
      body: 'Salam khoobi? Man residam dame dar, bia payin.',
    });
    expect(res.category).toBe('PERSONAL');
    expect(res.shouldBlock).toBe(false);
  });

  it('allows English personal message', () => {
    const res = classifyIranSms({
      sender: '+989123334444',
      body: 'Hey, are we still meeting at the cafe at 5pm?',
    });
    expect(res.category).toBe('PERSONAL');
    expect(res.shouldBlock).toBe(false);
  });

  it('allows any message from a saved contact even if they mention shopping/discounts', () => {
    const res = classifyIranSms({
      sender: '+989121112222',
      body: 'ببین دیجی‌کالا ۵۰ درصد تخفیف گذاشته، لینکش رو برات بفرستم؟',
      isSavedContact: true,
    });
    expect(res.category).toBe('PERSONAL');
    expect(res.shouldBlock).toBe(false);
  });
});
