# Changelog — ParsShield (`پارس‌شیلد`)

All notable architectural updates and releases for **ParsShield** are documented in this file.

---

## [v2.2.0] — Samsung One UI Zero-Notification Shield & BiDi Fix
### Added
- **Samsung One UI Silent + Instant Cancel Hybrid Engine (`SmsNotificationShieldService`)**:
  - Synchronous `< 0.8ms` in-memory classification (`ParsShieldApp` live RAM cache) that cancels promotional & phishing notifications before any disk or coroutine hop, plus automatic cleanup of orphaned `FLAG_GROUP_SUMMARY` headers.
  - Automatic `Ranking` & `NotificationChannel` silence detection: when the user sets Samsung Messages / Google Messages to **Silent (`بی‌صدا`)**, ads produce **0 sound, 0 pop-up, and 0 notification**, while allowed OTPs, bank alerts, and personal messages trigger ParsShield's loud heads-up notification preserving Samsung's original `contentIntent` to open the chat directly inside Samsung Messages.
- **3-Step Guided Samsung One UI Setup Wizard**:
  - Direct 1-tap shortcuts for Android 13+ `Allow restricted settings` (`App Info`), `Notification Access`, and Samsung Messages `Silent` notification configuration.
- **Expanded Iranian Local Spam Detection**:
  - Added rules for common Simbox/`09xx` local spam categories (carpet cleaning, moving companies, plumbing, thesis writing, unauthorized tours, bulk SMS panels) and 3–8 digit VAS shortcodes.

### Fixed
- **Unicode BiDi Sender Stripping**: Fixed classification of phone numbers wrapped in invisible Unicode directional marks (`\u200E`, `\u202A–\u202E`, `\u2066–\u2069`) by Samsung One UI and Google Messages.
- **SpannableString Notification Extraction**: Switched notification parsing to `NotificationCompat.MessagingStyle` + `Bundle.getCharSequence` so formatted titles/bodies on Samsung One UI never return `null`.

---

## [v2.1.0] — Native Dual-SIM (`SIM 1 / SIM 2`) & Contact Picker
### Added
- **Native Dual-SIM Support (`SimCardHelper` + `SubscriptionManager`)**:
  - Automatic detection of active SIM slots (`SIM 1` / `SIM 2`) and operator labels (`IR-MCI`, `Irancell`, `RighTel`).
  - 1-tap SIM selector chips inside both the Conversation Reply bar and the New SMS dialog (`SmsManager.createForSubscriptionId`).
  - Per-message `SIM 1` / `SIM 2` badges and automatic per-thread reply SIM memory.
- **Phonebook Contact Picker**: 1-tap contact phone number picker inside the New SMS composer.
- **Fallback `SMS_RECEIVED` Relay Receiver**.

---

## [v2.0.0] — Pre-Notification Radio Interception & Conversation Threads
### Added
- **Radio-Level `SMS_DELIVER` Interception (`priority="999"`)**: Zero notification creation for blocked ads when set as Default SMS App.
- **Zero-Tolerance Commercial Panel Filter**: Silences sneaky non-OTP / non-bank / non-service ads from `1000/2000/3000/5000/9000` and `021` landline panels even when words like `"تخفیف"` or `"لغو11"` are omitted.
- **Vaporize Mode (`حذف آنی در حافظه`)**: Optional setting to drop blocked ads directly in RAM without storing them in SQLite.
- **Full Conversation Threads & Chat Bubbles**: Grouped inbox threads, inline `RemoteInput` notification replies, and 1-tap `فارسی (RTL) / EN` UI toggle.

---

## [v1.0.0] — Initial Release
- 7-layer offline Iranian SMS classification engine (`OTP`, `BANK_ALERT`, `SERVICE_ALERT`, `PERSONAL`, `SPAM_AD`, `PHISHING`).
- Persian text normalization (Kashida stripping, Yeh/Kaf & Persian/Arabic digit unification).
- `ابلاغیه ثنا` & `.apk` phishing protection.
- Provable offline privacy (no `INTERNET` permission) + interactive React/Vite live simulator (`preview/`) + GitHub Actions signed APK pipeline.
