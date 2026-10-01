// Firebase initialisation. The web config comes from firebase-config.js (deployment) or, if
// that file is missing, from a one-time "paste your config" screen saved in this browser.
import {
  initializeApp, getAuth, connectAuthEmulator, getFirestore, connectFirestoreEmulator,
} from '../vendor/firebase.js';

const STORAGE_KEY = 'dt-admin-firebase-config';

export function loadConfig() {
  if (window.DT_FIREBASE_CONFIG && window.DT_FIREBASE_CONFIG.projectId &&
      !String(window.DT_FIREBASE_CONFIG.apiKey || '').startsWith('PASTE')) {
    return { config: window.DT_FIREBASE_CONFIG, source: 'file' };
  }
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
    if (saved && saved.projectId && saved.apiKey) return { config: saved, source: 'browser' };
  } catch (e) { /* ignore */ }
  return null;
}

/** Accepts either JSON or the `const firebaseConfig = {...}` snippet from the console. */
export function parseConfigText(text) {
  const body = String(text).slice(String(text).indexOf('{'), String(text).lastIndexOf('}') + 1);
  if (!body) throw new Error('No { ... } found');
  const json = body
    .replace(/\/\/.*$/gm, '')
    .replace(/([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)\s*:/g, '$1"$2":')
    .replace(/'/g, '"')
    .replace(/,\s*}/g, '}');
  const config = JSON.parse(json);
  if (!config.apiKey || !config.projectId || !config.authDomain) throw new Error('apiKey, authDomain and projectId are required');
  return config;
}

export function saveConfigInBrowser(config) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
}

export function forgetBrowserConfig() {
  localStorage.removeItem(STORAGE_KEY);
}

export const usingEmulators = Boolean(window.DT_USE_EMULATORS);

let services = null;

/** On Firebase Hosting the project's web config is served automatically. */
async function hostingConfig() {
  try {
    const res = await fetch('/__/firebase/init.json', { cache: 'no-store' });
    if (!res.ok) return null;
    const config = await res.json();
    return config && config.apiKey && config.projectId ? { config, source: 'hosting' } : null;
  } catch (e) {
    return null;
  }
}

/** Initialises Firebase once; resolves to null when no configuration is available. */
export async function initFirebase() {
  if (services) return services;
  const loaded = loadConfig() || await hostingConfig();
  if (!loaded) return null;
  const app = initializeApp(loaded.config);
  const auth = getAuth(app);
  const db = getFirestore(app);
  if (usingEmulators) {
    connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
    connectFirestoreEmulator(db, '127.0.0.1', 8080);
  }
  services = { app, auth, db, projectId: loaded.config.projectId, source: loaded.source };
  return services;
}

/** The initialised services (after initFirebase() resolved). */
export function firebase() {
  return services;
}
