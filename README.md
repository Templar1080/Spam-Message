# ParsShield — Zero-Notification Iran SMS Spam & Ad Blocker for Android

**ParsShield** (`پارس‌شیلد`) is a 100% offline native Android SMS app (**Kotlin + Jetpack Compose, Material 3**) engineered for Iran. By operating as Android's **Pre-Notification `SMS_DELIVER` Radio Interceptor (`RoleManager.ROLE_SMS`)**, it blocks promotional blasts, beauty/dental clinic ads, northern villa spam, betting/casino messages, and fake judicial (`ابلاغیه ثنا`) phishing links **in `< 2ms` before Android ever creates a notification, sound, vibration, or lock-screen wake**—while **never** blocking OTP verification codes or Shaparak Dynamic PINs (`رمز پویا`).

---

## How Zero-Notification Instant Blocking Works

Unlike notification-listener apps (which let your phone ring and flash a banner for a split second before swiping it away), ParsShield intercepts SMS directly at Android's **Telephony radio layer (`android.provider.Telephony.SMS_DELIVER`)**:

1. **Exclusive Pre-Notification Interception**:
   When ParsShield is set as your **Default SMS App**, Google Messages / Samsung Messages / Xiaomi Messaging never receive `SMS_DELIVER` and never ring.
2. **Sub-2ms On-Device Classification**:
   - **Advertisement / Spam / Phishing**: `NotificationManager` is **never called**—zero notification, zero chime, zero vibration, zero screen wake, and zero clutter in Android's system SMS inbox. Enable **Vaporize Mode (`حذف آنی در حافظه`)** to drop spam in RAM without even storing it in the Blocked vault!
   - **OTP / `رمز پویا` / Bank / Personal SMS**: Delivered immediately with auto-clipboard OTP copy, **1-Tap `Copy <OTP>`** in the notification shade, full **Conversation Threads**, chat bubble history, and **Inline Direct Reply (`RemoteInput`)**.
3. **Zero-Tolerance Commercial Panel Filter (`5000` / `2000` / `3000` / `1000` / `9000` / `021`)**:
   Blocks **all** messages from bulk commercial panels and landline panels *unless* they match a verified OTP, Bank/Shaparak transaction, essential service update, or your whitelist—catching even sneaky ads that avoid words like `"تخفیف"` or `"لغو11"`.

---

## Privacy Guarantee: Zero Internet Permission

`android.permission.INTERNET` is **intentionally omitted** from `AndroidManifest.xml`. Android's OS sandbox guarantees your OTP codes, Shaparak `رمز پویا`, bank balances, and personal chats can **never** leave your phone.

---

## Install on Your Android Phone (Android 8.0 – 15)

1. Open the **Actions** tab of this GitHub repository → select the latest **Build ParsShield Android APK** run → download **`ParsShield-Android-APK`** (`ParsShield-v1.0.0-signed.apk`).
2. Install the APK, open **ParsShield**, and tap **فعال‌سازی بلاک آنی (Set as Default SMS App)**.
3. Switch between **فارسی (RTL)** and **English** anytime in 1 tap from the top bar.
