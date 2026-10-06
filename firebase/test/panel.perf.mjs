// Speed check of the Super Admin panel against the emulators, with a realistic mobile-data
// latency (150 ms round trip, 10 Mbit/s). Prints how long each screen takes to show its data.
// The panel is served with the cache headers of firebase.json over plain HTTP/1.1, which is
// slower than Firebase Hosting's HTTP/2 (the browser fetches at most 6 files at a time).
//   npm run perf:panel   (from the firebase/ folder); PERF_TRACE=1 also prints the reload's requests.
import { createHash } from 'node:crypto';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, setDoc, Timestamp, writeBatch } from 'firebase/firestore';
import { chromium } from 'playwright';

const PANEL = fileURLToPath(new URL('../../admin-panel/', import.meta.url));
const PORT = 5056;
const DAY = 86400000;
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png' };
const SALONS = Number(process.env.SALONS || 120);

const server = createServer(async (req, res) => {
  const path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (path === '/firebase-config.js') {
    res.writeHead(200, { 'content-type': 'text/javascript' });
    res.end(`window.DT_FIREBASE_CONFIG = { apiKey: 'demo-key', authDomain: 'demo-dt-salon.firebaseapp.com', projectId: 'demo-dt-salon', appId: 'demo' };
window.DT_USE_EMULATORS = true;`);
    return;
  }
  const file = normalize(join(PANEL, path === '/' ? 'index.html' : path));
  if (!file.startsWith(PANEL)) { res.writeHead(403); res.end(); return; }
  try {
    // Same caching as Firebase Hosting with firebase.json: code is re-validated (ETag),
    // images are kept for a day.
    const body = await readFile(file);
    const etag = `"${createHash('sha1').update(body).digest('hex')}"`;
    const cache = /\.(js|css|html|json)$/.test(file) ? 'no-cache' : 'public, max-age=86400';
    if (req.headers['if-none-match'] === etag) { res.writeHead(304, { etag, 'cache-control': cache }); res.end(); return; }
    res.writeHead(200, { 'content-type': TYPES[extname(file)] || 'application/octet-stream', etag, 'cache-control': cache });
    res.end(body);
  } catch {
    res.writeHead(404);
    res.end();
  }
});
await new Promise((r) => server.listen(PORT, '127.0.0.1', r));

const env = await initializeTestEnvironment({ projectId: 'demo-dt-salon', firestore: { host: '127.0.0.1', port: 8080 } });
await env.clearFirestore();
const now = Date.now();
const key = (ms) => { const d = new Date(ms); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  const statuses = ['APPROVED', 'APPROVED', 'APPROVED', 'PENDING', 'PAYMENT_PENDING', 'SUSPENDED', 'EXPIRED'];
  for (let start = 0; start < SALONS; start += 100) {
    const batch = writeBatch(db);
    for (let i = start; i < Math.min(SALONS, start + 100); i++) {
      const id = `salon${i}`;
      batch.set(doc(db, 'accounts', id), {
        uid: id, email: `salon${i}@gmail.com`, salonName: `Salon ${i}`, salonNameLower: `salon ${i}`, ownerName: `Owner ${i}`,
        ownerNameLower: `owner ${i}`, phone: `0300${String(1000000 + i)}`, phoneDigits: `92300${String(1000000 + i)}`, city: 'Lahore',
        status: statuses[i % statuses.length], plan: 'MONTHLY', monthlyFee: 1500, customerId: `DTC-${String(i).padStart(4, '0')}`,
        expiresAt: Timestamp.fromMillis(now + ((i % 40) - 10) * DAY), createdAt: Timestamp.fromMillis(now - i * 3600000),
        deviceIds: [`a-phone-${i}`], lastSeenAt: Timestamp.fromMillis(now - i * 60000),
      });
      batch.set(doc(db, 'stats', id), {
        uid: id, salonName: `Salon ${i}`, day: key(now), todaySalesMinor: 100000 * (i % 9), todayCustomers: i % 9,
        yesterdaySalesMinor: 90000, yesterdayCustomers: 7, monthKey: key(now).slice(0, 7), monthSalesMinor: 3000000, monthCustomers: 120,
        updatedAt: Timestamp.fromMillis(now),
      });
      batch.set(doc(db, 'devices', `${id}__a-phone-${i}`), {
        uid: id, deviceId: `a-phone-${i}`, model: 'Samsung A15', platform: 'android', osVersion: 'Android 14', appVersion: '2.1.1',
        locationPermission: 'granted', lastSeenAt: Timestamp.fromMillis(now - i * 60000), lat: 31.5 + i / 1000, lng: 74.3 + i / 1000, accuracyM: 30,
      });
    }
    await batch.commit();
  }
  for (let d = 0; d < 30; d++) {
    await setDoc(doc(db, `stats/salon0/days/${key(now - d * DAY)}`), { date: key(now - d * DAY), salesMinor: 1000000, customers: 12, services: 15, cashMinor: 700000, onlineMinor: 300000, creditMinor: 0, updatedAt: Timestamp.fromMillis(now) });
  }
  for (let i = 0; i < 40; i++) {
    await setDoc(doc(db, 'invoices', `inv${i}`), {
      number: `DT-INV-2026-${String(i).padStart(4, '0')}`, accountUid: `salon${i}`, salonName: `Salon ${i}`, total: 1500, paid: i % 2 ? 1500 : 0,
      balance: i % 2 ? 0 : 1500, status: i % 2 ? 'PAID' : 'UNPAID', issuedAt: Timestamp.fromMillis(now - i * DAY), items: [],
    });
  }
});

const signUp = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key', {
  method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ email: 'owner@digitaltarget.test', password: 'Secret#2026', returnSecureToken: true }),
});
const { localId } = await signUp.json();
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'admins', localId), { email: 'owner@digitaltarget.test' });
  await setDoc(doc(db, 'config', 'owner'), { uid: localId });
});

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1360, height: 900 } });
const page = await context.newPage();
const cdp = await context.newCDPSession(page);
await cdp.send('Network.enable');
await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 150, downloadThroughput: 1250000, uploadThroughput: 625000 });
const base = `http://127.0.0.1:${PORT}`;
const results = {};
const firstContent = {}; // shown separately, not added to the total
// A person looks at a screen before the next click; 1 s of "think time" (not counted).
const THINK_MS = Number(process.env.THINK_MS ?? 1000);
let stepStart = 0;
const time = async (name, action, ready) => {
  if (Object.keys(results).length) await page.waitForTimeout(THINK_MS);
  const t0 = Date.now();
  stepStart = t0;
  await action();
  await ready();
  results[name] = Date.now() - t0;
};
// Each step is timed until its data is on screen, checked on every frame (Playwright's
// waitFor slows down to 500 ms checks, which would blur the numbers).
const shows = (fn, arg) => () => page.waitForFunction(fn, arg, { polling: 'raf', timeout: 60000 });
const statReady = shows((n) => [...document.querySelectorAll('.stat')]
  .some((s) => s.textContent.includes('Total salons') && s.querySelector('.value')?.textContent.trim() === n), String(SALONS));
const salonRows = shows(() => document.querySelector('#rows tr[data-href^="#/salon/"]'));

try {
  await page.goto(`${base}/index.html`);
  await page.fill('#email', 'owner@digitaltarget.test');
  await page.fill('#password', 'Secret#2026');
  await time('login → dashboard', () => page.click('#signin'), statReady);
  // PERF_TRACE=1 prints the reload's network timeline (ms from the reload).
  const trace = [];
  let t0 = Date.now();
  if (process.env.PERF_TRACE) {
    const short = (r) => `${r.method()} ${r.url().replace(/\?.*$/, '').slice(0, 100)}`;
    page.on('request', (r) => trace.push(`${Date.now() - t0} start ${short(r)}`));
    page.on('requestfinished', async (r) => trace.push(`${Date.now() - t0} done  ${short(r)} ${(await r.response())?.status()}`));
    page.on('requestfailed', (r) => trace.push(`${Date.now() - t0} fail  ${short(r)}`));
  }
  t0 = Date.now();
  // Timed until the data shows (not the page's load event, which also waits for Google Fonts).
  await time('reload → dashboard (returning admin)', () => page.reload({ waitUntil: 'commit' }), statReady);
  trace.forEach((line) => console.log(line));
  await time('dashboard → salons list', () => page.click('[data-nav="salons"]'), salonRows);
  await time('salons → salon page (daily table)', () => page.goto(`${base}/index.html#/salon/salon0`), async () => {
    await shows(() => document.querySelector('.hero-head'))();
    firstContent['salons → salon page (details)'] = Date.now() - stepStart;
    await shows(() => document.querySelector('#sales tbody tr'))();
  });
  await time('salon → dashboard', () => page.click('[data-nav="dashboard"]'), statReady);
  await time('dashboard → phones + map', () => page.click('[data-nav="phones"]'), shows(() => document.querySelector('.leaflet-marker-icon')));
  await time('phones → invoices', () => page.click('[data-nav="invoices"]'), shows(() => document.querySelector('#content')?.textContent.includes('DT-INV-2026-0000')));
  await time('invoices → salons (again)', () => page.click('[data-nav="salons"]'), salonRows);
  console.log(JSON.stringify({ ...results, ...firstContent }, null, 2));
  console.log(`TOTAL ${Object.values(results).reduce((a, b) => a + b, 0)} ms for ${SALONS} salons`);
} finally {
  await browser.close();
  await env.cleanup();
  server.close();
}
