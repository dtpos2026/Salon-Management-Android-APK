// Live copies of the collections most pages show: accounts, the sales totals the salons share
// (stats) and the phones' reports (devices). One listener each, started once after the admin
// check. With the browser cache (fb.js) a returning admin sees the last known data at once and
// Firestore only streams what changed since; pages re-draw when something changes.
import { collection, onSnapshot } from '../vendor/firebase.js';
import { firebase } from './fb.js';

const SYNCED_PREFIX = 'dt-admin-synced-';

/**
 * Whether this browser's cache already holds a full copy of the collection (it was synced
 * from the server before). Until then a cached result may be partial and is not shown.
 */
function cacheComplete(name, value) {
  const key = `${SYNCED_PREFIX}${firebase().projectId}-${name}`;
  try {
    if (value === undefined) return localStorage.getItem(key) === '1';
    localStorage.setItem(key, '1');
  } catch (e) { /* private mode: wait for the server */ }
  return false;
}

/** Forgets that the cache was complete (after the cache is cleared on sign-out). */
export function forgetCachedCollections() {
  try {
    Object.keys(localStorage).filter((k) => k.startsWith(SYNCED_PREFIX)).forEach((k) => localStorage.removeItem(k));
  } catch (e) { /* ignore */ }
}

class LiveCollection {
  constructor(name) {
    this.name = name;
    this.docs = new Map();
    this.subscribers = new Set();
    this.unsubscribe = null;
    this.state = 'idle'; // idle | loading | ready | failed
    this.synced = false; // true once the server confirmed the data (not only the cache)
    this.version = 0;
    this.readyPromise = null;
    this.timer = null;
    this.offlineTimer = null;
  }

  start() {
    if (this.unsubscribe) return;
    this.state = 'loading';
    this.readyPromise = new Promise((resolve) => {
      const becomeReady = () => {
        clearTimeout(this.offlineTimer);
        if (this.state === 'ready') return;
        this.state = 'ready';
        resolve(true);
      };
      this.unsubscribe = onSnapshot(collection(firebase().db, this.name), { includeMetadataChanges: true }, (snap) => {
        const changes = snap.docChanges();
        changes.forEach((c) => {
          if (c.type === 'removed') this.docs.delete(c.doc.id);
          else this.docs.set(c.doc.id, { id: c.doc.id, ...c.doc.data() });
        });
        const wasSynced = this.synced;
        this.synced = !snap.metadata.fromCache;
        if (this.synced && !wasSynced) cacheComplete(this.name, true);
        if (this.state !== 'ready') {
          if (this.synced || cacheComplete(this.name)) {
            becomeReady();
          } else if (!this.offlineTimer) {
            // Possibly partial cache: wait for the server, but show it if the network is down.
            this.offlineTimer = setTimeout(becomeReady, 8000);
          }
          return;
        }
        if (changes.length) this.version += 1;
        if (changes.length || wasSynced !== this.synced) this.notify();
      }, () => {
        // e.g. no permission: pages fall back to one-off queries. Data already shown stays.
        clearTimeout(this.offlineTimer);
        if (this.state !== 'ready') this.state = 'failed';
        this.unsubscribe = null;
        resolve(this.state === 'ready');
      });
    });
  }

  stop() {
    if (this.unsubscribe) this.unsubscribe();
    this.unsubscribe = null;
    this.docs.clear();
    this.state = 'idle';
    this.synced = false;
    this.readyPromise = null;
    clearTimeout(this.timer);
    clearTimeout(this.offlineTimer);
    this.offlineTimer = null;
  }

  /** Resolves to true when the live copy can be used, false when queries must be used. */
  ready() {
    if (this.state === 'ready') return Promise.resolve(true);
    if (!this.readyPromise) return Promise.resolve(false);
    return this.readyPromise;
  }

  get isReady() { return this.state === 'ready'; }

  list() { return [...this.docs.values()]; }

  get(id) { return this.docs.get(id) || null; }

  /** Calls [fn] (at most every 250 ms) when the data changes. Returns the unsubscribe function. */
  subscribe(fn) {
    this.subscribers.add(fn);
    return () => this.subscribers.delete(fn);
  }

  notify() {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.subscribers.forEach((fn) => { try { fn(); } catch (e) { console.error(e); } }), 250);
  }
}

export const accountsStore = new LiveCollection('accounts');
export const statsStore = new LiveCollection('stats');
export const devicesStore = new LiveCollection('devices');

const ALL = [accountsStore, statsStore, devicesStore];

export function startStores() {
  ALL.forEach((s) => s.start());
}

export function stopStores() {
  ALL.forEach((s) => s.stop());
}
