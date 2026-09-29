# Salon Manager – Offline Salon & Barber Shop Management (Android)

Salon Manager is a native Android app for running a salon or barber shop from one phone or tablet:
sales counter with receipts and Bluetooth printing, customers and visit history, services and
prices, staff commission and payments, business **and** household expenses (kept separate),
cash counter reconciliation, targets, budgets, reports and offline business insights.

It is **offline-first**: every feature works without internet and the app does not even request
the `INTERNET` permission. All data lives in a local Room (SQLite) database on the device and can
be backed up to a file (optionally password-encrypted).

The architecture is prepared for commercial distribution to many salons (offline signed licence
keys, trial mode, a `businessId` on every major table, pluggable AI insights).

---

## Features

| Area | What it does |
|------|--------------|
| **Dashboard** | Today's sales, expenses, profit, cash received, customers, services, outstanding staff pay, daily/monthly target progress, 7-day chart, insights preview, recent sales, backup and licence reminders. Updates live. |
| **POS / New Sale** | Pick customer (or walk-in, or quick-add), staff, services from a grid (price loads automatically), change quantity/price, per-line and whole-sale discount (amount or %), payment method (Cash, Card, Bank, Other), cash received & change, note. Two-pane layout on tablets. |
| **Receipts** | Numbered `SAL-000001` (configurable prefix, never duplicated). On-screen preview identical to the 58 mm printout, print / reprint, share as PDF, void with reason (kept in history, excluded from totals, refunded in the cash counter). |
| **Bluetooth printer** | Generic ESC/POS 58 mm / 80 mm printers: permission handling (Android 12+ and older), enable Bluetooth, paired list, scan & pair, save printer, test print, auto-print, copies, paper cut, logo. *Text mode* (fast) and *Image mode* (renders with Android fonts, so Urdu names print on any printer). Never crashes on printer errors – every failure becomes a clear message with Retry. |
| **Customers** | Name, phone (duplicate-protected), gender, date of birth, address, notes. Profile shows total visits, total spent, last visit, favourite services and full visit history; open any old receipt. Instant search by name or phone. |
| **Services** | Name, category, price, duration, active/inactive; add/edit/delete; one-tap "add common salon services" with suggested (editable) prices. |
| **Staff** | Name, phone, role (Barber, Hairdresser, Beautician, Receptionist, Other), salary type (fixed, commission, fixed + commission), commission %, fixed salary, active flag. Monthly performance: sales, customers, services, commission. |
| **Staff payments** | Salary, advance, commission, bonus, other – with date, method, note and optional "paid from cash counter". Monthly settlement shows earned, paid, **outstanding** or paid-in-advance. |
| **Expenses** | Business (electricity, gas, water, rent, internet, supplies, cosmetics, equipment, maintenance, marketing, staff expenses, other) and Personal/Household (food, children, education, medical, home, transport, shopping, other) plus custom categories. Strictly separated. |
| **Profit** | `Business profit = Sales − Business expenses − Staff payments`. Household spending is shown separately, with an optional combined cash-flow view (`Remaining cash = Business profit − Household spending`). The formula is shown on screen. |
| **Cash counter** | Opening cash (suggested from yesterday's count) + cash sales − refunds − cash expenses − staff cash payments ± manual cash in/out = expected cash. Enter the counted cash to see Balanced / Short / Excess. Day close, reopen, history. |
| **Reports** | Today, yesterday, this/last week, this/last month, this year or any custom date range: sales, discounts, average sale, customers served, new customers, payment methods, trend chart, top services, staff performance, expenses by category, profit and cash flow. Export as **PDF** (share) or **CSV** (zip, Excel-ready). |
| **Targets** | Daily, weekly, monthly: target, achieved, remaining, %, progress bars and "you need Rs. X per day for the remaining N days". |
| **Smart budget** | Monthly business expense budget, household, children, food and savings target vs. actual. |
| **Insights** | Offline rule engine: target pace, sales and expense trends vs. last month, best service, top staff, busiest/slowest weekday, returning customers, high household spending, loss warnings. Designed so an AI engine can be plugged in later (see below). |
| **Backup & restore** | Save / share backup files (`SalonBackup_YYYY-MM-DD.salonbak`), optional AES-256 password, automatic daily backup on the phone (last 10 kept), restore with warning, integrity check and automatic safety backup + rollback. CSV export of all data. |
| **Security** | Optional PIN or password (salted PBKDF2 hash, never plain text), fingerprint/face unlock, escalating lockout after wrong attempts, one-time recovery code, per-area protection (app start, reports, expenses, settings/backup/licence), auto re-lock after 2 minutes in background. |
| **Licence** | Offline ECDSA-signed licence keys bound to an installation ID, plans (1/3/6 months, 1 year, custom, lifetime), trial mode, expiry warnings, read-only mode after expiry (data, reports and backups stay available), clock-rollback detection. Off by default for the owner's own build. |
| **Settings** | Business profile (name, logo, phone, address, currency – default PKR/Rs.), receipt settings, printer, services, staff, expense categories, targets, backup & restore, security, licence, about, erase all data (with safety backup). |

UI: Jetpack Compose + Material 3 with a navy & gold salon theme, light/dark mode, adaptive
layouts for phones and tablets, empty/loading/error states, confirmation dialogs, adaptive
launcher icon (with Android 13 themed icon) and branded splash screen.

Every user-facing text is an Android string resource with positional arguments, so an Urdu
translation only needs `res/values-ur/strings*.xml` (layouts already support RTL).

---

## Technology

* Kotlin 2.0, Jetpack Compose (BOM 2024.12), Material 3, Navigation Compose
* Room 2.6 (KSP) with foreign keys, indexes, exported schemas and migration policy
* MVVM: ViewModels + `StateFlow`, Kotlin Coroutines/Flow, manual dependency injection (`AppContainer`)
* AndroidX Biometric, Core SplashScreen, Activity Result APIs (Storage Access Framework, Photo Picker)
* Android Bluetooth Classic (RFCOMM/SPP) + own ESC/POS encoder, `PdfDocument` for PDFs
* No analytics, ads, network libraries or DI frameworks

`minSdk 26` (Android 8.0), `targetSdk/compileSdk 35`. Pure Kotlin/Java code, so it runs on
ARM64, ARMv7 and x86_64 devices.

---

## Project structure

```
app/src/main/java/com/dtpos/salonmanager/
├── core/            di (AppContainer), util (money, dates, CSV, UiText), validation, security (hashing)
├── domain/          models, calculators (sale, profit, cash, staff pay, targets, budget,
│                    receipt numbering), insights (AI-ready engine), license (codec, evaluator)
├── data/
│   ├── database/    SalonDatabase, entities, DAOs, converters, migrations, default data
│   ├── repository/  one repository per module (sale transaction lives in SaleRepository)
│   └── DemoDataSeeder.kt
├── services/        printer (ESC/POS, Bluetooth, layouts), backup, export (PDF/CSV/share),
│                    security (lock, biometrics), license, branding (logo)
└── presentation/    theme, components, navigation, and one package per screen
                     (dashboard, sales, customers, services, staff, expenses, cash,
                     reports, targets, insights, settings, setup, lock)
app/src/test/        JVM unit tests + Robolectric database/backup/licence/security tests
app/src/androidTest/ on-device tests (app launch, device SQLite)
tools/license/       vendor licence tool (key generation and licence issuing)
.github/workflows/   CI: tests, APK build, emulator install/launch on Android 8 and 14
```

---

## Requirements

* **Android Studio Ladybug (2024.2) or newer** (Koala Feature Drop also works), with the
  Android SDK Platform 35 installed
* **JDK 17** (the one bundled with Android Studio is fine)
* Internet access for the first Gradle sync (downloads Gradle 8.14.3 and dependencies from
  Google Maven and Maven Central). The finished app itself never needs internet.

## Build

### In Android Studio
1. *File → Open…* and choose this folder. Let Gradle sync.
2. Select the `app` run configuration and a device, press **Run**.

### From the command line
```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit + Robolectric tests
./gradlew connectedDebugAndroidTest   # on-device tests (device/emulator connected)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
The debug build uses the application id `com.dtpos.salonmanager.debug`, so it can be installed
next to the production build without touching its data.

### Ready-made APKs from GitHub Actions
Every push runs `.github/workflows/android.yml`, which runs all tests, builds the debug and
release APKs, uploads them as artifacts (**Actions → run → Artifacts → `SalonManager-debug-apk`**)
and installs + launches the app on Android 8.0 and Android 14 emulators.

### Release APK and signing
A release build is minified (R8) and must be signed before installing:

1. Create a keystore once and keep it safe (losing it means you can never update the app):
   ```bash
   keytool -genkeypair -v -keystore salon-release.jks -alias salon -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Create `keystore.properties` in the project root (it is git-ignored – never commit it):
   ```properties
   storeFile=salon-release.jks
   storePassword=********
   keyAlias=salon
   keyPassword=********
   ```
3. Build: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
   (or `bundleRelease` for Google Play).

Without `keystore.properties` the release APK is produced unsigned
(`app-release-unsigned.apk`); sign it with `apksigner` or add the properties file.

> Moving from the debug build to the release build: they are different apps. Use
> *Settings → Backup & restore* in the debug app, then restore the file in the release app.

---

## First start

The setup screen asks for the salon name, phone, address and currency (PKR / Rs. by default)
and offers to add common salon services with suggested prices. For evaluation you can tick
**Explore with demo data** (sample staff, customers and 30 days of sales; clearly marked
"DEMO DATA" on the dashboard and removable via *Settings → Erase all data*).

Fastest daily flow: **Home → New Sale → tap services → Checkout → Complete sale → Print**.

---

## 58 mm Bluetooth thermal printer

1. Switch the printer on and charge it.
2. *Settings → Printer*: allow the Bluetooth permission and turn Bluetooth on if asked.
3. If the printer is not listed under *Paired devices*, tap **Scan** and **Pair** (common PINs
   are `0000` or `1234`), or pair it in Android's Bluetooth settings.
4. Tap the printer to select it (it is remembered), then **Test print**.
5. Optional: auto-print after each sale, copies, blank lines, paper cut (printers with cutter),
   logo, 80 mm paper.

The app speaks the standard ESC/POS command subset over the Serial Port Profile
(`00001101-0000-1000-8000-00805F9B34FB`) and tries three connection methods, so it works with
the common unbranded 58 mm printers as well as branded ones. If Urdu or special characters
print as `?`, switch *Print mode* to **Image**.

Troubleshooting: "Could not connect" → printer off, out of range, or connected to another
phone. "Permission needed" → allow *Nearby devices* for the app in Android settings.

---

## Backup & restore

* **Save backup file** writes a `.salonbak` file wherever you choose (Downloads, USB, Drive…),
  optionally encrypted with a password (AES-256-GCM, key derived with PBKDF2).
* **Share backup** sends the file via WhatsApp, email, etc.
* The app also keeps an **automatic daily backup** in its private storage (last 10).
* **Restore** validates the file (checksum, SQLite integrity check, schema version), shows its
  date and salon name, asks for confirmation, **saves a safety backup of the current data**,
  swaps the database and restarts the app. If anything fails the previous data is kept.
* *Export all data as CSV* produces a zip with sales, sale items, expenses, staff payments,
  customers and staff for Excel / Google Sheets.

A backup file contains the database (all business data, settings and the stored licence key)
and the logo. Keep backup files private.

---

## Licensing architecture

See [docs/LICENSING.md](docs/LICENSING.md). Summary:

* Licence keys look like `SLN1.<payload>.<signature>` – an ECDSA P-256 signature over the
  licence data (ID, business name, plan, issue and expiry date, installation ID, features).
* The app contains only the **public** key and verifies keys offline. The private key stays
  with the vendor (`tools/license`).
* `gradle.properties`: `salon.enforceLicense=false` (owner edition, default) or `true`
  (commercial build with trial of `salon.trialDays` days); `salon.licensePublicKey=...`.
* After expiry the app switches to read-only: viewing, reports and backups keep working, new
  sales are blocked. Nothing is ever deleted.
* An online verifier can be added later (the evaluator and manager are separated for that).

## Multi-salon readiness

Every major table has a `businessId` column (with foreign keys and indexes) and every
repository is scoped by it. Version 1 uses business `1`; a future multi-salon edition only needs
a business picker and a way to create more business rows.

## AI insights architecture

`domain/insights`: `InsightEngine` interface, the offline `RuleBasedInsightEngine`, and
`CompositeInsightEngine(fallback, primary)`. A future on-device or online AI engine is passed as
`primary`; if it is missing, fails or returns nothing, the offline engine is used, so AI can
never block daily work. Insights are structured objects (translated in the UI), which keeps the
engine testable and language-independent.

---

## Data safety & migrations

* All money is stored as integer minor units (paisa) – no floating point rounding.
* A sale, its items, the customer visit, the receipt number and the cash movement are written in
  **one Room transaction** (all or nothing); a unique index guarantees unique receipt numbers.
* Room schemas are exported to `app/schemas/` and committed
  (`app/schemas/com.dtpos.salonmanager.data.database.SalonDatabase/1.json`). CI fails if an
  entity changes without a committed schema.
* Every schema change must bump the database version and add a `Migration` in
  `data/database/Migrations.kt` with a `MigrationTestHelper` test (`androidTest/MigrationTest.kt`
  already opens every committed schema version with the current code). Destructive migration is
  never enabled, so an update can not silently wipe a salon's data.

## Tests

| Suite | Covers |
|-------|--------|
| `domain/*Test`, `core/*Test` | sale & discount maths, discount allocation, commission, profit and expense separation, cash reconciliation, staff settlement, targets, budgets, receipt numbering, validation, money parsing/formatting, PIN hashing, CSV |
| `services/*Test` | licence signing/verification/evaluation, ESC/POS encoding and 58/80 mm layouts, dithering, backup encryption, insights |
| `data/*Test` (Robolectric) | atomic sale transaction and rollback, receipt uniqueness, voids, customer history, duplicate phones, expense separation in reports, cash counter, staff settlement, dashboard |
| `services/BackupRestoreTest`, `LicenseAndSecurityTest` (Robolectric) | backup → restore round trip with safety copy, encrypted backups, auto backup, licence activation rules, PIN lockout and recovery |
| `androidTest` | app launch, the real queries on the device's SQLite, and the committed schema/migrations (run on Android 8 and 14 in CI) |

---

## Known limitations / TODO

* **Urdu translation** – architecture ready (all strings externalised, RTL enabled), translated
  `values-ur` files not yet written.
* **Online licence verification / revocation** – not implemented (offline keys only), by design
  for v1.
* **AI engine** – only the offline rule engine ships; the AI provider interface is ready.
* **Multi-salon UI** – data model ready, UI supports one business.
* **Appointments / booking, SMS/WhatsApp reminders, inventory** – not in scope for v1.
* Printers that only support Bluetooth Low Energy (not Classic SPP) are not supported.

## Roadmap

1. Urdu translation and Urdu receipts (image print mode already supports it)
2. Appointment booking and customer reminders
3. Product / inventory sales alongside services
4. Optional cloud backup and online licence server
5. Multi-branch management with consolidated reports
6. AI assistant for insights and target planning

## Privacy

No internet permission, no analytics, no third-party SDKs. Data leaves the phone only when the
owner explicitly saves or shares a backup, receipt, report or CSV export.
