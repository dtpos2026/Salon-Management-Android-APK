// Security rules tests (Firestore emulator): run with `npm test` in this folder.
import { readFileSync } from 'node:fs';
import { test, before, after, beforeEach } from 'node:test';
import {
  initializeTestEnvironment, assertFails, assertSucceeds,
} from '@firebase/rules-unit-testing';
import {
  doc, getDoc, setDoc, updateDoc, writeBatch, collection, getDocs, query, where, serverTimestamp, deleteDoc,
} from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-dt-salon',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'admins/boss'), { email: 'boss@gmail.com' });
    await setDoc(doc(db, 'config/branding'), { appName: 'DT Salon Management' });
    await setDoc(doc(db, 'config/app'), { minVersionCode: 1 });
    await setDoc(doc(db, 'config/billing'), { accountNumber: '0030-0110125578' });
    await setDoc(doc(db, 'accounts/other'), { uid: 'other', status: 'APPROVED', email: 'other@gmail.com' });
    await setDoc(doc(db, 'invoices/inv1'), { accountUid: 'salon1', total: 4000 });
    await setDoc(doc(db, 'invoices/inv2'), { accountUid: 'other', total: 7000 });
    await setDoc(doc(db, 'invoiceVerify/tok123'), { number: 'DT-INV-2026-0001', status: 'PAID' });
  });
});

const salon = () => env.authenticatedContext('salon1', { email: 'salon1@gmail.com', email_verified: false }).firestore();
const admin = () => env.authenticatedContext('boss', { email: 'boss@gmail.com', email_verified: true }).firestore();
const anon = () => env.unauthenticatedContext().firestore();

const registration = (extra = {}) => ({
  uid: 'salon1', email: 'salon1@gmail.com', displayName: 'Ali', photoUrl: null,
  salonName: 'Royal Cuts', ownerName: 'Ali', phone: '03001234567', city: 'Burewala', address: 'Main Bazar',
  salonNameLower: 'royal cuts', ownerNameLower: 'ali', phoneDigits: '923001234567',
  status: 'PENDING', platform: 'android', appVersion: '2.0.0', deviceId: 'dev-aaaa-1111', deviceModel: 'Test',
  createdAt: serverTimestamp(), lastSeenAt: serverTimestamp(), ...extra,
});

test('public config is readable before login, billing is not', async () => {
  await assertSucceeds(getDoc(doc(anon(), 'config/branding')));
  await assertSucceeds(getDoc(doc(anon(), 'config/app')));
  await assertFails(getDoc(doc(anon(), 'config/billing')));
  await assertFails(getDoc(doc(salon(), 'config/billing')));
  await assertFails(setDoc(doc(salon(), 'config/branding'), { appName: 'Hacked' }));
});

test('a salon registers itself only as PENDING', async () => {
  await assertSucceeds(setDoc(doc(salon(), 'accounts/salon1'), registration()));
});

test('a salon cannot approve itself or set admin fields', async () => {
  await assertFails(setDoc(doc(salon(), 'accounts/salon1'), registration({ status: 'APPROVED' })));
  await assertFails(setDoc(doc(salon(), 'accounts/salon1'), registration({ licenseId: 'DTL-FAKE' })));
  await assertFails(setDoc(doc(salon(), 'accounts/salon1'), registration({ expiresAt: new Date(2099, 1, 1) })));
  await assertFails(setDoc(doc(salon(), 'accounts/salon1'), registration({ email: 'someone@gmail.com' })));
  await assertFails(setDoc(doc(salon(), 'accounts/other2'), registration({ uid: 'other2' })));
  await assertFails(setDoc(doc(anon(), 'accounts/salon1'), registration()));
});

test('a salon updates only device fields of its own account', async () => {
  await assertSucceeds(setDoc(doc(salon(), 'accounts/salon1'), registration()));
  await assertSucceeds(updateDoc(doc(salon(), 'accounts/salon1'), {
    lastSeenAt: serverTimestamp(), appVersion: '2.0.1', deviceModel: 'Pixel',
  }));
  await assertFails(updateDoc(doc(salon(), 'accounts/salon1'), { status: 'APPROVED' }));
  await assertFails(updateDoc(doc(salon(), 'accounts/salon1'), { lastSeenAt: serverTimestamp(), expiresAt: new Date(2099, 1, 1) }));
  await assertFails(deleteDoc(doc(salon(), 'accounts/salon1')));
});

test('a new phone can only be requested; the admin approves it', async () => {
  await assertSucceeds(setDoc(doc(salon(), 'accounts/salon1'), registration()));
  await assertFails(setDoc(doc(salon(), 'accounts/salon1'), registration({ deviceId: 'x' })));
  await assertSucceeds(updateDoc(doc(salon(), 'accounts/salon1'), {
    pendingDeviceId: 'dev-bbbb-2222', pendingDeviceModel: 'Samsung A15',
    pendingDeviceAt: serverTimestamp(), lastSeenAt: serverTimestamp(),
  }));
  // The salon cannot approve the phone itself.
  await assertFails(updateDoc(doc(salon(), 'accounts/salon1'), { deviceId: 'dev-bbbb-2222', lastSeenAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(salon(), 'accounts/salon1'), {
    pendingDeviceId: 'dev-cccc-3333', pendingDeviceAt: new Date(2099, 1, 1), lastSeenAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(doc(salon(), 'accounts/salon1'), { deviceIds: ['dev-bbbb-2222'], maxDevices: 5, lastSeenAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(admin(), 'accounts/salon1'), {
    deviceIds: ['dev-aaaa-1111', 'dev-bbbb-2222'], maxDevices: 2, pendingDeviceId: null, pendingDeviceModel: null, pendingDeviceAt: null,
  }));
});

test('support chat: a salon talks only in its own thread', async () => {
  const db = salon();
  const thread = { uid: 'salon1', salonName: 'Royal Cuts', email: 'salon1@gmail.com', lastMessage: 'Printer help', lastFrom: 'user', lastAt: serverTimestamp(), unreadForAdmin: true, unreadForUser: false };
  await assertSucceeds(setDoc(doc(db, 'support/salon1'), thread));
  await assertSucceeds(setDoc(doc(db, 'support/salon1/messages/m1'), { from: 'user', text: 'Printer help', at: serverTimestamp() }));
  await assertFails(setDoc(doc(db, 'support/salon1/messages/m2'), { from: 'admin', text: 'Fake reply', at: serverTimestamp() }));
  await assertFails(setDoc(doc(db, 'support/other/messages/m1'), { from: 'user', text: 'Hi', at: serverTimestamp() }));
  await assertFails(getDoc(doc(db, 'support/other')));
  await assertFails(getDocs(collection(db, 'support')));
  await assertSucceeds(getDocs(collection(db, 'support/salon1/messages')));
  await assertSucceeds(setDoc(doc(admin(), 'support/salon1/messages/m3'), { from: 'admin', text: 'Restart the printer', at: serverTimestamp() }));
  await assertSucceeds(getDocs(collection(admin(), 'support')));
});

test('salons never see other salons', async () => {
  await assertFails(getDoc(doc(salon(), 'accounts/other')));
  await assertFails(getDocs(collection(salon(), 'accounts')));
  await assertFails(getDoc(doc(salon(), 'invoices/inv2')));
  await assertSucceeds(getDocs(query(collection(salon(), 'invoices'), where('accountUid', '==', 'salon1'))));
  await assertFails(getDocs(collection(salon(), 'invoices')));
  await assertFails(getDoc(doc(salon(), 'adminNotes/other')));
});

test('the first admin claims the panel once; later nobody can', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => deleteDoc(doc(ctx.firestore(), 'admins/boss')));
  const first = env.authenticatedContext('owner1', { email: 'owner@dt.test' }).firestore();
  const claim = (db, uid) => {
    const batch = writeBatch(db);
    batch.set(doc(db, `admins/${uid}`), { email: `${uid}@dt.test` });
    batch.set(doc(db, 'config/owner'), { uid, email: `${uid}@dt.test`, claimedAt: serverTimestamp() });
    return batch.commit();
  };
  await assertFails(setDoc(doc(first, 'admins/owner1'), { email: 'owner@dt.test' }));
  await assertSucceeds(claim(first, 'owner1'));
  await assertSucceeds(getDocs(collection(first, 'accounts')));
  const late = env.authenticatedContext('late1', { email: 'late@dt.test' }).firestore();
  await assertFails(claim(late, 'late1'));
});

test('nobody can make themselves an admin', async () => {
  await assertFails(setDoc(doc(salon(), 'admins/salon1'), { email: 'salon1@gmail.com' }));
  await assertSucceeds(getDoc(doc(salon(), 'admins/salon1')));
  await assertFails(getDoc(doc(salon(), 'admins/boss')));
});

test('admins manage accounts, invoices, counters and settings', async () => {
  const db = admin();
  await assertSucceeds(getDocs(collection(db, 'accounts')));
  await assertSucceeds(updateDoc(doc(db, 'accounts/other'), { status: 'SUSPENDED', messageToUser: 'Please pay' }));
  await assertSucceeds(setDoc(doc(db, 'counters/customers'), { next: 2 }));
  await assertSucceeds(setDoc(doc(db, 'invoices/inv3'), { accountUid: 'other', total: 1000 }));
  await assertSucceeds(setDoc(doc(db, 'config/billing'), { bankName: 'Meezan Bank' }));
  await assertSucceeds(setDoc(doc(db, 'adminNotes/other'), { notes: 'Pays late' }));
  await assertSucceeds(setDoc(doc(db, 'admins/helper'), { email: 'helper@gmail.com' }));
});

test('invoice verification is public by id but cannot be listed', async () => {
  await assertSucceeds(getDoc(doc(anon(), 'invoiceVerify/tok123')));
  await assertFails(getDocs(collection(anon(), 'invoiceVerify')));
  await assertFails(setDoc(doc(anon(), 'invoiceVerify/tok999'), { status: 'PAID' }));
});

test('daily sales totals: a salon writes only its own numbers, the admin reads them', async () => {
  const day = {
    date: '2026-10-05', salesMinor: 1250000, customers: 18, services: 25,
    cashMinor: 900000, onlineMinor: 250000, creditMinor: 100000, updatedAt: serverTimestamp(),
  };
  const summary = {
    uid: 'salon1', salonName: 'Royal Cuts', currency: 'PKR', day: '2026-10-05',
    todaySalesMinor: 1250000, todayCustomers: 18, yesterdaySalesMinor: 990000, yesterdayCustomers: 14,
    monthKey: '2026-10', monthSalesMinor: 5000000, monthCustomers: 70, appVersion: '2.1.0', updatedAt: serverTimestamp(),
  };
  await assertSucceeds(setDoc(doc(salon(), 'stats/salon1/days/2026-10-05'), day));
  await assertSucceeds(setDoc(doc(salon(), 'stats/salon1'), summary, { merge: true }));
  // Only numbers, only its own document, only a proper day id.
  await assertFails(setDoc(doc(salon(), 'stats/salon1/days/2026-10-05'), { ...day, customerName: 'Ali' }));
  await assertFails(setDoc(doc(salon(), 'stats/salon1/days/2026-10-05'), { ...day, salesMinor: -5 }));
  await assertFails(setDoc(doc(salon(), 'stats/salon1/days/latest'), { ...day, date: 'latest' }));
  await assertFails(setDoc(doc(salon(), 'stats/other'), { ...summary, uid: 'other' }));
  await assertFails(setDoc(doc(salon(), 'stats/salon1'), { ...summary, uid: 'other' }));
  await assertFails(getDocs(collection(salon(), 'stats')));
  await assertFails(getDoc(doc(salon(), 'stats/other')));
  // The Super Admin sees every salon's totals.
  await assertSucceeds(getDocs(collection(admin(), 'stats')));
  await assertSucceeds(getDocs(collection(admin(), 'stats/salon1/days')));
  await assertFails(setDoc(doc(admin(), 'stats/salon1/days/2026-10-06'), { ...day, date: '2026-10-06' }));
});
