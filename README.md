# স্মার্ট মানি ম্যানেজার ও বাজেট ট্র্যাকার (Smart Money Manager)
### Complete Android Application with Local-First Architecture & Firebase Cloud Sync

An installable Android app converted from the original Bengali budget management and expense tracking HTML application. It preserves 100% of the original UI/UX, typography, Bengali language strings, glassmorphism design, animations, charts, and calculations while adding production-ready Android features, native hardware integration, Firebase Authentication, and user-scoped Cloud Firestore backup/restore.

---

## 1. Features & Architectural Highlights

### Strict UI & UX Preservation
- **100% Visual Fidelity:** Original dark background (`#0b0f19`), ambient glowing orbs, glassmorphic cards, spring-physics press effects, and Bengali typography (Hind Siliguri & Plus Jakarta Sans).
- **Home & Budget Dashboard:** Monthly net savings, savings rate, total income and expense breakdown, 3-segment allocation bar, 80%/100% budget usage warning indicator, editable category sliders with instant recalculations.
- **Ledger Tab:** Income and expense transactions grouped by day, category filters, edit/delete actions, and automatic budget deduction.
- **Charts & 50/30/20 Analysis:** Interactive Chart.js doughnut chart, 6-month comparative bar-and-line chart, horizontal budget vs. actual chart, and 50/30/20 rule analyzer.
- **Monthly PDF Report:** Generates an A4 financial report with high-resolution canvas capture, saved directly to device storage and immediately opened via native Android PDF viewer (`Intent.ACTION_VIEW` via `FileProvider`).
- **Profile & Settings:** Custom user name, Web Audio + Native Android device vibration alert for budget threshold crossing, JSON backup export & import, data wipe dialog, and online/offline status indicators.

---

## 2. Android Architecture & Local-First Offline Support

- **Runtime:** Kotlin + Jetpack Compose + `androidx.webkit.WebViewAssetLoader`
- **Offline Bundling:** All required frontend libraries (Chart.js, html2pdf.js, Tailwind, Font Awesome 6.5.2, Google Fonts woff2 files, and Firebase Modular JS SDK) are locally bundled in `app/src/main/assets/web/vendor/`. The app launches and functions **100% offline without internet connection**.
- **Secure Virtual Origin:** Served from `https://appassets.androidplatform.net/assets/web/index.html` via `WebViewAssetLoader`, ensuring secure context (`isSecureContext === true`), standard ES module imports, persistent `localStorage`, and zero CORS restrictions.
- **Android Back Navigation:** Intercepts system back gestures to gracefully dismiss open modals (`#m-alarm`, `#m-confirm`, `#m-tx`, `#m-cat`, `#m-fbcfg`) or return to the Home tab before exiting the app.
- **Native AndroidBridge:**
  - `saveAndOpenPdf(base64Data, fileName)`: Decodes and writes PDF reports to app external storage, then triggers `FileProvider` view intent to open in the system PDF viewer.
  - `saveAndShareJson(jsonContent, fileName)`: Exports and triggers Android share sheet for JSON backup files.
  - `vibrate(durationMs)`: Drives device `Vibrator` / `VibratorManager` for realistic tactile feedback and audio sirens.
  - `signInWithGoogle()`: Seamless native Google Sign-In via Android Jetpack `CredentialManager`.

---

## 3. Firebase Configuration & Cloud Backup

### Pre-configured Environment
The app is provisioned with Firebase Authentication and Cloud Firestore:
- **Project ID:** `gen-lang-client-0633431606`
- **Storage Bucket:** `gen-lang-client-0633431606.firebasestorage.app`
- **Web OAuth Client ID:** `705715516333-f1bupdl4tec22lusrf2qruobsng50vbg.apps.googleusercontent.com`
- **Package Name:** `com.aistudio.moneymanager.bqzmtw`

### Firestore Security Rules (`firestore.rules`)
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // User-scoped data isolation: authenticated users can only access their own backups
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

### Authentication Workflows
1. **Email / Password Registration & Login:** Standard Firebase Auth with Bengali error messaging.
2. **Password Reset:** "পাসওয়ার্ড ভুলে গেছেন?" link triggers `sendPasswordResetEmail`.
3. **Google Sign-In:** Native Jetpack `CredentialManager` flow exchanging Google ID tokens with Firebase Auth (`signInWithCredential`).
4. **Guest Mode:** App functions completely offline with local storage without requiring account sign-in.
5. **Manual Cloud Backup:** "ক্লাউডে ব্যাকআপ নিন" serializes the entire multi-month dataset, categories, and settings to Firestore path `users/{uid}/financeData/masterBackup`.
6. **Cloud Restore:** "ক্লাউড থেকে রিস্টোর করুন" validates the cloud snapshot, asks for explicit user confirmation, and atomically restores local application state.

---

## 4. Building the Android Application

To assemble the Debug APK:
```bash
gradle :app:assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

To run local unit & Robolectric tests:
```bash
gradle :app:testDebugUnitTest
```
