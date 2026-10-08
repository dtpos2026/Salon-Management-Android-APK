// All Firestore reads and writes of the Super Admin panel. Field names match the Android app
// (services/account) and firebase/firestore.rules.
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, orderBy, limit, startAfter,
  getDocs, getCountFromServer, runTransaction, serverTimestamp, Timestamp, deleteField, writeBatch,
} from '../vendor/firebase.js';
import { firebase } from './fb.js';
import { accountsStore, statsStore, devicesStore } from './store.js';
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

// Most account reads use the live copy (store.js) once it is ready and fall back to one-off
// queries when it is not available.

const time = (value) => toDate(value)?.getTime() ?? null;
const hasDeviceRequest = (a) => typeof a.pendingDeviceId === 'string' && a.pendingDeviceId > '';

function finishCounts(result) {
  // Approved accounts whose licence date has passed are effectively expired.
  result.active = result.APPROVED - result.lapsed;
  result.expiredTotal = result.EXPIRED + result.lapsed;
  return result;
}

/** The dashboard numbers from a list of accounts (same rules as the count queries below). */
export function countAccounts(list, now = new Date()) {
  const nowMs = now.getTime();
  const soonMs = addDays(now, 7).getTime();
  const result = { total: list.length, lapsed: 0, expiringSoon: 0, deviceRequests: 0 };
  STATUSES.forEach((s) => { result[s] = 0; });
  list.forEach((a) => {
    if (STATUSES.includes(a.status)) result[a.status] += 1;
    const ends = time(a.expiresAt);
    if (a.status === 'APPROVED' && ends !== null) {
      if (ends < nowMs) result.lapsed += 1;
      else if (ends <= soonMs) result.expiringSoon += 1;
    }
    if (hasDeviceRequest(a)) result.deviceRequests += 1;
  });
  return finishCounts(result);
}

async function countsFromServer() {
  const accounts = collection(db(), 'accounts');
  const now = Timestamp.fromDate(new Date());
  const soon = Timestamp.fromDate(addDays(new Date(), 7));
  const count = async (q) => (await getCountFromServer(q)).data().count;
  const [total, lapsed, expiringSoonCount, deviceRequests, ...byStatus] = await Promise.all([
    count(accounts),
    count(query(accounts, where('status', '==', 'APPROVED'), where('expiresAt', '<', now))),
    count(query(accounts, where('status', '==', 'APPROVED'), where('expiresAt', '>=', now), where('expiresAt', '<=', soon))),
    count(query(accounts, where('pendingDeviceId', '>', ''))).catch(() => 0),
    ...STATUSES.map((s) => count(query(accounts, where('status', '==', s)))),
  ]);
  const result = { total, lapsed, expiringSoon: expiringSoonCount, deviceRequests };
  STATUSES.forEach((s, i) => { result[s] = byStatus[i]; });
  return finishCounts(result);
}

export async function dashboardCounts() {
  if (await accountsStore.ready()) return countAccounts(accountsStore.list());
  return countsFromServer();
}

const newestFirst = (a, b) => (time(b.createdAt) ?? -Infinity) - (time(a.createdAt) ?? -Infinity);
const endsFirst = (a, b) => (time(a.expiresAt) ?? Infinity) - (time(b.expiresAt) ?? Infinity);

export async function recentPending(n = 6) {
  if (await accountsStore.ready()) {
    return accountsStore.list().filter((a) => a.status === 'PENDING').sort(newestFirst).slice(0, n);
  }
  const snap = await getDocs(query(collection(db(), 'accounts'), where('status', '==', 'PENDING'), orderBy('createdAt', 'desc'), limit(n)));
  return snap.docs.map(fromSnap);
}

export async function expiringSoon(n = 6) {
  const from = addDays(new Date(), -30);
  const soon = addDays(new Date(), 7);
  if (await accountsStore.ready()) {
    return accountsStore.list().filter((a) => {
      const ends = time(a.expiresAt);
      return a.status === 'APPROVED' && ends !== null && ends >= from.getTime() && ends <= soon.getTime();
    }).sort(endsFirst).slice(0, n);
  }
  const snap = await getDocs(query(collection(db(), 'accounts'), where('status', '==', 'APPROVED'),
    where('expiresAt', '>=', Timestamp.fromDate(from)), where('expiresAt', '<=', Timestamp.fromDate(soon)), orderBy('expiresAt', 'asc'), limit(n)));
  return snap.docs.map(fromSnap);
}

/** The accounts of one list filter, in the list's order, from the live copy. */
function filterAccounts(list, filter) {
  const now = Date.now();
  const soon = addDays(new Date(), 7).getTime();
  const approvedEnding = (a, test) => a.status === 'APPROVED' && time(a.expiresAt) !== null && test(time(a.expiresAt));
  switch (filter) {
    case 'DEVICE':
      return list.filter(hasDeviceRequest).sort((a, b) => a.pendingDeviceId.localeCompare(b.pendingDeviceId));
    case 'LAPSED':
      return list.filter((a) => approvedEnding(a, (t) => t < now)).sort(endsFirst);
    case 'SOON':
      return list.filter((a) => approvedEnding(a, (t) => t >= now && t <= soon)).sort(endsFirst);
    case 'ALL':
      return list.sort(newestFirst);
    default:
      return list.filter((a) => a.status === filter).sort(newestFirst);
  }
}

/**
 * One page of accounts. filter: ALL | a status | LAPSED (approved but past expiry) | SOON |
 * DEVICE (a new phone asks for approval). [after] is the previous page's [last].
 */
export async function listAccounts({ filter = 'ALL', after = null, pageSize = 20 } = {}) {
  if (await accountsStore.ready()) {
    const all = filterAccounts(accountsStore.list(), filter);
    const start = typeof after === 'number' ? after : 0;
    const items = all.slice(start, start + pageSize);
    return { items, last: start + items.length, more: start + items.length < all.length };
  }
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

/** Any part of the salon / owner name, city, email, phone, IDs or UID (live copy). */
function searchLive(q) {
  const lower = q.toLowerCase();
  const digits = q.replace(/\D/g, '');
  const phone = whatsappNumber(q);
  const fields = ['salonName', 'ownerName', 'city', 'email', 'customerId', 'licenseId', 'businessId', 'id'];
  return accountsStore.list().filter((a) => fields.some((f) => String(a[f] || '').toLowerCase().includes(lower)) ||
    (digits.length >= 4 && (String(a.phoneDigits || '').includes(digits) || String(a.phone || '').replace(/\D/g, '').includes(digits))) ||
    (phone && a.phoneDigits === phone)).sort(newestFirst).slice(0, 50);
}

/**
 * Any part of the name, email, phone or ID from the live copy; without it exact match on
 * email / IDs / phone and prefix match on salon and owner name.
 */
export async function searchAccounts(text) {
  const q = text.trim();
  if (!q) return [];
  if (await accountsStore.ready()) return searchLive(q);
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

/**
 * One account. From the live copy once the server confirmed it (so actions never work on an
 * old cached copy); otherwise read from the server. With [cached] a copy from this browser's
 * cache is fine too (the caller redraws when the live copy changes).
 */
export async function getAccount(uid, { cached = false } = {}) {
  if (accountsStore.isReady && (accountsStore.synced || cached) && accountsStore.get(uid)) return accountsStore.get(uid);
  return fromSnap(await getDoc(doc(db(), 'accounts', uid)));
}

/**
 * The salon account of [uid]; null only when the server confirms it does not exist, and
 * undefined when that is not known (offline or no answer), so nothing is treated as deleted by mistake.
 */
export async function findAccount(uid) {
  if (accountsStore.isReady && accountsStore.synced) return accountsStore.get(uid);
  try {
    const snap = await getDoc(doc(db(), 'accounts', uid));
    if (snap.exists()) return { id: snap.id, ...snap.data() };
    return snap.metadata.fromCache ? undefined : null;
  } catch (e) {
    return undefined;
  }
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

/** Removes an approved phone; it stops opening the app at its next check. Its map report goes too. */
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
  await deleteDeviceReport(`${account.id}__${id}`).catch(() => null);
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

/** Deletes the online account with its notes, shared sales totals, phone reports (map) and support chat. */
export async function deleteAccount(uid) {
  const phones = await devicesOf(uid).catch(() => []);
  await deleteDoc(doc(db(), 'accounts', uid));
  await Promise.all([
    deleteDoc(doc(db(), 'adminNotes', uid)).catch(() => null),
    deleteDoc(doc(db(), 'stats', uid)).catch(() => null),
    ...phones.map((d) => deleteDeviceReport(d.id).catch(() => null)),
    deleteSupportThread(uid).catch(() => null),
  ]);
}

/**
 * Deletes a salon's whole support conversation (every message and the conversation itself), so it
 * leaves the Support list for the admin and the salon. If the salon writes again, a new one starts.
 */
export async function deleteSupportThread(uid) {
  const snap = await getDocs(collection(db(), 'support', uid, 'messages'));
  for (let i = 0; i < snap.docs.length; i += 400) {
    const batch = writeBatch(db());
    snap.docs.slice(i, i + 400).forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
  await deleteDoc(doc(db(), 'support', uid));
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
  invoicesChanged();
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
  invoicesChanged();
  if (current.verifyToken) {
    await setDoc(doc(db(), 'invoiceVerify', current.verifyToken), verifyRecord({ ...merged, ...patch, issuedAt: patch.issuedAt || current.issuedAt }, billing?.companyName));
  }
}

export async function deleteInvoice(invoice) {
  await deleteDoc(doc(db(), 'invoices', invoice.id));
  invoicesChanged();
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

let revenueCache = null;

/** Invoices changed: the dashboard reloads its money totals next time. */
function invoicesChanged() {
  revenueCache = null;
}

/** This month's money totals; kept for a minute so going back to the dashboard is instant. */
export async function revenueSummary() {
  if (revenueCache && Date.now() - revenueCache.at < 60000) return revenueCache.value;
  const value = await loadRevenueSummary();
  revenueCache = { at: Date.now(), value };
  return value;
}

async function loadRevenueSummary() {
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
  if (await statsStore.ready()) return Object.fromEntries(statsStore.list().map((s) => [s.id, s]));
  const snap = await getDocs(collection(db(), 'stats'));
  const map = {};
  snap.forEach((d) => { map[d.id] = d.data(); });
  return map;
}

export async function salesStats(uid) {
  if (await statsStore.ready()) return statsStore.get(uid);
  const snap = await getDoc(doc(db(), 'stats', uid));
  return snap.exists() ? snap.data() : null;
}

/** The last [n] days a salon shared, newest first. */
export async function salesDays(uid, n = 31) {
  const snap = await getDocs(query(collection(db(), 'stats', uid, 'days'), orderBy('date', 'desc'), limit(n)));
  return snap.docs.map((d) => d.data());
}

// ---- Phones (status reported by the app, see DeviceMonitor) ------------------------------

/** Every phone that reported itself: model, versions, last seen and location. */
export async function listDevices() {
  if (await devicesStore.ready()) return devicesStore.list();
  const snap = await getDocs(collection(db(), 'devices'));
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

/** Accounts by uid (missing ones are left out). */
export async function listAccountsByIds(ids) {
  const out = {};
  if (await accountsStore.ready()) {
    ids.forEach((id) => { const a = accountsStore.get(id); if (a) out[id] = a; });
    return out;
  }
  await Promise.all(ids.map(async (id) => { const a = await getAccount(id).catch(() => null); if (a) out[id] = a; }));
  return out;
}

export async function devicesOf(uid) {
  if (await devicesStore.ready()) return devicesStore.list().filter((d) => d.uid === uid);
  const snap = await getDocs(query(collection(db(), 'devices'), where('uid', '==', uid)));
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

/**
 * Deletes a phone's report (model, last seen, location) from the Phones page and map. A phone
 * that is still approved and in use reports itself again at its next check.
 */
export async function deleteDeviceReport(id) {
  await deleteDoc(doc(db(), 'devices', id));
}

/** Blocks or unblocks one phone of an account. The app checks this list on the server copy. */
export async function setDeviceBlocked(account, deviceId, blocked) {
  const current = new Set(account.blockedDeviceIds || []);
  if (blocked) current.add(deviceId); else current.delete(deviceId);
  await updateDoc(doc(db(), 'accounts', account.id), { blockedDeviceIds: [...current], updatedAt: serverTimestamp() });
}

