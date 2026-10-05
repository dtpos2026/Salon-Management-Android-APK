# DT Salon Management - app guide for the assistant

DT Salon Management is an offline Android POS for salons and barber shops, made by Digital Target.
All salon data (sales, customers, staff, expenses, cash) stays on the phone and works without internet.
The account (login, approval, licence), support chat and the daily sales totals (amount and number of
customers only, for DT billing) use the internet. Customer names, phones and receipts never leave the phone.

## Account and login
- Login with email and password. New salon: "Create account" on the login screen, then fill salon details and "Submit for approval".
- The account waits for approval by DT (screen "Waiting for approval"). After approval the app opens by itself when online.
- Forgot password: type the email on the login screen and tap "Forgot password?"; a reset link comes by email (check Spam).
- Each login works only on phones DT approved. On another phone the app shows "New phone needs approval" and sends a request; DT approves it, then tap "Check again".
- The phone must connect to the internet at least once a week to check the account. Statuses: Pending, Approved, Payment pending, Suspended, Blocked, Expired. Data stays safe on the phone in every status.
- Settings > Account & subscription shows Customer ID, Licence ID, plan and expiry, and "Sign out" (data stays on the phone).

## Daily work
- Home (dashboard): chips Today / Yesterday / This week / This month / pick a date; cards for sales, customers, cash received, online received, udhaar, staff commission, expenses and profit; "Money by account" (cash, JazzCash, EasyPaisa, bank, card, udhaar); staff work; today's tokens; buttons Token, Udhaar, Close day; shortcuts (Close day, Menu, Udhaar, Promotions, AI, Support, Guide, Services, Staff, Cash Counter, Payment accounts, Targets, Budget, Insights, Settings, About).
- New Sale (POS): tap services (photos show when added), choose staff per service, add a customer (name/phone) or walk-in, discounts per item or per bill. Payment: Cash or one of the salon's payment accounts (JazzCash, EasyPaisa, bank, card machine; without accounts: card, bank, other), received amount and change.
- Paid in full or Udhaar (credit): with Udhaar choose the customer, enter "Paid now" (optional); the rest becomes the customer's pending bill automatically and only the paid part goes into the cash drawer. The receipt shows Paid, Balance (udhaar) and an UDHAAR stamp.
- Sales: list of receipts, open one to reprint, share, save as PNG/JPEG, send on WhatsApp, "Edit receipt" to correct a wrong service/price/staff/payment/udhaar (owner PIN if set; the receipt number stays), or void it with a reason.
- Settings > Payment accounts: add JazzCash, EasyPaisa, bank account or card machine (name, title, number). Used accounts are hidden, not deleted.
- Close day (Home > Close day): the day's total sales, customers, services, money by account, udhaar given and received, each staff member's sales and commission, the owner's own work, expenses, staff paid, profit and the cash drawer (opening, cash in/out, expected). Count the cash, enter it, press "Close day"; print the report (58/80 mm) or share it. After closing, new sales count for the next day so the dashboard starts from zero. "Reopen day" undoes a mistaken close.
- Menu (Home > Menu): services with photos, names and prices by category, like a restaurant menu, to show customers. Photos are added in Services (Add photo).
- Guide (Home > Guide or Settings > Guide): step-by-step help for every part of the app, searchable, in English, Urdu and Roman Urdu.
- Customers: add/edit, visit history, favourite services, total spent; start a sale for a customer.
- Expenses: business and personal expenses kept separate, with categories (Settings > Expense categories).
- Staff: salary type fixed / commission / both, commission %. Role "Owner" for the owner's own work (no commission, shown apart). Record payment (salary, advance, commission, bonus); after saving, print or share the staff payment slip (name, amount, this month's commission, paid, balance, signature line). The staff list has the same Today / Yesterday / Week / Month / date chips.
- Cash Counter: opening cash, cash in/out, close the day and compare expected vs counted cash.
- Reports: daily/weekly/monthly sales, profit, services, staff performance, expenses; export CSV for Excel.
- Targets and Budget: daily/weekly/monthly sales targets and a monthly budget.
- Insights: offline tips from the salon's numbers. AI Assistant (when DT switches AI on): ask business growth questions.

## Udhaar (pending bills)
- Home > Udhaar > "Add pending bill": customer, phone, amount, note.
- Credit (udhaar) sales from checkout appear here automatically with the receipt number.
- "Remind" opens WhatsApp with a polite reminder showing the balance; "SMS" opens the SMS app with the same text. The owner presses send; the app never claims it was sent.
- "Received" records full or partial payment; tick "Received in cash" to add it to today's cash drawer. Fully paid bills move to Paid.

## Promotions
- Home > Promotions: choose a message (discount offer, Eid/festival, new service, we miss you, thank you), edit it, choose customers (all, not visited in 30 days, top customers) and tap Send for each; WhatsApp opens ready. WhatsApp does not allow automatic bulk sending.

## Tokens and advance booking (if switched on in Settings > Tokens & booking)
- Rush time: give walk-in customers a token number; the queue shows waiting, serving and done.
- Advance booking: pick date and time, customer and service; the booking gets a token for that day.
- After a token is issued (or with the print / chat button on a token) the print preview opens, showing exactly what the printer prints. From there: Print, WhatsApp text, WhatsApp picture (token image), SMS or Share.

## Receipts and printer
- Settings > Printer: choose the connection. Bluetooth: turn on the printer and Bluetooth, pair (common PIN 0000 or 1234), tap the printer to select it. LAN / Wi-Fi: enter the printer's IP address (printed on its self-test page) and port (usually 9100), "Check connection", "Save and use". Then "Test print".
- Paper width 58 mm (default) or 80 mm. Print mode: Image (styled receipt, works for Urdu) or Text (fast, English letters).
- Receipt design: Classic, Modern, Minimal (saves paper), Elegant, Mono (till slip, typewriter font) or Mono table (item, qty, rate, amount columns). Options: auto-print after sale, logo, cut paper, copies, blank lines.
- Services: "Bold name on receipt" and "Bold price on receipt" make a service stand out on printed receipts.
- If nothing prints: check Bluetooth is on, the printer is paired and selected (or for LAN: same Wi-Fi, correct IP and port), paper is loaded, try Image mode, restart the printer, and keep the phone close.
- Settings > Receipt settings: receipt number prefix, header note and thank-you message. Settings > Business profile: salon name, logo, phone, address, currency.

## Safety
- Settings > Backup and restore: save a backup file (share it to WhatsApp/Drive), restore it on the same or a new phone. Take a backup before changing phones or uninstalling.
- Settings > Security: PIN/password/fingerprint lock for the app or for reports and settings.
- Settings > App preferences: colour theme (Royal Purple, Black & Gold, Rose Gold), light/dark, language (English, Urdu, Roman Urdu), sound effects on/off, Fast mode (no intro animation or screen fades, for older phones and rush hours).
- WhatsApp buttons open WhatsApp with the message ready (the owner presses send). Without WhatsApp the app opens the browser link or the share menu and says so.
- Installing a new APK: if the phone says "App not installed", the new APK was signed differently; take a backup, uninstall, install, then restore.

## Help
- Home > Support: chat with DT. The assistant answers first; a DT team member replies for account, payment or technical issues. The bin icon "Clear chat" deletes old messages after confirmation.
- About: WhatsApp, call and email buttons for DT support.
