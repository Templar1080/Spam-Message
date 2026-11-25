# ParsShield — Iran SMS Spam, Ad & Phishing Blocker for Android

**ParsShield** is a 100% offline native Android app (**Kotlin + Jetpack Compose, Material 3**) purpose-built for the Iranian SMS ecosystem. It blocks annoying promotional blasts, beauty/dental clinic ads, northern villa spam, betting/casino messages, and fake judicial (`ابلاغیه ثنا`) phishing links **without ever blocking your OTP verification codes, Shaparak Dynamic PINs (`رمز پویا`), or bank transaction alerts**.

---

## Why Generic SMS Blockers Fail in Iran

1. **Shared Bulk SMS Gateways (`1000` / `2000` / `3000` / `5000` / `9000`)**:
   In Iran, Digikala, Snapp, Divar, Torob, Nobitex, government portals (`پنجره ملی خدمات دولت هوشمند`), and banks send login OTPs over the exact same gateway prefixes used by bulk advertisers. Even worse, many Iranian SMS panels automatically append **`لغو11`** to the bottom of legitimate OTP messages. Blocking by prefix or `لغو11` alone breaks your logins!
2. **Promo Codes Disguised as Codes**:
   Spammers send `"کد تخفیف: 5040"` (discount code) or `"کد قرعه‌کشی: 88219"` (lottery code). ParsShield distinguishes promotional codes from genuine login/verification OTPs and `رمز پویا`.
3. **Persian Script Obfuscation**:
   Spammers stretch words with Kashida (`تـخـفـیـف ویـژه`), mix Arabic `ك`/`ي` with Persian `ک`/`ی`, use Persian/Arabic numerals (`۰-۹`), or write in **Finglish** (`Takhfif vijeh... laghv 11`). ParsShield normalizes all character variants in `< 2ms` before classifying.
4. **Fake Judiciary (`ثنا`) & Subsidy Malware Scams**:
   Blocks `09xx` SIM-blaster phishing messages impersonating `سامانه ثنا`, `کالابرگ / یارانه`, or bank card-freeze alerts containing `.apk` or shady links—while allowing official `ADLIRAN` notices.

---

## Privacy Guarantee: Zero Internet Permission

Check **Android Settings → Apps → ParsShield → Permissions**:
`android.permission.INTERNET` is **intentionally omitted** from `AndroidManifest.xml`. The Android OS kernel guarantees your OTP codes, Shaparak `رمز پویا`, bank balances, and personal messages can **never** leave your phone.

---

## How to Install the APK on Your Android Phone (Android 8.0 – 15)

### Method 1: Download Ready-to-Install Signed APK via GitHub Actions (No PC Needed)
1. Push or merge this branch on GitHub.
2. Open the **Actions** tab in your GitHub repository → select **Build ParsShield Android APK**.
3. Download the **`ParsShield-Android-APK`** artifact (`ParsShield-v1.0.0-signed.apk`) and open it on your Android phone to install.

### Method 2: Build Locally in Android Studio or CLI
```bash
# Requires JDK 17 + Android SDK 35
gradle testDebugUnitTest
gradle assembleRelease
# Signed APK output: app/build/outputs/apk/release/app-release.apk
```

---

## Two Ways to Block Spam on Android

Inside ParsShield's **Shield** tab, you can choose whichever mode fits your workflow:

1. **Option A — Companion Notification Shield (Keep Google / Samsung / Xiaomi Messages!)**
   - Grants Android *Notification Access* (`SmsNotificationShieldService`).
   - Automatically dismisses incoming spam/ad SMS notifications in real time and logs them in ParsShield's **Blocked** vault, while leaving OTPs, bank receipts, and personal chats untouched in your favorite SMS app.
   - *Android 13+ Sideload Tip*: If Notification Access says **"Restricted setting"** after installing the APK, open **Settings → Apps → ParsShield → ⋮ (top right) → Allow restricted settings**, then enable Notification Access.

2. **Option B — Full Default SMS Blocker (`RoleManager.ROLE_SMS`)**
   - Sets ParsShield as your Default SMS handler (`SmsDeliverReceiver`).
   - Quarantines spam at the OS level with **zero ring, zero vibration, and zero lock-screen wake**.
   - Delivers high-priority OTP notifications with a **1-Tap `Copy <code>` button** right in the Android notification shade.
   - Writes allowed messages through to Android's system SMS provider so switching back to another SMS app later never loses a single OTP, bank receipt, or personal text.

---

## Interactive Web Simulator & Classifier Workbench (`preview/`)

Want to test how ParsShield classifies your own Persian, Finglish, or English SMS messages in a browser?

```bash
cd preview
npm install
npm test        # Runs 43 real-world Iranian SMS unit tests
npm run dev     # Starts interactive Android simulator & SMS analyzer
```

---

## Project Structure

- `app/` — Native Android application (Kotlin, Jetpack Compose Material 3, Room SQLite, DataStore)
  - `engine/` — Pure-Kotlin offline Iranian SMS normalizer, sender analyzer & multi-layer classifier
  - `service/` — `SmsNotificationShieldService`, `SmsDeliverReceiver`, `NotificationHelper`, `CopyOtpReceiver`
  - `data/` — Room database, custom whitelist/blacklist rules, existing inbox scanner
  - `ui/` — English Material 3 interface (`Inbox`, `OTPs & Bank`, `Blocked`, `Rules`, `Shield`, `On-Device Tester`) with automatic RTL/LTR rendering for Persian & English SMS
- `.github/workflows/android-apk.yml` — Automated JUnit test runner & signed Android APK builder
- `preview/` — Interactive React + TypeScript live simulator mirroring the exact classification engine
