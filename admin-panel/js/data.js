// All Firestore reads and writes of the Super Admin panel. Field names match the Android app
// (services/account) and firebase/firestore.rules.
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, orderBy, limit, startAfter,
  getDocs, getCountFromServer, runTransaction, serverTimestamp, Timestamp, deleteField, writeBatch,
} from '../vendor/firebase.js';
import { firebase } from './fb.js';
import { randomCode, addMonths, addDays, endOfDay, toDate, whatsappNumber } from './util.js';

const db = () => firebase().db;

export const STATUSES = ['PENDING', 'APPROVED', 'PAYMENT_PENDING', 'SUSPENDED', 'BLOCKED', 'EXPIRED', 'REJECTED'];

export const STATUS_LABELS = {
  PENDING: 'Pending approval',
  APPROVED: 'Active',
  PAYMENT_PENDING: 'Payment pending',
  SUSPENDED: 'Suspended',
  BLOCKED: 'Blocked',
  EXPIRED: 'Expired',
  REJECTED: 'Rejected',
};

export const PLANS = {
  TRIAL: { label: 'Trial (14 days)', days: 14 },
  MONTHLY: { label: 'Monthly', months: 1 },
  QUARTERLY: { label: '3 months', months: 3 },
  HALF_YEARLY: { label: '6 months', months: 6 },
  YEARLY: { label: 'Yearly', months: 12 },
  LIFETIME: { label: 'Lifetime', lifetime: true },
  CUSTOM: { label: 'Custom date' },
};

/** Licence end for a plan starting at [from]; null = no expiry. */
export function planExpiry(plan, from = new Date(), customDate = null) {
  const p = PLANS[plan];
  if (!p || p.lifetime) return null;
  if (plan === 'CUSTOM') return customDate;
  if (p.days) return endOfDay(addDays(from, p.days));
  return endOfDay(addMonths(from, p.months));
}

/** Status the salon actually sees (approved but past expiry = expired). */
export function effectiveStatus(account) {
  const expires = toDate(account.expiresAt);
  if (account.status === 'APPROVED' && expires && expires < new Date()) return 'EXPIRED';
  return account.status || 'PENDING';
}

function fromSnap(snap) {
  if (!snap.exists()) return null;
  return { id: snap.id, ...snap.data() };
}

// ------------------------------------------------------------------ admins

/** Whether the panel already has its first Super Admin (config/owner). */
export async function ownerClaimed() {
  return (await getDoc(doc(db(), 'config', 'owner'))).exists();
}

/** The first person to sign in after deployment becomes Super Admin (allowed once by the rules). */
export async function claimFirstAdmin(user) {
  const batch = writeBatch(db());
  batch.set(doc(db(), 'admins', user.uid), { email: user.email || '', addedAt: serverTimestamp(), addedBy: 'first-login' });
  batch.set(doc(db(), 'config', 'owner'), { uid: user.uid, email: user.email || '', claimedAt: serverTimestamp() });
  await batch.commit();
}

/** Admins created by hand before this feature: record the owner so nobody else can claim. */
export async function ensureOwnerRecorded(user) {
  try {
    if (!(await ownerClaimed())) {
      await setDoc(doc(db(), 'config', 'owner'), { uid: user.uid, email: user.email || '', claimedAt: serverTimestamp() });
    }
  } catch { /* best effort */ }
}

export async function isAdmin(uid) {
  try {
    return (await getDoc(doc(db(), 'admins', uid))).exists();
  } catch (e) {
    if (e.code === 'permission-denied') return false;
    throw e;
  }
}

export async function listAdmins() {
  const snap = await getDocs(collection(db(), 'admins'));
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

export async function addAdmin(uid, email, name, byUid) {
  await setDoc(doc(db(), 'admins', uid.trim()), { email: email.trim(), name: name.trim(), createdAt: serverTimestamp(), createdBy: byUid });
}

export async function removeAdmin(uid) {
  await deleteDoc(doc(db(), 'admins', uid));
}

// ------------------------------------------------------------------ accounts

export async function dashboardCounts() {
  const accounts = collection(db(), 'accounts');
  const now = Timestamp.fromDate(new Date());
  const soon = Timestamp.fromDate(addDays(new Date(), 7));
  const count = async (q) => (await getCountFromServer(q)).data().count;
  const [total, ...byStatus] = await Promise.all([
    count(accounts),
    ...STATUSES.map((s) => count(query(accounts, where('status', '==', s)))),
  ]);
  const [lapsed, expiringSoon] = await Promise.all([
    count(query(accounts, where('status', '==', 'APPROVED'), where('expiresAt', '<', now))),
    count(query(accounts, where('status', '==', 'APPROVED'), where('expiresAt', '>=', now), where('expiresAt', '<=', soon))),
  ]);
  const deviceRequests = await count(query(accounts, where('pendingDeviceId', '>', ''))).catch(() => 0);
  const result = { total, lapsed, expiringSoon, deviceRequests };
  STATUSES.forEach((s, i) => { result[s] = byStatus[i]; });
  // Approved accounts whose licence date has passed are effectively expired.
  result.active = result.APPROVED - lapsed;
  result.expiredTotal = result.EXPIRED + lapsed;
  return result;
}

export async function recentPending(n = 6) {
  const snap = await getDocs(query(collection(db(), 'accounts'), where('status', '==', 'PENDING'), orderBy('createdAt', 'desc'), limit(n)));
  return snap.docs.map(fromSnap);
}

export async function expiringSoon(n = 6) {
  const now = Timestamp.fromDate(addDays(new Date(), -30));
  const soon = Timestamp.fromDate(addDays(new Date(), 7));
  const snap = await getDocs(query(collection(db(), 'accounts'), where('status', '==', 'APPROVED'),
    where('expiresAt', '>=', now), where('expiresAt', '<=', soon), orderBy('expiresAt', 'asc'), limit(n)));
  return snap.docs.map(fromSnap);
}

/**
 * One page of accounts. filter: ALL | a status | LAPSED (approved but past expiry) | SOON |
 * DEVICE (a new phone asks for approval).
 */
export async function listAccounts({ filter = 'ALL', after = null, pageSize = 20 } = {}) {
  const accounts = collection(db(), 'accounts');
  const parts = [];
  if (filter === 'DEVICE') {
    parts.push(where('pendingDeviceId', '>', ''), orderBy('pendingDeviceId'));
  } else if (filter === 'LAPSED') {
    parts.push(where('status', '==', 'APPROVED'), where('expiresAt', '<', Timestamp.now()), orderBy('expiresAt', 'asc'));
  } else if (filter === 'SOON') {
    parts.push(where('status', '==', 'APPROVED'), where('expiresAt', '>=', Timestamp.now()),
      where('expiresAt', '<=', Timestamp.fromDate(addDays(new Date(), 7))), orderBy('expiresAt', 'asc'));
  } else {
    if (filter !== 'ALL') parts.push(where('status', '==', filter));
    parts.push(orderBy('createdAt', 'desc'));
  }
  if (after) parts.push(startAfter(after));
  parts.push(limit(pageSize));
  const snap = await getDocs(query(accounts, ...parts));
  return { items: snap.docs.map(fromSnap), last: snap.docs[snap.docs.length - 1] || null, more: snap.docs.length === pageSize };
}

/** Exact match on email / IDs / phone, prefix match on salon and owner name. */
export async function searchAccounts(text) {
  const q = text.trim();
  if (!q) return [];
  const accounts = collection(db(), 'accounts');
  const lower = q.toLowerCase();
  const queries = [
    query(accounts, where('email', '==', lower), limit(10)),
    query(accounts, where('customerId', '==', q.toUpperCase()), limit(10)),
    query(accounts, where('licenseId', '==', q.toUpperCase()), limit(10)),
    query(accounts, where('businessId', '==', q.toUpperCase()), limit(10)),
    query(accounts, orderBy('salonNameLower'), where('salonNameLower', '>=', lower), where('salonNameLower', '<=', `${lower}`), limit(10)),
    query(accounts, orderBy('ownerNameLower'), where('ownerNameLower', '>=', lower), where('ownerNameLower', '<=', `${lower}`), limit(10)),
  ];
  const phone = whatsappNumber(q);
  if (phone) queries.push(query(accounts, where('phoneDigits', '==', phone), limit(10)));
  if (/^[A-Za-z0-9]{20,}$/.test(q)) {
    const byUid = await getDoc(doc(db(), 'accounts', q));
    if (byUid.exists()) return [fromSnap(byUid)];
  }
  const results = await Promise.all(queries.map((x) => getDocs(x).catch(() => null)));
  const seen = new Map();
  results.filter(Boolean).forEach((snap) => snap.docs.forEach((d) => seen.set(d.id, fromSnap(d))));
  return [...seen.values()];
}

export async function getAccount(uid) {
  return fromSnap(await getDoc(doc(db(), 'accounts', uid)));
}

export async function getNotes(uid) {
  const snap = await getDoc(doc(db(), 'adminNotes', uid));
  return snap.exists() ? snap.data().notes || '' : '';
}

export async function saveNotes(uid, notes) {
  await setDoc(doc(db(), 'adminNotes', uid), { notes, updatedAt: serverTimestamp() });
}

/** Profile, plan and billing fields edited by the admin. */
export async function updateAccount(uid, patch) {
  const clean = { ...patch, updatedAt: serverTimestamp() };
  if ('salonName' in clean) clean.salonNameLower = String(clean.salonName).trim().toLowerCase();
  if ('ownerName' in clean) clean.ownerNameLower = String(clean.ownerName).trim().toLowerCase();
  if ('phone' in clean) clean.phoneDigits = whatsappNumber(clean.phone) || String(clean.phone).replace(/\D/g, '');
  if ('expiresAt' in clean) clean.expiresAt = clean.expiresAt ? Timestamp.fromDate(toDate(clean.expiresAt)) : null;
  if ('messageToUser' in clean && !clean.messageToUser) clean.messageToUser = deleteField();
  await updateDoc(doc(db(), 'accounts', uid), clean);
}

async function nextCustomerId() {
  return runTransaction(db(), async (tx) => {
    const ref = doc(db(), 'counters', 'customers');
    const snap = await tx.get(ref);
    const next = (snap.exists() ? snap.data().next : 1) || 1;
    tx.set(ref, { next: next + 1, updatedAt: serverTimestamp() });
    return `DTC-${String(next).padStart(4, '0')}`;
  });
}

/** Gives the account its Customer, Business and License IDs (once). */
export async function ensureIds(account) {
  const patch = {};
  if (!account.customerId) patch.customerId = await nextCustomerId();
  if (!account.businessId) patch.businessId = `DTB-${randomCode(8)}`;
  if (!account.licenseId) patch.licenseId = `DTL-${randomCode(4)}-${randomCode(4)}`;
  return patch;
}

export async function approveAccount(account, { plan, customDate, monthlyFee, message }) {
  const ids = await ensureIds(account);
  const expiresAt = planExpiry(plan, new Date(), customDate);
  await updateAccount(account.id, {
    ...ids,
    status: 'APPROVED',
    plan,
    expiresAt,
    monthlyFee: Number(monthlyFee) || 0,
    paymentStatus: account.paymentStatus || 'PENDING',
    approvedAt: serverTimestamp(),
    messageToUser: message || '',
  });
}

export async function setStatus(account, status, message) {
  const patch = { status, messageToUser: message || '' };
  if (status === 'APPROVED') Object.assign(patch, await ensureIds(account));
  await updateAccount(account.id, patch);
}

/** Phones approved for an account (older accounts only have the registering phone). */
export function approvedDevices(account) {
  if (Array.isArray(account.deviceIds) && account.deviceIds.length) return account.deviceIds;
  return account.deviceId ? [account.deviceId] : [];
}

export function deviceLimit(account) {
  return Math.max(1, Number(account.maxDevices) || 1);
}

export function deviceName(account, id) {
  return account.deviceNames?.[id] || (id === account.deviceId ? account.deviceModel : null) || 'Phone';
}

/**
 * Approves the phone that asked. With replace=true the other phones are removed (the account
 * moves to the new phone); otherwise it is added while under the limit.
 */
export async function approveDevice(account, { replace = false } = {}) {
  const id = account.pendingDeviceId;
  if (!id) return;
  const current = replace ? [] : approvedDevices(account).filter((d) => d !== id);
  if (current.length >= deviceLimit(account)) throw new Error(`This account may use ${deviceLimit(account)} phone(s). Increase the limit or remove a phone first.`);
  const names = replace ? {} : { ...(account.deviceNames || {}) };
  if (!replace && account.deviceId && !names[account.deviceId]) names[account.deviceId] = account.deviceModel || 'Phone';
  names[id] = account.pendingDeviceModel || 'Phone';
  await updateDoc(doc(db(), 'accounts', account.id), {
    deviceIds: [...current, id],
    deviceNames: names,
    deviceApprovedAt: serverTimestamp(),
    pendingDeviceId: deleteField(),
    pendingDeviceModel: deleteField(),
    pendingDeviceAt: deleteField(),
    updatedAt: serverTimestamp(),
  });
}

/** Removes an approved phone; it stops opening the app at its next check. */
export async function removeDevice(account, id) {
  const remaining = approvedDevices(account).filter((d) => d !== id);
  const names = { ...(account.deviceNames || {}) };
  delete names[id];
  await updateDoc(doc(db(), 'accounts', account.id), {
    // An empty list would mean "not bound"; keep a placeholder so no phone matches.
    deviceIds: remaining.length ? remaining : ['none'],
    deviceNames: names,
    updatedAt: serverTimestamp(),
  });
}

export async function setDeviceLimit(account, max) {
  await updateDoc(doc(db(), 'accounts', account.id), { maxDevices: Math.min(20, Math.max(1, Math.round(max) || 1)), updatedAt: serverTimestamp() });
}

/** Ignores a new-phone request; the account stays on its current phone. */
export async function dismissDeviceRequest(account) {
  await updateDoc(doc(db(), 'accounts', account.id), {
    pendingDeviceId: deleteField(),
    pendingDeviceModel: deleteField(),
    pendingDeviceAt: deleteField(),
    updatedAt: serverTimestamp(),
  });
}

export async function deleteAccount(uid) {
  await deleteDoc(doc(db(), 'accounts', uid));
  await deleteDoc(doc(db(), 'adminNotes', uid)).catch(() => null);
}

// ------------------------------------------------------------------ invoices

export async function listInvoices({ status = 'ALL', accountUid = null, after = null, pageSize = 20 } = {}) {
  const parts = [];
  if (accountUid) parts.push(where('accountUid', '==', accountUid));
  else if (status !== 'ALL') parts.push(where('status', '==', status));
  parts.push(orderBy('issuedAt', 'desc'));
  if (after) parts.push(startAfter(after));
  parts.push(limit(pageSize));
  const snap = await getDocs(query(collection(db(), 'invoices'), ...parts));
  return { items: snap.docs.map(fromSnap), last: snap.docs[snap.docs.length - 1] || null, more: snap.docs.length === pageSize };
}

export async function getInvoice(id) {
  return fromSnap(await getDoc(doc(db(), 'invoices', id)));
}

export function invoiceTotals(items, discount, paid) {
  const subtotal = items.reduce((sum, it) => sum + (Number(it.qty) || 0) * (Number(it.rate) || 0), 0);
  const total = Math.max(0, subtotal - (Number(discount) || 0));
  const paidAmount = Math.min(Math.max(0, Number(paid) || 0), total);
  const balance = total - paidAmount;
  const status = balance <= 0 ? 'PAID' : paidAmount > 0 ? 'PARTIAL' : 'UNPAID';
  return { subtotal, total, paid: paidAmount, balance, status };
}

function verifyRecord(inv, company) {
  return {
    number: inv.number,
    salonName: inv.salonName || '',
    total: inv.total,
    paid: inv.paid,
    balance: inv.balance,
    status: inv.status,
    issuedAt: inv.issuedAt,
    company: company || '',
    updatedAt: serverTimestamp(),
  };
}

/** Creates an invoice with the next number for its year (e.g. DT-INV-2026-0002). */
export async function createInvoice(data, billing, adminUid) {
  const issued = toDate(data.issuedAt) || new Date();
  const year = issued.getFullYear();
  const prefix = (billing?.invoicePrefix || 'DT-INV').trim();
  const token = randomCode(24);
  const totals = invoiceTotals(data.items, data.discount, data.paid);
  const id = await runTransaction(db(), async (tx) => {
    const counterRef = doc(db(), 'counters', `invoices-${year}`);
    const counter = await tx.get(counterRef);
    const next = (counter.exists() ? counter.data().next : 1) || 1;
    const number = `${prefix}-${year}-${String(next).padStart(4, '0')}`;
    const ref = doc(collection(db(), 'invoices'));
    const invoice = {
      ...data,
      ...totals,
      number,
      issuedAt: Timestamp.fromDate(issued),
      periodFrom: data.periodFrom ? Timestamp.fromDate(toDate(data.periodFrom)) : null,
      periodTo: data.periodTo ? Timestamp.fromDate(toDate(data.periodTo)) : null,
      verifyToken: token,
      createdAt: serverTimestamp(),
      createdBy: adminUid,
    };
    tx.set(counterRef, { next: next + 1, updatedAt: serverTimestamp() });
    tx.set(ref, invoice);
    tx.set(doc(db(), 'invoiceVerify', token), verifyRecord(invoice, billing?.companyName));
    return ref.id;
  });
  return id;
}

export async function updateInvoice(id, data, billing) {
  const current = await getInvoice(id);
  const merged = { ...current, ...data };
  const totals = invoiceTotals(merged.items || [], merged.discount, merged.paid);
  const patch = {
    ...data,
    ...totals,
    updatedAt: serverTimestamp(),
  };
  if ('issuedAt' in data) patch.issuedAt = Timestamp.fromDate(toDate(data.issuedAt));
  if ('periodFrom' in data) patch.periodFrom = data.periodFrom ? Timestamp.fromDate(toDate(data.periodFrom)) : null;
  if ('periodTo' in data) patch.periodTo = data.periodTo ? Timestamp.fromDate(toDate(data.periodTo)) : null;
  await updateDoc(doc(db(), 'invoices', id), patch);
  if (current.verifyToken) {
    await setDoc(doc(db(), 'invoiceVerify', current.verifyToken), verifyRecord({ ...merged, ...patch, issuedAt: patch.issuedAt || current.issuedAt }, billing?.companyName));
  }
}

export async function deleteInvoice(invoice) {
  await deleteDoc(doc(db(), 'invoices', invoice.id));
  if (invoice.verifyToken) await deleteDoc(doc(db(), 'invoiceVerify', invoice.verifyToken)).catch(() => null);
}

/**
 * Records a payment on an invoice and, optionally, extends the salon's licence by its plan
 * (from today or from the current expiry, whichever is later) and re-activates the account.
 */
export async function recordPayment(invoice, { amount, method, extend, billing }) {
  const paid = (Number(invoice.paid) || 0) + (Number(amount) || 0);
  await updateInvoice(invoice.id, { paid, paymentMethod: method || invoice.paymentMethod || '', paidAt: Timestamp.now() }, billing);
  if (!invoice.accountUid) return;
  const account = await getAccount(invoice.accountUid);
  if (!account) return;
  const updated = await getInvoice(invoice.id);
  const patch = {
    lastPaymentAt: Timestamp.now(),
    paymentStatus: updated.balance > 0 ? 'PARTIAL' : 'PAID',
    pendingAmount: Math.max(0, (Number(account.pendingAmount) || 0) - (Number(amount) || 0)),
  };
  if (extend && account.plan && PLANS[account.plan] && !PLANS[account.plan].lifetime && account.plan !== 'CUSTOM') {
    const current = toDate(account.expiresAt);
    const from = current && current > new Date() ? current : new Date();
    patch.expiresAt = planExpiry(account.plan, from);
  }
  if (extend && ['PAYMENT_PENDING', 'EXPIRED'].includes(account.status)) {
    patch.status = 'APPROVED';
    patch.messageToUser = '';
  }
  await updateAccount(account.id, patch);
}

export async function revenueSummary() {
  const start = new Date();
  start.setDate(1);
  start.setHours(0, 0, 0, 0);
  const [month, unpaid, partial] = await Promise.all([
    getDocs(query(collection(db(), 'invoices'), where('issuedAt', '>=', Timestamp.fromDate(start)))),
    getDocs(query(collection(db(), 'invoices'), where('status', '==', 'UNPAID'))),
    getDocs(query(collection(db(), 'invoices'), where('status', '==', 'PARTIAL'))),
  ]);
  const sum = (snap, key) => snap.docs.reduce((s, d) => s + (Number(d.data()[key]) || 0), 0);
  return {
    collectedThisMonth: sum(month, 'paid'),
    invoicedThisMonth: sum(month, 'total'),
    outstanding: sum(unpaid, 'balance') + sum(partial, 'balance'),
    unpaidCount: unpaid.size + partial.size,
  };
}

// ------------------------------------------------------------------ config

export const CONFIG_DEFAULTS = {
  ai: { enabled: false, supportAutoReply: true, businessAssistant: true, dailyLimitPerSalon: 30, extraInstructions: '' },
  branding: {
    appName: 'DT Salon Management', companyName: 'Digital Target', contactNumber: '', whatsapp: '',
    email: '', website: '', supportText: '', logoDataUrl: '',
  },
  billing: {
    companyName: 'Digital Target', tagline: 'AI Software • Digital Marketing • Social Media', phone: '', address: '',
    bankAccountTitle: '', bankName: '', accountNumber: '', iban: '', paymentQrDataUrl: '', signatureDataUrl: '',
    signatoryName: '', signatoryTitle: 'Authorized Signatory', invoicePrefix: 'DT-INV',
    terms: 'Payment due within 7 days of invoice date.\nWork starts after advance payment confirmation.',
    thankYou: 'Shukriya! Thank you', footerNote: 'Thank you for your business!', logoDataUrl: '', defaultService: 'DT Salon Management subscription',
  },
  app: {
    latestVersionCode: 0, latestVersionName: '', minVersionCode: 0, updateUrl: '', updateMessage: '',
    notice: '', offlineGraceDays: 7,
  },
};

export async function getConfig(name) {
  const snap = await getDoc(doc(db(), 'config', name));
  return { ...CONFIG_DEFAULTS[name], ...(snap.exists() ? snap.data() : {}) };
}

export async function saveConfig(name, data) {
  await setDoc(doc(db(), 'config', name), { ...data, updatedAt: serverTimestamp() }, { merge: true });
}

export async function getVerification(token) {
  return fromSnap(await getDoc(doc(db(), 'invoiceVerify', token)));
}

// ---- Sales totals shared by the salons (numbers only; see SalesSync in the app) ------------

function dayKey(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Today / yesterday / this month for one salon's stats document (stale days count as 0). */
export function salesSummary(stats, now = new Date()) {
  const today = dayKey(now);
  const yesterday = dayKey(addDays(now, -1));
  const month = today.slice(0, 7);
  if (!stats) return { today: 0, todayCustomers: 0, yesterday: 0, yesterdayCustomers: 0, month: 0, monthCustomers: 0, updatedAt: null };
  const rupees = (minor) => (Number(minor) || 0) / 100;
  let t = 0; let tc = 0; let y = 0; let yc = 0;
  if (stats.day === today) {
    t = rupees(stats.todaySalesMinor); tc = stats.todayCustomers || 0;
    y = rupees(stats.yesterdaySalesMinor); yc = stats.yesterdayCustomers || 0;
  } else if (stats.day === yesterday) {
    y = rupees(stats.todaySalesMinor); yc = stats.todayCustomers || 0;
  }
  const m = stats.monthKey === month ? rupees(stats.monthSalesMinor) : 0;
  const mc = stats.monthKey === month ? (stats.monthCustomers || 0) : 0;
  return { today: t, todayCustomers: tc, yesterday: y, yesterdayCustomers: yc, month: m, monthCustomers: mc, updatedAt: stats.updatedAt || null };
}

/** Every salon's latest shared totals, by account uid. */
export async function salesStatsMap() {
  const snap = await getDocs(collection(db(), 'stats'));
  const map = {};
  snap.forEach((d) => { map[d.id] = d.data(); });
  return map;
}

export async function salesStats(uid) {
  const snap = await getDoc(doc(db(), 'stats', uid));
  return snap.exists() ? snap.data() : null;
}

/** The last [n] days a salon shared, newest first. */
export async function salesDays(uid, n = 31) {
  const snap = await getDocs(query(collection(db(), 'stats', uid, 'days'), orderBy('date', 'desc'), limit(n)));
  return snap.docs.map((d) => d.data());
}

// ---- Phones (status reported by the app, see DeviceMonitor) ------------------------------

/** Every phone that reported itself: model, versions, last seen and (when shared) location. */
export async function listDevices() {
  const snap = await getDocs(collection(db(), 'devices'));
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

/** Accounts by uid (missing ones are left out). */
export async function listAccountsByIds(ids) {
  const out = {};
  await Promise.all(ids.map(async (id) => { const a = await getAccount(id).catch(() => null); if (a) out[id] = a; }));
  return out;
}

export async function devicesOf(uid) {
  const snap = await getDocs(query(collection(db(), 'devices'), where('uid', '==', uid)));
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

/** Blocks or unblocks one phone of an account. The app checks this list on the server copy. */
export async function setDeviceBlocked(account, deviceId, blocked) {
  const current = new Set(account.blockedDeviceIds || []);
  if (blocked) current.add(deviceId); else current.delete(deviceId);
  await updateDoc(doc(db(), 'accounts', account.id), { blockedDeviceIds: [...current], updatedAt: serverTimestamp() });
}

