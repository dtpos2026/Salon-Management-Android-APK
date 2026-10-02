# DT Salon Management – Setup aur chalane ka guide

Is guide mein yeh sab hai: Firebase ki ek dafa setting, APK banana, Super Admin panel deploy karna,
aur rozana salon owners ko manage karna. Har step ke saath batayi gayi jagah par click karein.

> Sab kuch aap ke Firebase project **dt-salon-mangment** ke liye tayyar hai.
> Koi password, key ya config file GitHub repo mein nahi rakhi gayi. Yeh sab aap khud
> **GitHub Secrets** mein daalte hain (step 3), taake koi aur inhein na dekh sake.

---

## 1. System kaise kaam karta hai

```
Salon owner ka phone (DT Salon app)            Aap (Super Admin panel, browser)
  - Gmail se login                                - Naye salons approve
  - Salon ka sara data ISI phone mein (offline)   - Plan, fees, expiry, payment
  - Sirf account/licence online check hota hai    - Suspend / block / message
                 \                                 /
                  \---------  Firebase  ----------/
                       (Login + accounts + invoices)
```

- Salon ki sales, customers, staff, kharche **kabhi online nahi jate**. Woh sirf phone mein rehte hain.
- Online sirf yeh hai: Gmail login, account ka status (pending/approved/...), licence ki expiry,
  aur aap ke invoices.
- Approved phone **internet ke baghair** chalta rehta hai. Har 30 din mein (yeh admin panel se
  badal sakte hain) ek dafa internet se account check hona zaroori hai.

---

## 2. Firebase console mein ek dafa ki setting

Firebase console kholein: https://console.firebase.google.com/project/dt-salon-mangment

### 2.1 Firestore Database on karein
1. Left menu: **Build → Firestore Database → Create database**.
2. **Production mode** chunein.
3. Location: **asia-south1 (Mumbai)** ya jo qareeb ho. **Enable**.

### 2.2 Google login on karein
1. **Build → Authentication → Get started** (agar pehle nahi kiya).
2. **Sign-in method → Google → Enable**.
3. "Project support email" mein apni Gmail chunein → **Save**.

### 2.3 Security rules publish karein
Rules yeh yaqeeni banate hain ke koi salon doosre salon ka account nahi dekh sakta aur koi khud
ko approve nahi kar sakta.

**Tareeqa A (PC par Firebase CLI, behtar):** step 5.1 ka `firebase deploy` rules aur indexes
dono khud publish kar deta hai.

**Tareeqa B (sirf browser):**
1. **Firestore Database → Rules** tab.
2. Repo ki file `firebase/firestore.rules` ka poora text copy kar ke wahan paste karein (purana
   sab mita kar).
3. **Publish**.
4. Indexes: panel pehli dafa kisi list par "needs a Firestore index" dikhaye to browser console
   (F12) mein diya gaya link kholein aur **Create index** dabayein. Ya `firebase/firestore.indexes.json`
   ke 4 indexes **Firestore → Indexes → Composite** mein bana dein.

### 2.4 App ki signing key aur SHA-1 (Google login ke liye zaroori)
Android par Google login sirf usi app mein chalta hai jis ki signing key ka fingerprint Firebase
mein darj ho. Is liye ek **pakki signing key** banani hai (sirf ek dafa, aur isay sambhal kar rakhna hai).

PC par (Android Studio ya Java install ho):
- **Windows:** repo ki folder `tools/signing` mein PowerShell kholein:
  `powershell -ExecutionPolicy Bypass -File create-signing-key.ps1`
- **Mac / Linux / Git Bash:** `bash tools/signing/create-signing-key.sh`

Script password poochegi (kam az kam 12 huroof, yaad rakhein). Phir yeh banayegi:
- `dt-salon-release.p12` – aap ki key. **Iski backup USB/Google Drive mein rakhein.** Yeh gum
  ho gayi to installed apps ko kabhi update nahi kar sakenge.
- `DT_KEYSTORE_BASE64.txt` – wohi key text ki shakal mein (GitHub secret ke liye).
- Screen par **SHA1** aur **SHA256** fingerprints.

Ab Firebase mein:
1. **Project settings (⚙) → General → Your apps → Android app (dtsalon.management)**.
2. **Add fingerprint** → SHA1 paste → Save. Phir dobara **Add fingerprint** → SHA256 → Save.
3. Isi jagah se **google-services.json** dobara download karein (ab is mein Google login ki
   settings bhi hongi).

> PC nahi hai? Kisi bhi PC par Android Studio laga kar *Build → Generate Signed App Bundle/APK →
> Create new keystore* se bhi key ban sakti hai (alias: `dtsalon`, store aur key ka password ek
> hi rakhein). SHA-1 baad mein GitHub Actions ke log ("Configure signing key") mein bhi chhapta hai.

---

## 3. GitHub Secrets (ek dafa)

GitHub par repo kholein → **Settings → Secrets and variables → Actions → New repository secret**.
Yeh 4 secrets banayein (naam bilkul isi tarah):

| Secret ka naam | Value |
|---|---|
| `DT_KEYSTORE_BASE64` | `DT_KEYSTORE_BASE64.txt` file ka poora text |
| `DT_SIGNING_PASSWORD` | Signing key ka password |
| `GOOGLE_SERVICES_JSON` | Step 2.4 mein dobara download ki gayi `google-services.json` ka poora text |
| `FIREBASE_WEB_CONFIG` | Firebase web app config (sirf GitHub Pages wale panel ke liye, step 5.2) |

`FIREBASE_WEB_CONFIG` ke liye: **Project settings → General → Your apps → Web app → SDK setup
and configuration → Config**. `const firebaseConfig = {...}` wala poora hissa paste kar dein.

---

## 4. APK lena aur install karna

1. Secrets add karne ke baad repo par koi bhi push ho, ya **Actions → Android CI → Run workflow**
   dabayein.
2. Run green hone par usi run mein neeche **Artifacts → SalonManager-release-apk** download karein.
3. Zip kholein, andar `app-release.apk` hogi. Yeh **signed aur fast** hai. Salon owners ko yahi dein.
4. Phone mein install karein ("unknown sources" ki ijazat dein).

> `SalonManager-debug-apk` sirf testing ke liye hai; release APK zyada tez chalti hai.
> Purani "Salon Manager" app (com.dtpos.salonmanager) alag app thi; yeh nayi app
> `dtsalon.management` hai.

---

## 5. Super Admin panel deploy karna

Panel `admin-panel/` folder mein hai. Is ka koi build nahi; bas upload karna hai. Do tareeqe hain:

### 5.1 Tareeqa A – Firebase Hosting (behtar, PC chahiye)
1. Node.js install karein: https://nodejs.org (LTS).
2. Terminal / Command Prompt mein:
   ```
   npm install -g firebase-tools
   firebase login
   ```
3. Repo ka folder kholein (GitHub se **Code → Download ZIP** kar ke unzip karein) aur us folder mein:
   ```
   firebase deploy
   ```
   Yeh ek hi command mein **panel + security rules + indexes** sab publish kar deti hai.
4. Panel ka address: **https://dt-salon-mangment.web.app**
   Firebase Hosting par panel apni config khud le leta hai; koi file banane ki zaroorat nahi.

Baad mein panel update karna ho to wohi `firebase deploy` dobara chala dein.

### 5.2 Tareeqa B – GitHub Pages (PC ke baghair)
1. Repo **Settings → Pages → Build and deployment → Source: GitHub Actions**.
2. **Settings → Secrets and variables → Actions → Variables → New repository variable**:
   naam `ADMIN_PANEL_PAGES`, value `true`.
3. Secret `FIREBASE_WEB_CONFIG` (step 3) add karein.
4. Yeh branch main branch mein merge karein. "Super Admin panel" workflow tests chala kar panel
   deploy karega. Address: **https://dtpos2026.github.io/Salon-Management-Android-APK/**
5. Firebase: **Authentication → Settings → Authorized domains → Add domain** →
   `dtpos2026.github.io`.
6. Rules step 2.3 ke tareeqa B se publish karein.

> Note: GitHub Pages free mein sirf **public** repo par chalta hai. Repo private karna ho to
> tareeqa A (Firebase Hosting) istemal karein.

---

## 6. Pehla Super Admin banana (ek dafa)

1. Panel kholein → **Continue with Google** → apni Gmail chunein.
2. Screen kahegi "No admin access" aur aap ka **UID** dikhayegi → **Copy UID**.
3. Firebase console → **Firestore Database → Data → Start collection**:
   - Collection ID: `admins`
   - Document ID: copy kiya hua UID paste karein
   - Field: `email` (string) = aap ki Gmail → **Save**
4. Panel par **Retry** dabayein. Dashboard khul jayega.

Doosre admin baad mein panel ke **Admins** page se add ho sakte hain (unhein bhi pehle ek dafa
panel par sign in kar ke apna UID bhejna hoga).

---

## 7. Rozana ka kaam (Super Admin panel)

### Naya salon
1. Salon owner app install kar ke **Continue with Google** karta hai aur salon ka naam, owner,
   phone, shehar likh kar **Submit for approval** dabata hai.
2. Aap ke panel ke Dashboard par **Waiting for approval** mein aa jata hai.
3. **Approve** dabayein → plan (Trial / Monthly / 3 / 6 / 12 months / Lifetime / Custom date) aur
   monthly fee likhein → **Approve account**.
4. Customer ID (DTC-0001), Business ID aur License ID khud ban jate hain. Salon ki app internet par
   **khud khul jati hai** (dobara login ki zaroorat nahi).

### Billing aur invoice
1. Salon ke page par **+ Invoice** (ya Invoices → New invoice). Salon ki fee aur agla period khud
   bhar jata hai.
2. **Create invoice** → invoice A4 ya Receipt design mein dikhti hai (bilkul aap ki di hui designs
   jaisi), saath "Scan to verify" QR.
3. **Download PNG**, **Print / PDF**, **Share** (phone par seedha WhatsApp) ya **WhatsApp** dabayein.
4. Payment aane par **Record payment** → "Extend licence" tick rahe to licence plan ke hisaab se
   aage barh jata hai aur salon dobara active ho jata hai.
5. Settings → **Billing & invoices** mein bank account, payment QR, signature, terms ek dafa daal dein.

### Status badalna
Salon ke page par: **Payment pending**, **Suspend**, **Block**, **Mark expired**, **Reject**,
**Re-activate**. Har ek ke saath salon ko message bhi likh sakte hain; woh app mein nazar aata hai.
- Expiry date guzarte hi app khud "Subscription expired" dikhati hai, chahe phone offline ho.
- Salon ka data hamesha unke phone mein mehfooz rehta hai; status wapas Active karte hi sab wahin
  se chalta hai.

### Dashboard aur lists
- Dashboard: total, waiting, active, payment pending, expired, 7 din mein expire hone wale,
  is mahine ki wasooli aur baqaya.
- Salons page: naam ke shuru, Gmail, phone, Customer ID, License ID ya UID se search; status
  filters; private notes (sirf admin dekhta hai).

### App control (Settings → App control)
- **Offline days allowed**: approved phone kitne din internet ke baghair chale (default 30).
- **Notice to all salons**: sab apps mein ek dafa dikhne wala paigham.
- **Latest / Minimum version**: naya APK dene par yahan version code aur download link likhein;
  minimum se purani apps ko update karna zaroori ho jata hai.

### Branding (Settings → App branding)
App ka naam, company, contact number, WhatsApp, email, website aur support message. Yeh salon
apps ke login, approval aur About screens par dikhte hain (WhatsApp/Call buttons inhi se chalte hain).

---

## 8. Salon owner ki app mein kya hai

- **Pehli dafa:** khoobsurat intro → Gmail login → registration → "Waiting for approval".
- **Approval ke baad:** salon setup (naam, phone wagera pehle se bhare hue) → poori POS app.
- **Settings → Account & subscription:** Gmail, Customer/Business/License ID, plan, expiry,
  "Verify now", Sign out (data phone mein hi rehta hai).
- **Settings → App preferences:** 3 themes (Royal Purple, Black & Gold, Rose Gold), Light/Dark/Phone
  setting, zabaan (English, اردو, Roman Urdu), awaazein ON/OFF.
- **Receipt:** sale ke baad branded receipt ki preview, Print, **Save PNG**, **Save JPEG** (Gallery →
  Pictures/DT Salon), Share aur **WhatsApp** (customer ke number par receipt ki tasveer aur paigham;
  bhejne ka button aap khud dabate hain).
- Ek phone ka data ek Gmail se juda hota hai. Koi doosri Gmail login kare to app poochti hai: usi
  Gmail se login karein ya (backup ke baad) is phone ka data mita kar naya shuru karein.

---

## 9. Masle aur hal

| Masla | Hal |
|---|---|
| App kahe "App setup incomplete" | `GOOGLE_SERVICES_JSON` secret nahi tha jab APK bani. Secret add kar ke workflow dobara chalayein. |
| "Google sign-in is not set up yet" | Step 2.2 (Google enable) aur 2.4 (SHA-1 + naya google-services.json + secret update) poore karein, phir nayi APK banayein. |
| Google login par "No Google account found" | Phone ki Settings → Accounts mein Google account add karein. |
| Panel: "No admin access" | Step 6 (admins collection) karein. |
| Panel: "Permission denied" | Rules publish nahi hue (step 2.3) ya aap admin nahi. |
| Panel: "needs a Firestore index" | Step 2.3 ka index wala hissa, ya `firebase deploy`. |
| Panel par Google popup: "unauthorized domain" | Authentication → Settings → Authorized domains mein panel ka domain add karein. |
| Salon kahe "Internet needed" | Phone ko ek dafa internet se jod kar "Check again". |

---

## 10. Hifazat (security)

- Signing key, passwords, google-services.json aur web config **sirf GitHub Secrets** mein hain,
  repo mein nahi.
- Firestore rules par 8 automatic tests chalte hain (salon khud ko approve nahi kar sakta, doosre
  salon ka data nahi dekh sakta, koi khud admin nahi ban sakta, bank details sirf admin dekh sakta hai).
- Super Admin panel ka end-to-end test bhi har change par chalta hai (login, approve, suspend,
  invoice, PNG, payment, QR verify, settings).
- Firebase ka free (Spark) plan is kaam ke liye kaafi hai.

---

## 11. Jo is version mein jaan boojh kar nahi hai

- **Cloud sync / online salon data:** aap ki hidayat ke mutabiq salon ka data sirf phone mein hai.
  Architecture tayyar hai (har record mein businessId); baad mein add ho sakta hai.
- **Activation keys:** in ki jagah Gmail + admin approval + licence expiry istemal hui hai.
- **Appointments:** app mein pehle bhi nahi thi, is liye add nahi ki.
- **Firebase Storage:** logo, QR aur signature chhoti tasveeren hain, Firestore settings mein hi
  rakhi jati hain (Storage ki zaroorat nahi).
- **Online payment gateway:** payment haath se "Record payment" se darj hoti hai.
