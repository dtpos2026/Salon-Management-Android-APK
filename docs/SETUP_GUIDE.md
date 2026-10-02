# DT Salon Management – Setup aur chalane ka guide

Is guide mein yeh sab hai: Firebase ki ek dafa setting, APK lena, Super Admin panel deploy karna,
aur rozana salon owners aur un ke phones ko approve karna.

> Sab kuch aap ke Firebase project **dt-salon-mangment** ke liye tayyar hai.
> Koi password, key ya config file GitHub repo mein nahi rakhi gayi. Yeh aap khud
> **GitHub Secrets** mein daalte hain (step 3).

**Sab se chhota raasta (zaroori kaam):** 2.1 Firestore on → 2.2 Email/Password on →
3 secret `GOOGLE_SERVICES_JSON` → 4 APK lein → 5 panel deploy → 6 apna admin login.

---

## 1. System kaise kaam karta hai

```
Salon owner ka phone (DT Salon app)            Aap (Super Admin panel, browser)
  - Email + password se login / naya account      - Naye salons approve
  - Salon ka sara data ISI phone mein (offline)   - Naya phone approve
  - Account sirf ek phone par chalta hai          - Plan, fees, expiry, payment
                 \                                 /- Suspend / block / message
                  \---------  Firebase  ----------/
                       (Login + accounts + invoices)
```

- Salon ki sales, customers, staff, kharche **kabhi online nahi jate**. Woh sirf phone mein rehte hain.
- Online sirf yeh hai: login, account ka status (pending/approved/...), kaunsa phone approved hai,
  licence ki expiry aur aap ke invoices.
- Approved phone **internet ke baghair** chalta rehta hai. Har 30 din mein (admin panel se badal
  sakte hain) ek dafa internet se account check hona zaroori hai.
- **Ek account = ek phone.** Wahi email kisi doosre phone par login ho to woh phone "New phone needs
  approval" dikhata hai aur aap ke panel mein request aa jati hai. Aap approve karein to account
  naye phone par chala jata hai aur purana phone band ho jata hai.

---

## 2. Firebase console mein ek dafa ki setting

Firebase console kholein: https://console.firebase.google.com/project/dt-salon-mangment

### 2.1 Firestore Database on karein
1. Left menu: **Build → Firestore Database → Create database**.
2. **Production mode** chunein.
3. Location: **asia-south1 (Mumbai)** ya jo qareeb ho. **Enable**.

### 2.2 Email/Password login on karein
1. **Build → Authentication → Get started** (agar pehle nahi kiya).
2. **Sign-in method → Email/Password → Enable** (pehla switch) → **Save**.

Bas. Email login ke liye **SHA-1 ki zaroorat nahi**.

### 2.3 Security rules publish karein
Rules yeh yaqeeni banate hain ke koi salon doosre salon ka account nahi dekh sakta, koi khud ko
approve nahi kar sakta aur koi apna phone khud approve nahi kar sakta.

**Tareeqa A (PC par Firebase CLI, behtar):** step 5.1 ka `firebase deploy` rules aur indexes
dono khud publish kar deta hai.

**Tareeqa B (sirf browser):**
1. **Firestore Database → Rules** tab.
2. Repo ki file `firebase/firestore.rules` ka **naya** poora text copy kar ke wahan paste karein
   (purana sab mita kar). **Publish**.
3. Indexes: panel kisi list par "needs a Firestore index" dikhaye to browser console (F12) mein
   diya gaya link kholein aur **Create index** dabayein.

> Rules is version mein badle hain (phone approval). Pehle publish kiye the to dobara publish karein.

---

## 3. GitHub Secrets

GitHub par repo kholein → **Settings → Secrets and variables → Actions → New repository secret**.

| Secret ka naam | Zaroori? | Value |
|---|---|---|
| `GOOGLE_SERVICES_JSON` | **Haan** | Firebase → Project settings → Your apps → Android app `dtsalon.management` → `google-services.json` download kar ke Notepad mein kholein, poora text paste |
| `DT_KEYSTORE_BASE64` | Mashwara | Signing key (neeche 3.1) |
| `DT_SIGNING_PASSWORD` | Mashwara | Signing key ka password |
| `FIREBASE_WEB_CONFIG` | Sirf GitHub Pages panel ke liye | Firebase web app config (step 5.2) |

### 3.1 Signing key (mashwara, ek dafa)
Signing key ke baghair bhi app chalti hai (**debug APK**), lekin har nayi APK alag key se banti hai.
Is liye nayi APK purani ke upar install nahi hogi: pehle purani uninstall karni padegi, aur
uninstall se **phone ka salon data mit jata hai** (pehle Settings → Backup kar lein).
Ek pakki key bana lein to nayi APK seedha upar install hoti hai aur data mehfooz rehta hai:

- **Windows:** repo ke folder `tools/signing` mein CMD kholein:
  `powershell -ExecutionPolicy Bypass -File create-signing-key.ps1`
- **Mac / Linux / Git Bash:** `bash tools/signing/create-signing-key.sh`

Script password poochegi (kam az kam 12 huroof, yaad rakhein) aur `dt-salon-release.p12` (backup
USB/Drive mein rakhein) aur `DT_KEYSTORE_BASE64.txt` banayegi. Us text ko `DT_KEYSTORE_BASE64`
secret mein aur password ko `DT_SIGNING_PASSWORD` mein daal dein. (Android Studio ya Java chahiye.)

---

## 4. APK lena aur install karna

1. Secrets add karne ke baad **Actions → Android CI → Run workflow** → branch
   `claude/adoring-feynman-iae1sx` → **Run workflow**.
2. Run green hone par usi run ke neeche **Artifacts** se:
   - signing key secrets diye hain to **SalonManager-release-apk** (tez, signed) lein;
   - warna **SalonManager-debug-apk** lein.
3. Zip kholein, andar `.apk` hogi. Phone mein install karein ("unknown sources" ki ijazat dein).

> Login screen par "App setup incomplete" aaye to APK `GOOGLE_SERVICES_JSON` secret ke baghair bani
> thi. Secret daal kar workflow dobara chalayein.

---

## 5. Super Admin panel deploy karna

Panel `admin-panel/` folder mein hai. Is ka koi build nahi; bas upload karna hai.

### 5.1 Tareeqa A – Firebase Hosting (behtar, PC chahiye)
1. Node.js install karein: https://nodejs.org (LTS). Firebase console mein **Hosting → Get started**
   ek dafa dabayein.
2. Code download: https://github.com/dtpos2026/Salon-Management-Android-APK/archive/refs/heads/claude/adoring-feynman-iae1sx.zip
   → unzip (maslan `C:\dt-salon`).
3. CMD mein:
   ```
   npm install -g firebase-tools
   firebase login
   cd C:\dt-salon\Salon-Management-Android-APK-claude-adoring-feynman-iae1sx
   firebase deploy
   ```
   Yeh ek hi command mein **panel + security rules + indexes** sab publish kar deti hai.
4. Panel ka address: **https://dt-salon-mangment.web.app**

Baad mein panel update karna ho to wohi `firebase deploy` dobara chala dein.

### 5.2 Tareeqa B – GitHub Pages (PC ke baghair)
1. Repo **Settings → Pages → Build and deployment → Source: GitHub Actions**.
2. **Settings → Secrets and variables → Actions → Variables → New repository variable**:
   naam `ADMIN_PANEL_PAGES`, value `true`.
3. Secret `FIREBASE_WEB_CONFIG`: **Project settings → General → Your apps → Web app → Config**
   wala `const firebaseConfig = {...}` poora paste.
4. Yeh branch main branch mein merge karein. "Super Admin panel" workflow panel deploy karega.
   Address: **https://dtpos2026.github.io/Salon-Management-Android-APK/**
5. Firebase: **Authentication → Settings → Authorized domains → Add domain** → `dtpos2026.github.io`.
6. Rules step 2.3 ke tareeqa B se publish karein.

> GitHub Pages free mein sirf **public** repo par chalta hai.

---

## 6. Pehla Super Admin banana (ek dafa)

1. Firebase console → **Authentication → Users → Add user** → apni email aur ek mazboot password →
   **Add user**.
2. Panel kholein → wohi email aur password → **Sign in**.
3. Screen kahegi "No admin access" aur aap ka **UID** dikhayegi → **Copy UID**.
4. Firebase console → **Firestore Database → Data → Start collection**:
   - Collection ID: `admins`
   - Document ID: copy kiya hua UID paste karein
   - Field: `email` (string) = aap ki email → **Save**
5. Panel par **Retry** dabayein. Dashboard khul jayega.

Doosre admin: unka login bhi step 1 se banayein, woh ek dafa panel par sign in kar ke apna UID
bhejein, phir panel ke **Admins** page se add karein. Password bhool jayein to panel par
"Forgot password?" dabayein.

---

## 7. Rozana ka kaam (Super Admin panel)

### Naya salon
1. Salon owner app install kar ke **Create account** (email + password) karta hai, phir salon ka
   naam, owner, phone, shehar likh kar **Submit for approval** dabata hai. Account usi phone se
   jud jata hai.
2. Aap ke panel ke Dashboard par **Waiting for approval** mein aa jata hai.
3. **Approve** dabayein → plan (Trial / Monthly / 3 / 6 / 12 months / Lifetime / Custom date) aur
   monthly fee likhein → **Approve account**.
4. Customer ID (DTC-0001), Business ID aur License ID khud ban jate hain. Salon ki app internet par
   **khud khul jati hai** (dobara login ki zaroorat nahi).

### Naya phone (device approval)
1. Salon owner naya phone le, ya koi aur us ka email/password doosre phone par istemal kare, to
   us phone par "New phone needs approval" aata hai aur request khud aap ko aa jati hai.
2. Dashboard par **New phone requests** (ya Salons → **New phone requests** filter).
3. Salon ke page par phone ka model dikhega → **Approve this phone** (ya **Ignore**).
4. Approve karte hi account naye phone par khul jata hai aur **purana phone band** ho jata hai.
   (Purane phone ka data naye phone mein khud nahi jata: purane phone se Settings → Backup bana
   kar naye phone mein Restore karein.)

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
- Salons page: naam ke shuru, email, phone, Customer ID, License ID ya UID se search; status
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

- **Pehli dafa:** khoobsurat intro → **Create account** (email + password) → salon details →
  "Waiting for approval". Password bhoolne par login screen par **Forgot password?**.
- **Approval ke baad:** salon setup (naam, phone wagera pehle se bhare hue) → poori POS app.
- **Settings → Account & subscription:** email, Customer/Business/License ID, plan, expiry,
  "Verify now", Sign out (data phone mein hi rehta hai).
- **Settings → App preferences:** 3 themes (Royal Purple, Black & Gold, Rose Gold), Light/Dark/Phone
  setting, zabaan (English, اردو, Roman Urdu), awaazein ON/OFF.
- **Receipt:** sale ke baad branded receipt ki preview, Print, **Save PNG**, **Save JPEG** (Gallery →
  Pictures/DT Salon), Share aur **WhatsApp** (customer ke number par receipt ki tasveer aur paigham;
  bhejne ka button aap khud dabate hain).
- Ek phone ka data ek account se juda hota hai. Koi doosra account isi phone par login kare to app
  poochti hai: usi account se login karein ya (backup ke baad) is phone ka data mita kar naya shuru karein.

---

## 9. Masle aur hal

| Masla | Hal |
|---|---|
| App kahe "App setup incomplete" | `GOOGLE_SERVICES_JSON` secret nahi tha jab APK bani. Secret add kar ke workflow dobara chalayein. |
| "Email login is not switched on yet" | Step 2.2 (Email/Password enable). |
| "Email or password is incorrect" | Sahi email/password, ya login screen par "Forgot password?". |
| "New phone needs approval" | Panel → salon ka page → **Approve this phone**. |
| Nayi APK install nahi hoti ("app not installed") | Purani APK doosri key se bani thi. Backup le kar purani uninstall karein, ya signing key (3.1) istemal karein. |
| Panel: "No admin access" | Step 6 (admins collection) karein. |
| Panel: "Permission denied" | Rules publish nahi hue (step 2.3) ya aap admin nahi. |
| Panel: "needs a Firestore index" | Step 2.3 ka index wala hissa, ya `firebase deploy`. |
| Salon kahe "Internet needed" | Phone ko ek dafa internet se jod kar "Check again". |

---

## 10. Hifazat (security)

- Signing key, passwords, google-services.json aur web config **sirf GitHub Secrets** mein hain,
  repo mein nahi.
- Firestore rules par 9 automatic tests chalte hain (salon khud ko ya apna naya phone approve nahi
  kar sakta, doosre salon ka data nahi dekh sakta, koi khud admin nahi ban sakta, bank details sirf
  admin dekh sakta hai).
- Super Admin panel ka end-to-end test bhi har change par chalta hai (login, approve, naya phone
  approve, suspend, invoice, PNG, payment, QR verify, settings).
- Firebase ka free (Spark) plan is kaam ke liye kaafi hai.

---

## 11. Jo is version mein jaan boojh kar nahi hai

- **Cloud sync / online salon data:** aap ki hidayat ke mutabiq salon ka data sirf phone mein hai.
  Architecture tayyar hai (har record mein businessId); baad mein add ho sakta hai.
- **Activation keys:** in ki jagah email login + account aur phone ki admin approval + licence
  expiry istemal hui hai.
- **Google login:** aap ki hidayat par hata diya; email + password hai (SHA-1 ki zaroorat nahi).
- **Appointments:** app mein pehle bhi nahi thi, is liye add nahi ki.
- **Firebase Storage:** logo, QR aur signature chhoti tasveeren hain, Firestore settings mein hi
  rakhi jati hain (Storage ki zaroorat nahi).
- **Online payment gateway:** payment haath se "Record payment" se darj hoti hai.
