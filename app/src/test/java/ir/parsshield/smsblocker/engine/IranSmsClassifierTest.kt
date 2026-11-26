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
    fun `canonicalizes Iranian mobile thread addresses identically`() {
        assertEquals("+989121234567", SenderAnalyzer.canonicalizeThreadAddress("09121234567"))
        assertEquals("+989121234567", SenderAnalyzer.canonicalizeThreadAddress("+98 912 123 4567"))
        assertEquals("+989121234567", SenderAnalyzer.canonicalizeThreadAddress("00989121234567"))
    }

    @Test
    fun `protects Digikala OTP and minimal brand OTPs even with laghv11`() {
        val digikala = IranSmsClassifier.classify(
            sender = "9810004321",
            body = "کد تایید شما در دیجی‌کالا: ۴۸۲۹۱۰\nاین کد را در اختیار دیگران قرار ندهید."
        )
        assertEquals(SmsCategory.OTP, digikala.category)
        assertFalse(digikala.shouldBlock)
        assertEquals("482910", digikala.otpCode)

        val minimal = IranSmsClassifier.classify(
            sender = "9810005544",
            body = "اسنپ: ۴۸۲۹۱۰\nلغو11"
        )
        assertEquals(SmsCategory.OTP, minimal.category)
        assertFalse(minimal.shouldBlock)
        assertEquals("482910", minimal.otpCode)
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
    }

    @Test
    fun `blocks sneaky commercial ad from 5000 panel even without discount or laghv11 words`() {
        val sneaky = IranSmsClassifier.classify(
            sender = "98500012345",
            body = "گروه صنعتی پارس استیل، تولید کننده انواع ورق و پروفیل صنعتی در تهران. تلفن: ۸۸۷۷۶۶۵۵"
        )
        assertEquals(SmsCategory.SPAM_AD, sneaky.category)
        assertTrue(sneaky.shouldBlock)
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
