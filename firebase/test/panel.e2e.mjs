// End-to-end test of the Super Admin panel against the Auth + Firestore emulators:
//   npm run test:panel   (from the firebase/ folder)
// Serves ../admin-panel with an emulator config, signs in, approves a salon, suspends one,
// creates an invoice, exports PNGs, records a payment and checks the public verify page.
import { createServer } from 'node:http';
import { readFile, mkdir, stat } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, getDocs, deleteDoc, collection, Timestamp } from 'firebase/firestore';
import { chromium } from 'playwright';

const PANEL = fileURLToPath(new URL('../../admin-panel/', import.meta.url));
const SHOTS = fileURLToPath(new URL('./screenshots/', import.meta.url));
const PORT = 5055;
const DAY = 86400000;
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml' };

const server = createServer(async (req, res) => {
  const path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (path === '/firebase-config.js') {
    res.writeHead(200, { 'content-type': 'text/javascript' });
    res.end(`window.DT_FIREBASE_CONFIG = { apiKey: 'demo-key', authDomain: 'demo-dt-salon.firebaseapp.com', projectId: 'demo-dt-salon', appId: 'demo' };
window.DT_USE_EMULATORS = true;`);
    return;
  }
  const file = normalize(join(PANEL, path === '/' ? 'index.html' : path));
  if (!file.startsWith(PANEL)) { res.writeHead(403).end(); return; }
  try {
    const body = await readFile(file);
    res.writeHead(200, { 'content-type': TYPES[extname(file)] || 'application/octet-stream' });
    res.end(body);
  } catch {
    res.writeHead(404).end();
  }
});
await new Promise((r) => server.listen(PORT, '127.0.0.1', r));

const env = await initializeTestEnvironment({ projectId: 'demo-dt-salon', firestore: { host: '127.0.0.1', port: 8080 } });
await env.clearFirestore();
const admin = async (fn) => {
  let out;
  await env.withSecurityRulesDisabled(async (ctx) => { out = await fn(ctx.firestore()); });
  return out;
};
const now = Date.now();
await admin(async (db) => {
  await setDoc(doc(db, 'accounts/pending1'), {
    uid: 'pending1', email: 'newsalon@gmail.com', salonName: 'Glam Studio', salonNameLower: 'glam studio', ownerName: 'Sara',
    ownerNameLower: 'sara', phone: '03011112222', phoneDigits: '923011112222', city: 'Lahore', status: 'PENDING',
    createdAt: Timestamp.fromMillis(now - DAY), appVersion: '2.0.0', deviceModel: 'Samsung A15',
  });
  await setDoc(doc(db, 'accounts/royal1'), {
    uid: 'royal1', email: 'royal@gmail.com', salonName: 'Royal Cuts', salonNameLower: 'royal cuts', ownerName: 'Ali Raza',
    ownerNameLower: 'ali raza', phone: '03001234567', phoneDigits: '923001234567', city: 'Burewala', status: 'APPROVED',
    plan: 'MONTHLY', monthlyFee: 1500, customerId: 'DTC-0001', businessId: 'DTB-AAAA2222', licenseId: 'DTL-ABCD-EFGH',
    expiresAt: Timestamp.fromMillis(now + 20 * DAY), createdAt: Timestamp.fromMillis(now - 40 * DAY),
    deviceId: 'a-old-phone-1111', deviceModel: 'Samsung A15', maxDevices: 2,
    pendingDeviceId: 'a-new-phone-2222', pendingDeviceModel: 'Infinix Hot 40', pendingDeviceAt: Timestamp.fromMillis(now - 3600000),
  });
  await setDoc(doc(db, 'accounts/late1'), {
    uid: 'late1', email: 'late@gmail.com', salonName: 'Classic Barber', salonNameLower: 'classic barber', ownerName: 'Usman',
    ownerNameLower: 'usman', phone: '03211234567', status: 'APPROVED', plan: 'MONTHLY', monthlyFee: 1000,
    expiresAt: Timestamp.fromMillis(now - 3 * DAY), createdAt: Timestamp.fromMillis(now - 90 * DAY),
  });
  await setDoc(doc(db, 'counters/customers'), { next: 2 });
  // Phones as the app reports them (DeviceMonitor).
  await setDoc(doc(db, 'devices/royal1__a-old-phone-1111'), {
    uid: 'royal1', deviceId: 'a-old-phone-1111', model: 'Samsung A15', platform: 'android', osVersion: 'Android 14 (API 34)',
    appVersion: '2.1.0', locationPermission: 'granted', lastSeenAt: Timestamp.fromMillis(now - 600000),
    lat: 30.1575, lng: 72.6847, accuracyM: 40, locationAt: Timestamp.fromMillis(now - 600000),
  });
  await setDoc(doc(db, 'devices/royal1__a-new-phone-2222'), {
    uid: 'royal1', deviceId: 'a-new-phone-2222', model: 'Infinix Hot 40', platform: 'android', osVersion: 'Android 13 (API 33)',
    appVersion: '2.1.0', locationPermission: 'off', lastSeenAt: Timestamp.fromMillis(now - 3 * DAY),
  });
  // A phone of a salon that was deleted earlier: hidden on the Phones page and the map.
  await setDoc(doc(db, 'devices/gone1__a-old-gone-9999'), {
    uid: 'gone1', deviceId: 'a-old-gone-9999', model: 'Oppo A5', platform: 'android', osVersion: 'Android 12 (API 31)',
    appVersion: '2.0.0', locationPermission: 'granted', lastSeenAt: Timestamp.fromMillis(now - 9 * DAY),
    lat: 31.52, lng: 74.35, accuracyM: 30, locationAt: Timestamp.fromMillis(now - 9 * DAY),
  });
  // Daily totals the app shares (numbers only).
  const key = (ms) => { const d = new Date(ms); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
  await setDoc(doc(db, 'stats/royal1'), {
    uid: 'royal1', salonName: 'Royal Cuts', currency: 'PKR', day: key(now), todaySalesMinor: 1250000, todayCustomers: 18,
    yesterdaySalesMinor: 990000, yesterdayCustomers: 14, monthKey: key(now).slice(0, 7), monthSalesMinor: 4500000, monthCustomers: 61,
    appVersion: '2.1.0', updatedAt: Timestamp.fromMillis(now - 120000),
  });
  await setDoc(doc(db, `stats/royal1/days/${key(now)}`), { date: key(now), salesMinor: 1250000, customers: 18, services: 25, cashMinor: 900000, onlineMinor: 250000, creditMinor: 100000, updatedAt: Timestamp.fromMillis(now) });
  await setDoc(doc(db, `stats/royal1/days/${key(now - DAY)}`), { date: key(now - DAY), salesMinor: 990000, customers: 14, services: 20, cashMinor: 990000, onlineMinor: 0, creditMinor: 0, updatedAt: Timestamp.fromMillis(now - DAY) });
  await setDoc(doc(db, 'support/royal1'), { uid: 'royal1', salonName: 'Royal Cuts', lastMessage: 'Printer not printing', lastFrom: 'user', lastAt: Timestamp.fromMillis(now - 60000), unreadForAdmin: true, unreadForUser: false });
  await setDoc(doc(db, 'support/royal1/messages/m1'), { from: 'user', text: 'Printer not printing', at: Timestamp.fromMillis(now - 60000) });
  await setDoc(doc(db, 'config/billing'), {
    companyName: 'Digital Target', phone: '+923451873354', bankAccountTitle: 'TEST ACCOUNT', bankName: 'Test Bank', accountNumber: '0000000000',
  });
});

await mkdir(SHOTS, { recursive: true });
const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1360, height: 900 }, acceptDownloads: true });
const page = await context.newPage();
const consoleErrors = [];
page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
page.on('pageerror', (e) => consoleErrors.push(e.message));
const badResponses = [];
page.on('response', (r) => { if (r.url().startsWith(`http://127.0.0.1:${PORT}`) && r.status() >= 400) badResponses.push(`${r.status()} ${r.url()}`); });
const base = `http://127.0.0.1:${PORT}`;
const step = (name) => console.log(`  ✓ ${name}`);

try {
  // The admin login is created once in Firebase console (Authentication → Add user).
  const signUp = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: 'owner@digitaltarget.test', password: 'Secret#2026', returnSecureToken: true }),
  });
  assert.equal(signUp.status, 200, await signUp.text());
  await page.goto(`${base}/index.html`);
  await page.getByText('Super Admin').waitFor();
  await page.fill('#email', 'owner@digitaltarget.test');
  await page.fill('#password', 'wrong-password');
  await page.click('#signin');
  await page.getByText('Email or password is incorrect.').waitFor();
  await page.fill('#email', 'owner@digitaltarget.test');
  await page.fill('#password', 'Secret#2026');
  await page.click('#signin');
  await page.getByText('Become Super Admin').waitFor();
  await page.click('#claim');
  step('email login: wrong password refused; the first login claims Super Admin with one click');

  await page.getByText('Waiting for approval').first().waitFor();
  await page.waitForSelector('.stat .value');
  const statValue = async (label) => page.locator('.stat', { hasText: label }).locator('.value').textContent();
  assert.equal(await statValue('Total salons'), '3');
  assert.equal(await statValue('Waiting for approval'), '1');
  assert.equal((await statValue('Expired')).trim(), '1');
  assert.equal(await statValue('New phone requests'), '1');
  assert.equal(await statValue("All salons' sales today"), 'Rs 12,500');
  await page.screenshot({ path: join(SHOTS, '1-dashboard.png'), fullPage: true });
  step('admin dashboard shows the right counts and today\'s sales of all salons');

  // A salon signs up while the dashboard is open: counts and the badge follow without reloading.
  await admin((db) => setDoc(doc(db, 'accounts/live1'), { uid: 'live1', email: 'live@gmail.com', salonName: 'Live Salon', status: 'PENDING', createdAt: Timestamp.now() }));
  await page.locator('.stat', { hasText: 'Waiting for approval' }).locator('.value', { hasText: /^2$/ }).waitFor();
  await page.locator('#pending-badge', { hasText: /^2$/ }).waitFor();
  await page.getByText('Live Salon').waitFor();
  await admin((db) => deleteDoc(doc(db, 'accounts/live1')));
  await page.locator('.stat', { hasText: 'Waiting for approval' }).locator('.value', { hasText: /^1$/ }).waitFor();
  step('live: a new sign-up shows on the open dashboard and the badge without reloading');

  await page.click('[data-approve="pending1"]');
  await page.selectOption('select[name=plan]', 'MONTHLY');
  await page.fill('input[name=monthlyFee]', '2000');
  await page.click('.modal button[type=submit]');
  await page.getByText('Account approved').waitFor();
  const approved = await admin(async (db) => (await getDoc(doc(db, 'accounts/pending1'))).data());
  assert.equal(approved.status, 'APPROVED');
  assert.equal(approved.customerId, 'DTC-0002');
  assert.match(approved.licenseId, /^DTL-[A-Z0-9]{4}-[A-Z0-9]{4}$/);
  assert.match(approved.businessId, /^DTB-[A-Z0-9]{8}$/);
  assert.equal(approved.monthlyFee, 2000);
  const days = Math.round((approved.expiresAt.toMillis() - now) / DAY);
  assert.ok(days >= 28 && days <= 32, `expiry in ${days} days`);
  step('approving a salon assigns IDs, plan, fee and expiry');

  await page.goto(`${base}/index.html#/salons?filter=DEVICE`);
  await page.locator('tr', { hasText: 'Royal Cuts' }).getByText('New phone').waitFor();
  await page.goto(`${base}/index.html#/salon/royal1`);
  await page.getByText('Infinix Hot 40').first().waitFor();
  await page.click('[data-act="approve-device"]');
  await page.click('.modal button[type=submit]');
  await page.getByText('Phone approved').waitFor();
  const moved = await admin(async (db) => (await getDoc(doc(db, 'accounts/royal1'))).data());
  assert.deepEqual(moved.deviceIds, ['a-old-phone-1111', 'a-new-phone-2222']);
  assert.equal(moved.deviceNames['a-new-phone-2222'], 'Infinix Hot 40');
  assert.equal(moved.pendingDeviceId, undefined);
  await page.locator('.device-list li').nth(1).waitFor();
  step('a new phone request is approved within the phone limit (2 phones listed)');

  const salesCard = page.locator('#sales');
  await salesCard.getByText('Rs 12,500').first().waitFor();
  await salesCard.getByText('Rs 9,900').first().waitFor();
  await salesCard.getByText('Rs 45,000').waitFor();
  await salesCard.locator('tbody tr').nth(1).waitFor();
  assert.equal(await salesCard.locator('tbody tr').count(), 2);
  await salesCard.screenshot({ path: join(SHOTS, '1b-salon-sales.png') });
  step('salon page shows today / yesterday / month sales and the daily table');

  // A third phone asks while the salon page is open: the request shows without reloading.
  await admin((db) => setDoc(doc(db, 'accounts/royal1'), { pendingDeviceId: 'a-third-phone-3333', pendingDeviceModel: 'Tecno Spark 20' }, { merge: true }));
  await page.locator('#device-request', { hasText: 'Tecno Spark 20' }).waitFor();
  await page.click('[data-act="dismiss-device"]');
  await page.getByText('Request ignored').waitFor();
  await page.locator('#device-request').waitFor({ state: 'detached' });
  step('live: a new phone request appears on the open salon page; Ignore removes it');

  await page.goto(`${base}/index.html#/phones`);
  await page.locator('tr', { hasText: 'Infinix Hot 40' }).waitFor();
  // The map library loads after the list is shown.
  await page.locator('.leaflet-marker-icon').first().waitFor();
  assert.equal(await page.locator('.leaflet-marker-icon').count(), 1);
  await page.locator('tr', { hasText: 'Samsung A15' }).getByText('Active now').waitFor();
  await page.locator('tr', { hasText: 'Infinix Hot 40' }).getByText('Location switched off').waitFor();
  await page.screenshot({ path: join(SHOTS, '1c-phones-map.png'), fullPage: true });
  await page.locator('tr', { hasText: 'Infinix Hot 40' }).locator('[data-block]').click();
  await page.click('.modal button[type=submit]');
  await page.getByText('Phone blocked').waitFor();
  const blockedNow = await admin(async (db) => (await getDoc(doc(db, 'accounts/royal1'))).data());
  assert.deepEqual(blockedNow.blockedDeviceIds, ['a-new-phone-2222']);
  await page.locator('tr', { hasText: 'Infinix Hot 40' }).getByText('Blocked').waitFor();
  step('phones page: map marker for the shared location, last seen, block a phone');

  assert.equal(await page.locator('tr', { hasText: 'Oppo A5' }).count(), 0);
  await page.getByText('1 phone report(s) of deleted salons are hidden').waitFor();
  await page.click('#clear-orphans');
  await page.click('.modal button[type=submit]');
  await page.getByText('Old phone reports deleted').waitFor();
  assert.equal((await admin(async (db) => getDoc(doc(db, 'devices/gone1__a-old-gone-9999')))).exists(), false);
  await page.locator('tr', { hasText: 'Infinix Hot 40' }).locator('[data-delete]').click();
  await page.click('.modal button[type=submit]');
  await page.getByText('Phone report deleted').waitFor();
  await page.locator('tr', { hasText: 'Infinix Hot 40' }).waitFor({ state: 'detached' });
  assert.equal((await admin(async (db) => getDoc(doc(db, 'devices/royal1__a-new-phone-2222')))).exists(), false);
  step('phones page: only registered salons; old reports of deleted salons and a single phone can be deleted');

  await page.goto(`${base}/index.html#/salons`);
  await page.fill('#q', 'roy');
  await page.getByText('Royal Cuts').first().waitFor();
  await page.fill('#q', 'DTC-0001');
  await page.locator('tr', { hasText: 'Royal Cuts' }).waitFor();
  await page.fill('#q', '0300-1234567');
  await page.locator('tr', { hasText: 'Royal Cuts' }).waitFor();
  step('search by name prefix, Customer ID and phone');

  await page.click('text=Licence ended');
  await page.locator('tr', { hasText: 'Classic Barber' }).waitFor();
  step('filter shows salons whose licence ended');

  await page.goto(`${base}/index.html#/reminders?group=LAPSED`);
  await page.locator('#list li', { hasText: 'Classic Barber' }).waitFor();
  assert.match(await page.inputValue('#msg'), /licence for \{salon\} ended/);
  await page.evaluate(() => { window.__opened = []; window.open = (url) => { window.__opened.push(url); }; });
  await page.click('#list [data-send]');
  const opened = await page.evaluate(() => window.__opened);
  assert.match(opened[0], /^https:\/\/wa\.me\/923211234567\?text=/);
  assert.match(decodeURIComponent(opened[0]), /Classic Barber/);
  await page.getByText('1 sent').waitFor();
  step('WhatsApp reminders for salons whose licence ended');

  await page.goto(`${base}/index.html#/salon/late1`);
  await page.getByRole('button', { name: 'Suspend' }).click();
  await page.fill('.modal textarea[name=message]', 'Please clear your dues.');
  await page.click('.modal button[type=submit]');
  await page.getByText('Status: Suspended').waitFor();
  const late = await admin(async (db) => (await getDoc(doc(db, 'accounts/late1'))).data());
  assert.equal(late.status, 'SUSPENDED');
  assert.equal(late.messageToUser, 'Please clear your dues.');
  step('suspending a salon with a message for the app');

  await page.goto(`${base}/index.html#/invoice/new?uid=royal1`);
  await page.waitForSelector('input[name=salonName]');
  assert.equal(await page.inputValue('input[name=salonName]'), 'Royal Cuts');
  assert.equal(await page.inputValue('#items [name=rate]'), '1500');
  await page.click('#save');
  await page.waitForURL(/#\/invoice\/[^/]+$/);
  await page.waitForSelector('.inv-a4');
  const invoices = await admin(async (db) => (await getDocs(collection(db, 'invoices'))).docs.map((d) => ({ id: d.id, ...d.data() })));
  assert.equal(invoices.length, 1);
  const invoice = invoices[0];
  assert.equal(invoice.number, `DT-INV-${new Date().getFullYear()}-0001`);
  assert.equal(invoice.total, 1500);
  assert.equal(invoice.status, 'UNPAID');
  const verify = await admin(async (db) => (await getDoc(doc(db, 'invoiceVerify', invoice.verifyToken))).data());
  assert.equal(verify.number, invoice.number);
  await page.screenshot({ path: join(SHOTS, '2-invoice-a4.png'), fullPage: true });
  step('invoice created with the next number and a verification record');

  const [download] = await Promise.all([page.waitForEvent('download'), page.click('#png')]);
  const a4File = join(SHOTS, download.suggestedFilename());
  await download.saveAs(a4File);
  assert.ok((await stat(a4File)).size > 30000, 'A4 PNG has content');
  await page.click('[data-d=receipt]');
  await page.waitForSelector('.inv-receipt');
  const [download2] = await Promise.all([page.waitForEvent('download'), page.click('#png')]);
  const receiptFile = join(SHOTS, download2.suggestedFilename());
  await download2.saveAs(receiptFile);
  assert.ok((await stat(receiptFile)).size > 15000, 'receipt PNG has content');
  step('A4 and receipt invoices export as PNG');

  await page.click('#pay');
  await page.click('.modal button[type=submit]');
  await page.getByText('Payment recorded').waitFor();
  const paidInvoice = await admin(async (db) => (await getDoc(doc(db, 'invoices', invoice.id))).data());
  assert.equal(paidInvoice.status, 'PAID');
  assert.equal(paidInvoice.balance, 0);
  const royal = await admin(async (db) => (await getDoc(doc(db, 'accounts/royal1'))).data());
  const extended = Math.round((royal.expiresAt.toMillis() - now) / DAY);
  assert.ok(extended >= 48 && extended <= 52, `licence extended to ${extended} days`);
  assert.equal(royal.paymentStatus, 'PAID');
  step('recording payment marks the invoice paid and extends the licence by the plan');

  const verifyPage = await context.newPage();
  await verifyPage.goto(`${base}/verify.html?t=${invoice.verifyToken}`);
  await verifyPage.getByText('Genuine invoice · PAID').waitFor();
  await verifyPage.screenshot({ path: join(SHOTS, '4-verify.png') });
  await verifyPage.goto(`${base}/verify.html?t=NOTAREALTOKEN1234`);
  await verifyPage.getByText('Not found').waitFor();
  step('public QR verification page');

  await page.goto(`${base}/index.html#/support?uid=royal1`);
  await page.locator('.bubble.from-user', { hasText: 'Printer not printing' }).waitFor();
  await page.fill('#reply textarea', 'Please turn the printer off and on, then press Test print.');
  await page.click('#reply button[type=submit]');
  await page.locator('.bubble.from-admin', { hasText: 'Test print' }).waitFor();
  // The bubble shows at once (local write); the server copy follows a moment later.
  let thread;
  for (let i = 0; i < 50 && thread?.lastFrom !== 'admin'; i += 1) {
    if (i) await page.waitForTimeout(200);
    thread = await admin(async (db) => (await getDoc(doc(db, 'support/royal1'))).data());
  }
  assert.equal(thread.lastFrom, 'admin');
  assert.equal(thread.unreadForUser, true);
  assert.equal(thread.unreadForAdmin, false);
  step('support inbox: salon message shown live, admin reply delivered');

  await page.goto(`${base}/index.html#/settings`);
  await page.fill('input[name=appName]', 'DT Salon Management');
  await page.fill('input[name=whatsapp]', '0345-1873354');
  await page.click('#form button[type=submit]');
  await page.getByText('Settings saved').waitFor();
  const branding = await admin(async (db) => (await getDoc(doc(db, 'config/branding'))).data());
  assert.equal(branding.whatsapp, '0345-1873354');
  await page.click('[data-t=app]');
  await page.fill('input[name=offlineGraceDays]', '15');
  await page.click('#form button[type=submit]');
  let appConfig;
  for (let i = 0; i < 50 && appConfig?.offlineGraceDays !== 15; i += 1) {
    await page.waitForTimeout(200);
    appConfig = await admin(async (db) => (await getDoc(doc(db, 'config/app'))).data());
  }
  assert.equal(appConfig?.offlineGraceDays, 15);
  step('branding and app control settings are saved');

  const mobile = await context.newPage();
  await mobile.setViewportSize({ width: 390, height: 844 });
  await mobile.goto(`${base}/index.html#/salons`);
  await mobile.getByText('Royal Cuts').first().waitFor();
  await mobile.screenshot({ path: join(SHOTS, '3-salons-mobile.png'), fullPage: true });
  step('mobile layout renders');
  await mobile.close();

  await page.goto(`${base}/index.html#/dashboard`);
  await page.reload();
  await page.locator('.stat', { hasText: 'Total salons' }).locator('.value').waitFor();
  assert.equal(await page.getByText('Checking admin access').count(), 0);
  step('returning admin: a reload opens the panel straight away (admin check in the background)');

  await Promise.all([page.waitForEvent('load'), page.click('#sign-out')]);
  await page.locator('#signin').waitFor();
  const leftovers = await page.evaluate(() => Object.keys(localStorage).filter((k) => k === 'dt-admin-verified' || k.startsWith('dt-admin-synced-')));
  assert.deepEqual(leftovers, []);
  step('sign out returns to the login and forgets this browser\'s cached admin data');

  // Network failures of third-party fonts are not app errors; our own files are checked below.
  const unexpected = consoleErrors.filter((e) => !/^Failed to load resource/.test(e));
  assert.deepEqual(unexpected, [], `console errors: ${unexpected.join(' | ')}`);
  assert.deepEqual(badResponses, [], `missing panel files: ${badResponses.join(' | ')}`);
  step('no JavaScript errors');
  console.log('PANEL E2E: ALL PASSED');
} catch (e) {
  await page.screenshot({ path: join(SHOTS, 'failure.png'), fullPage: true }).catch(() => {});
  console.error('PANEL E2E FAILED:', e);
  console.error('Console errors:', consoleErrors);
  process.exitCode = 1;
} finally {
  await browser.close();
  await env.cleanup();
  server.close();
}
