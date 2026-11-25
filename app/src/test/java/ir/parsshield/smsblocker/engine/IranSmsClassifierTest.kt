package ir.parsshield.smsblocker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IranSmsClassifierTest {

    @Test
    fun `normalizes Persian digits Arabic letters and Kashida obfuscation`() {
        val raw = "تـخـفـيـف ويـژه ۵۰٪ كـاشـت مـو\u200Cها ۰۹۱۲۳۴۵۶۷۸۹"
        assertEquals(
            "تخفیف ویژه 50٪ کاشت مو ها 09123456789",
            TextNormalizer.normalizeSmsText(raw)
        )
    }

    @Test
    fun `protects Digikala OTP and extracts code`() {
        val res = IranSmsClassifier.classify(
            sender = "9810004321",
            body = "کد تایید شما در دیجی‌کالا: ۴۸۲۹۱۰\nاین کد را در اختیار دیگران قرار ندهید."
        )
        assertEquals(SmsCategory.OTP, res.category)
        assertFalse(res.shouldBlock)
        assertEquals("482910", res.otpCode)
    }

    @Test
    fun `protects OTP even when bulk SMS gateway appends laghv11`() {
        val res = IranSmsClassifier.classify(
            sender = "985000491234",
            body = "فروشگاه آنلاین بانی‌مد\nکد ورود شما: ۷۳۹۱۵\nلغو11"
        )
        assertEquals(SmsCategory.OTP, res.category)
        assertFalse(res.shouldBlock)
        assertEquals("73915", res.otpCode)
    }

    @Test
    fun `protects Shaparak Dynamic PIN Ramz Pouya`() {
        val res = IranSmsClassifier.classify(
            sender = "9820004000",
            body = "بانک ملت\nرمز پویا: ۸۴۹۲۰۱۵\nمبلغ: ۲,۵۰۰,۰۰۰ ریال\nکارت: ۵۱۰۴\nتاریخ: ۱۴۰۵/۰۹/۰۵"
        )
        assertEquals(SmsCategory.OTP, res.category)
        assertFalse(res.shouldBlock)
        assertEquals("8492015", res.otpCode)
    }

    @Test
    fun `protects multi-line OTP where code is on next line`() {
        val res = IranSmsClassifier.classify(
            sender = "98300077",
            body = "همراه من\nرمز یکبار مصرف شما جهت ورود:\n۷۳۹۱۰۴\nمهلت استفاده ۲ دقیقه"
        )
        assertEquals(SmsCategory.OTP, res.category)
        assertFalse(res.shouldBlock)
        assertEquals("739104", res.otpCode)
    }

    @Test
    fun `protects Finglish and English OTPs`() {
        val finglish = IranSmsClassifier.classify(
            sender = "98500012345",
            body = "Kode taeed shoma: 49201 - Azki.com"
        )
        assertEquals(SmsCategory.OTP, finglish.category)
        assertFalse(finglish.shouldBlock)
        assertEquals("49201", finglish.otpCode)

        val google = IranSmsClassifier.classify(
            sender = "GOOGLE",
            body = "G-748291 is your Google verification code."
        )
        assertEquals(SmsCategory.OTP, google.category)
        assertFalse(google.shouldBlock)
        assertEquals("748291", google.otpCode)
    }

    @Test
    fun `blocks promo discount code instead of mistaking for OTP`() {
        val res = IranSmsClassifier.classify(
            sender = "9850002910",
            body = "۵۰ درصد تخفیف خرید اول اسنپ‌مارکت!\nکد تخفیف: 5040\nمهلت استفاده تا امشب\nلغو11"
        )
        assertEquals(SmsCategory.SPAM_AD, res.category)
        assertTrue(res.shouldBlock)
        assertNull(res.otpCode)
    }

    @Test
    fun `allows Iranian bank alerts and POS transactions`() {
        val saman = IranSmsClassifier.classify(
            sender = "SAMAN",
            body = "بانک سامان\nبرداشت: ۱,۲۵۰,۰۰۰ ریال\nحساب: ۸۲۱-۸۰۰-۱۲۳۴۵۶۷-۱\nمانده: ۴۵,۸۰۰,۰۰۰ ریال"
        )
        assertEquals(SmsCategory.BANK_ALERT, saman.category)
        assertFalse(saman.shouldBlock)

        val sepah = IranSmsClassifier.classify(
            sender = "98300094",
            body = "بانک سپه\nکارت603769******1234\nخرید:450,000ریال\nمانده:18,200,000ریال"
        )
        assertEquals(SmsCategory.BANK_ALERT, sepah.category)
        assertFalse(sepah.shouldBlock)
    }

    @Test
    fun `blocks beauty clinic casino villa and Kashida spam`() {
        val clinic = IranSmsClassifier.classify(
            sender = "+982122446688",
            body = "جشنواره پاییزه کلینیک زیبایی ونک! کاشت مو و لیزر موهای زائد با ۴۰٪ تخفیف."
        )
        assertEquals(SmsCategory.SPAM_AD, clinic.category)
        assertTrue(clinic.shouldBlock)

        val casino = IranSmsClassifier.classify(
            sender = "+989351112233",
            body = "واریز هدیه ثبت نام ۵۰۰ هزار تومانی + ۱۰۰٪ بونوس بازی انفجار در کازینو آنلاین! yun.ir/bet99"
        )
        assertEquals(SmsCategory.SPAM_AD, casino.category)
        assertTrue(casino.shouldBlock)
    }

    @Test
    fun `blocks fake Sana judicial phishing from mobile SIM while allowing official ADLIRAN`() {
        val fakeSana = IranSmsClassifier.classify(
            sender = "+989198765432",
            body = "ابلاغیه فوری سامانه ثنا قوه قضاییه: شکواییه جدید ثبت شد: http://adl-sana-ir.xyz/case"
        )
        assertEquals(SmsCategory.PHISHING, fakeSana.category)
        assertTrue(fakeSana.shouldBlock)

        val realAdliran = IranSmsClassifier.classify(
            sender = "ADLIRAN",
            body = "ابلاغیه شماره ۱۴۰۵۹۱۲۳۴۵۶ در سامانه ثنا ثبت شد. جهت مشاهده به درگاه قوه قضاییه مراجعه فرمایید."
        )
        assertEquals(SmsCategory.SERVICE_ALERT, realAdliran.category)
        assertFalse(realAdliran.shouldBlock)
    }

    @Test
    fun `allows normal personal Persian and English messages`() {
        val persian = IranSmsClassifier.classify(
            sender = "+989121234567",
            body = "سلام علی جان خوبی؟ امشب ساعت ۸ میای خونه ما؟"
        )
        assertEquals(SmsCategory.PERSONAL, persian.category)
        assertFalse(persian.shouldBlock)
    }
}
