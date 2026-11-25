export interface SeedSmsMessage {
  id: string;
  sender: string;
  displayName?: string;
  body: string;
  minutesAgo: number;
  isSavedContact?: boolean;
}

export const INITIAL_IRANIAN_MESSAGES: SeedSmsMessage[] = [
  {
    id: 'msg-1',
    sender: '9820004000',
    body: 'بانک ملت\nرمز پویا: ۸۴۹۲۰۱۵\nمبلغ: ۲,۵۰۰,۰۰۰ ریال\nکارت: ۵۱۰۴\nفروشگاه اینترنتی دیجی‌کالا',
    minutesAgo: 2,
  },
  {
    id: 'msg-2',
    sender: '985000491234',
    body: 'دیجی‌کالا\nکد تایید ورود شما: ۴۸۲۹۱۰\nاین کد محرمانه است.\nلغو11',
    minutesAgo: 7,
  },
  {
    id: 'msg-3',
    sender: '+982122446688',
    body: 'جشنواره پاییزه کلینیک زیبایی ونک! کاشت مو و ابرو + لیزر موهای زائد با ۴۰٪ تخفیف ویژه. مشاوره رایگان: 02122446688',
    minutesAgo: 14,
  },
  {
    id: 'msg-4',
    sender: 'SAMAN',
    body: 'بانک سامان\nبرداشت: ۱,۲۵۰,۰۰۰ ریال\nحساب: ۸۲۱-۸۰۰-۱۲۳۴۵۶۷-۱\nمانده: ۴۵,۸۰۰,۰۰۰ ریال\n۱۴۰۵/۰۹/۰۵ ۱۸:۴۲',
    minutesAgo: 23,
  },
  {
    id: 'msg-5',
    sender: '+989198765432',
    body: 'ابلاغیه فوری سامانه ثنا قوه قضاییه: شکواییه جدید علیه شما ثبت شد. جهت مشاهده پرونده فورا وارد لینک شوید: http://adl-sana-ir.xyz/case',
    minutesAgo: 31,
  },
  {
    id: 'msg-6',
    sender: '+989121234567',
    displayName: 'Reza (Saved Contact)',
    body: 'سلام خوبی؟ امشب ساعت ۸ میای کافه؟',
    minutesAgo: 45,
    isSavedContact: true,
  },
  {
    id: 'msg-7',
    sender: '9850002910',
    body: '۵۰ درصد تخفیف خرید اول اسنپ‌مارکت!\nکد تخفیف: 5040\nمهلت استفاده فقط تا امشب\nلغو11',
    minutesAgo: 58,
  },
  {
    id: 'msg-8',
    sender: 'POST',
    body: 'مرسوله پستی شما با کد رهگیری مرسوله ۲۹۸۴۱۰۰۹۲۳۴۵ تحویل مامور توزیع گردید.',
    minutesAgo: 95,
  },
  {
    id: 'msg-9',
    sender: '+989351112233',
    body: 'واریز هدیه ثبت نام ۵۰۰ هزار تومانی + ۱۰۰٪ بونوس بازی انفجار در معتبرترین کازینو آنلاین! yun.ir/bet99',
    minutesAgo: 120,
  },
  {
    id: 'msg-10',
    sender: 'GOOGLE',
    body: 'G-748291 is your Google verification code.',
    minutesAgo: 150,
  },
  {
    id: 'msg-11',
    sender: '+989124445566',
    body: 'فروش فوری ویلا شمال استخردار با سند تک برگ! اقساط بلندمدت بدون بهره. تهاتر ملک و خودرو.',
    minutesAgo: 210,
  },
  {
    id: 'msg-12',
    sender: 'ADLIRAN',
    body: 'ابلاغیه شماره ۱۴۰۵۹۱۲۳۴۵۶ در سامانه ثنا ثبت شد. جهت مشاهده به درگاه قوه قضاییه مراجعه فرمایید.',
    minutesAgo: 300,
  },
];

export interface SimulationScenario {
  label: string;
  badge: 'OTP' | 'BANK' | 'SPAM' | 'PHISHING';
  sender: string;
  body: string;
}

export const SIMULATION_SCENARIOS: SimulationScenario[] = [
  {
    label: 'Snapp OTP (with لغو11)',
    badge: 'OTP',
    sender: '9810005544',
    body: '<#> کد ورود اسنپ: ۱۹۴۸۲\nاین کد را در اختیار دیگران قرار ندهید.\nلغو11',
  },
  {
    label: 'Shaparak رمز پویا',
    badge: 'OTP',
    sender: 'BLU',
    body: 'بلوبانک\nرمز دوم یکبار مصرف: ۶۲۹۴۰۱\nمبلغ: ۸۵۰,۰۰۰ ریال',
  },
  {
    label: 'Finglish OTP',
    badge: 'OTP',
    sender: '98500012345',
    body: 'Kode taeed shoma: 49201 - Azki.com',
  },
  {
    label: 'BluBank Deposit',
    badge: 'BANK',
    sender: '981000258',
    body: 'بلو، بانک ولی دوست‌داشتنی\nواریز به حساب: ۵,۰۰۰,۰۰۰ ریال\nموجودی: ۱۲,۴۰۰,۰۰۰ ریال',
  },
  {
    label: 'Dental Implant Ad',
    badge: 'SPAM',
    sender: '98500041122',
    body: 'ایمپلنت فوری دندان و لمینت دندان با اقساط بدون بهره! جهت مشاوره رایگان عدد ۳ را ارسال کنید.',
  },
  {
    label: 'Kashida Obfuscated Loan Ad',
    badge: 'SPAM',
    sender: '09908887766',
    body: 'وام فـوری بـدون ضـامـن روی سـیـم‌کـارت و طـلا در ۲۴ سـاعـت! مـشـاوره رایـگـان',
  },
  {
    label: 'Fake ثنا Malware Link',
    badge: 'PHISHING',
    sender: '09019998877',
    body: 'ابلاغیه قضایی سامانه ثنا: حکم جلب در پرونده شما صادر شد. مشاهده ابلاغیه: https://sana-edalat.top/eblagh.apk',
  },
];
