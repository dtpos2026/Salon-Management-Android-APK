// Super Admin panel shell: configuration, email sign-in, admin check, navigation and routing.
import {
  onAuthStateChanged, signOut, signInWithEmailAndPassword, sendPasswordResetEmail,
} from '../vendor/firebase.js';
import { firebase, initFirebase, parseConfigText, saveConfigInBrowser, forgetBrowserConfig, usingEmulators, clearLocalData } from './fb.js';
import { isAdmin, dashboardCounts, ownerClaimed, claimFirstAdmin, ensureOwnerRecorded } from './data.js';
import { accountsStore, startStores, stopStores, forgetCachedCollections } from './store.js';
import { esc, $, $$, toast, copyText, errorMessage } from './util.js';
import { ICONS } from './icons.js';
import * as dashboard from './pages/dashboard.js';
import * as salons from './pages/salons.js';
import * as salon from './pages/salon.js';
import * as invoices from './pages/invoices.js';
import * as invoiceEdit from './pages/invoice-edit.js';
import * as invoiceView from './pages/invoice-view.js';
import * as settings from './pages/settings.js';
import * as admins from './pages/admins.js';
import * as reminders from './pages/reminders.js';
import * as support from './pages/support.js';
import * as phones from './pages/phones.js';

const root = document.getElementById('app');

const brand = `
  <img class="brand-mark" src="assets/dt-monogram.png" alt="">
  <div class="wordmark">DT SALON</div>
  <div class="wordmark-sub">MANAGER</div>`;

function glass(inner) {
  root.innerHTML = `<div class="glass-screen"><div class="glass-card">${inner}<div class="powered">BY DIGITAL TARGET</div></div></div>`;
}

function renderLoading(text = 'Loading…') {
  glass(`${brand}<p style="margin-top:24px"><span class="spinner"></span>${esc(text)}</p>`);
}

function renderSetup(error) {
  glass(`${brand}
    <h2>Connect Firebase</h2>
    <p>Paste the <b>web app config</b> from Firebase console → Project settings → Your apps → Web app. It is saved only in this browser.</p>
    <textarea id="cfg" placeholder="const firebaseConfig = { apiKey: ..., authDomain: ..., projectId: ... };"></textarea>
    ${error ? `<p style="color:#ffb4ab">${esc(error)}</p>` : ''}
    <button class="btn btn-primary" id="save-cfg" style="width:100%">Save and continue</button>
    <div class="glass-note">Tip: when the panel is deployed with a firebase-config.js file (see the guide), this step is not needed.</div>`);
  $('#save-cfg').onclick = () => {
    try {
      saveConfigInBrowser(parseConfigText($('#cfg').value));
      location.reload();
    } catch (e) {
      renderSetup(`Could not read the config: ${e.message}`);
    }
  };
}

function authErrorText(e) {
  switch (e?.code) {
    case 'auth/invalid-credential':
    case 'auth/wrong-password':
    case 'auth/user-not-found':
    case 'auth/invalid-login-credentials':
      return 'Email or password is incorrect.';
    case 'auth/invalid-email':
      return 'Please enter a valid email address.';
    case 'auth/user-disabled':
      return 'This login has been disabled in Firebase console.';
    case 'auth/too-many-requests':
      return 'Too many attempts. Wait a few minutes and try again.';
    case 'auth/operation-not-allowed':
      return 'Email/Password sign-in is not enabled. Firebase console → Authentication → Sign-in method → Email/Password → Enable.';
    case 'auth/network-request-failed':
      return 'No internet connection.';
    default:
      return e?.message || String(e);
  }
}

function renderLogin(error, info) {
  glass(`${brand}
    <h2>Super Admin</h2>
    <p>Sign in with the admin email and password.</p>
    <form id="login" class="login-form" autocomplete="on">
      <input id="email" type="email" placeholder="Email" autocomplete="username" required>
      <input id="password" type="password" placeholder="Password" autocomplete="current-password" required>
      <button class="btn btn-primary" id="signin" type="submit" style="width:100%">Sign in</button>
    </form>
    <button class="btn btn-ghost btn-sm" id="forgot" type="button" style="margin-top:8px">Forgot password?</button>
    ${error ? `<p style="color:#ffb4ab;margin-top:16px">${esc(error)}</p>` : ''}
    ${info ? `<p style="color:#b8f5cc;margin-top:16px">${esc(info)}</p>` : ''}
    <div class="glass-note">Create the admin login once in Firebase console → Authentication → Users → Add user. Only logins listed as admins in Firestore can open the panel.</div>`);
  $('#login').onsubmit = async (ev) => {
    ev.preventDefault();
    const btn = $('#signin');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span>Signing in…';
    try {
      await signInWithEmailAndPassword(firebase().auth, $('#email').value.trim(), $('#password').value);
    } catch (e) {
      renderLogin(authErrorText(e));
    }
  };
  $('#forgot').onclick = async () => {
    const email = $('#email').value.trim();
    if (!email) return renderLogin('Type your email first, then press Forgot password.');
    try {
      await sendPasswordResetEmail(firebase().auth, email);
      renderLogin(null, `Password reset link sent to ${email}. Check the inbox and Spam folder.`);
    } catch (e) {
      renderLogin(authErrorText(e));
    }
  };
}

function renderClaim(user, error) {
  glass(`${brand}
    <h2>Welcome</h2>
    <p>This panel has no Super Admin yet. Make <b>${esc(user.email || '')}</b> the Super Admin?</p>
    <button class="btn btn-primary" id="claim" style="width:100%">Become Super Admin</button>
    <button class="btn btn-ghost btn-sm" id="out" style="margin-top:8px">Sign out</button>
    ${error ? `<p style="color:#ffb4ab;margin-top:16px">${esc(error)}</p>` : ''}
    <div class="glass-note">Only the first login can do this, once. Later admins are added from the Admins page.</div>`);
  $('#claim').onclick = async () => {
    try {
      await claimFirstAdmin(user);
      start(user);
    } catch (e) {
      renderClaim(user, errorMessage(e));
    }
  };
  $('#out').onclick = () => signOut(firebase().auth);
}

function renderNotAdmin(user) {
  const { projectId } = firebase();
  glass(`${brand}
    <h2>No admin access</h2>
    <p>${esc(user.email)} is signed in but is not an admin yet.</p>
    <div class="uid" id="uid">${esc(user.uid)}</div>
    <button class="btn btn-ghost btn-sm" id="copy">Copy UID</button>
    <ol>
      <li>Open <a href="https://console.firebase.google.com/project/${esc(projectId)}/firestore/data" target="_blank" rel="noopener" style="color:#c9a4ff">Firestore Database</a>.</li>
      <li>Start collection <b>admins</b> (or open it).</li>
      <li>Document ID: paste the UID above. Add field <b>email</b> (string) = your email. Save.</li>
      <li>Come back and press Retry.</li>
    </ol>
    <div class="actions" style="justify-content:center;margin-top:12px">
      <button class="btn btn-primary" id="retry">Retry</button>
      <button class="btn btn-ghost" id="out">Sign out</button>
    </div>`);
  $('#copy').onclick = () => copyText(user.uid);
  $('#retry').onclick = () => start(user);
  $('#out').onclick = () => signOut(firebase().auth);
}

const NAV = [
  { key: 'dashboard', href: '#/dashboard', label: 'Dashboard', icon: ICONS.dashboard },
  { key: 'salons', href: '#/salons', label: 'Salons', icon: ICONS.salons, badge: true },
  { key: 'invoices', href: '#/invoices', label: 'Invoices', icon: ICONS.invoices },
  { key: 'support', href: '#/support', label: 'Support', icon: ICONS.support },
  { key: 'phones', href: '#/phones', label: 'Phones', icon: ICONS.phones },
  { key: 'reminders', href: '#/reminders', label: 'Reminders', icon: ICONS.reminders },
  { key: 'settings', href: '#/settings', label: 'Settings', icon: ICONS.settings },
  { key: 'admins', href: '#/admins', label: 'Admins', icon: ICONS.admins },
];

const ROUTES = [
  { re: /^#\/invoice\/new/, nav: 'invoices', page: invoiceEdit },
  { re: /^#\/invoice\/([^/?]+)\/edit/, nav: 'invoices', page: invoiceEdit },
  { re: /^#\/invoice\/([^/?]+)/, nav: 'invoices', page: invoiceView },
  { re: /^#\/invoices/, nav: 'invoices', page: invoices },
  { re: /^#\/salon\/([^/?]+)/, nav: 'salons', page: salon },
  { re: /^#\/salons/, nav: 'salons', page: salons },
  { re: /^#\/support/, nav: 'support', page: support },
  { re: /^#\/phones/, nav: 'phones', page: phones },
  { re: /^#\/reminders/, nav: 'reminders', page: reminders },
  { re: /^#\/settings/, nav: 'settings', page: settings },
  { re: /^#\/admins/, nav: 'admins', page: admins },
  { re: /^(#\/?(dashboard)?)?$/, nav: 'dashboard', page: dashboard },
];

let currentUser = null;
let badgeWatch = null;

const ADMIN_KEY = 'dt-admin-verified';

/** Admin access already confirmed for this login in this browser (checked again in the background). */
function knownAdmin(uid) {
  try { return localStorage.getItem(ADMIN_KEY) === uid; } catch (e) { return false; }
}

function rememberAdmin(uid) {
  try {
    if (uid) localStorage.setItem(ADMIN_KEY, uid); else localStorage.removeItem(ADMIN_KEY);
  } catch (e) { /* ignore */ }
}

let signingOut = false;

/** Signs out and removes the salons' data cached in this browser. */
async function signOutEverywhere() {
  signingOut = true;
  renderLoading('Signing out…');
  rememberAdmin(null);
  stopStores();
  await signOut(firebase().auth);
  await clearLocalData();
  forgetCachedCollections();
  location.reload();
}

function renderShell(user) {
  root.innerHTML = `
    <div class="shell">
      <aside class="sidebar">
        <div class="side-brand"><img src="assets/dt-monogram.png" alt=""><div><b>DT SALON</b><span>ADMIN</span></div></div>
        <nav class="nav">${NAV.map((n) => `<a href="${n.href}" data-nav="${n.key}">${n.icon}<span>${n.label}</span>${n.badge ? '<span class="badge hidden" id="pending-badge"></span>' : ''}</a>`).join('')}</nav>
        <div class="side-foot">
          Signed in as<div class="who">${esc(user.email || user.uid)}</div>
          <button class="btn btn-ghost btn-sm" id="sign-out" style="margin-top:10px">${ICONS.logout.replace('<svg', '<svg width="16" height="16"')} Sign out</button>
          ${firebase().source === 'browser' ? '<div style="margin-top:8px"><a href="#" id="forget-cfg" style="color:rgba(255,255,255,.6)">Change Firebase project</a></div>' : ''}
        </div>
      </aside>
      <div class="main">
        <div class="topbar">
          <button class="icon-btn menu-btn" id="menu" aria-label="Menu">${ICONS.menu.replace('<svg', '<svg width="22" height="22"')}</button>
          <h1 id="page-title">Dashboard</h1>
          <div class="spacer"></div>
          <div id="page-actions" class="actions"></div>
        </div>
        <div class="content" id="content"></div>
      </div>
    </div>`;
  $('#sign-out').onclick = (e) => {
    e.currentTarget.disabled = true;
    signOutEverywhere();
  };
  $('#menu').onclick = () => $('.shell').classList.toggle('nav-open');
  $('#forget-cfg')?.addEventListener('click', (e) => {
    e.preventDefault();
    forgetBrowserConfig();
    location.reload();
  });
  $$('.nav a').forEach((a) => a.addEventListener('click', () => $('.shell').classList.remove('nav-open')));
  refreshBadge();
  // The pending badge follows new sign-ups live.
  if (badgeWatch) badgeWatch();
  badgeWatch = accountsStore.subscribe(refreshBadge);
  route();
}

export async function refreshBadge() {
  try {
    const counts = await dashboardCounts();
    const badge = $('#pending-badge');
    if (badge) {
      badge.textContent = counts.PENDING;
      badge.classList.toggle('hidden', !counts.PENDING);
    }
    return counts;
  } catch (e) {
    return null;
  }
}

let routeToken = 0;
let cleanups = [];

async function route() {
  const content = $('#content');
  if (!content) return;
  const token = ++routeToken;
  const alive = () => token === routeToken;
  // Stop the previous page's live updates (and its map).
  cleanups.forEach((fn) => { try { fn(); } catch (e) { /* ignore */ } });
  cleanups = [];
  const hash = location.hash || '#/dashboard';
  const entry = ROUTES.find((r) => r.re.test(hash)) || ROUTES[ROUTES.length - 1];
  const match = hash.match(entry.re);
  const params = new URLSearchParams(hash.split('?')[1] || '');
  $$('.nav a').forEach((a) => a.classList.toggle('active', a.dataset.nav === entry.nav));
  $('#page-actions').innerHTML = '';
  // Each navigation renders into its own element, so a slow earlier page can never
  // overwrite the current one.
  const pageEl = document.createElement('div');
  pageEl.innerHTML = '<div class="loading"><div class="spinner"></div>Loading…</div>';
  content.replaceChildren(pageEl);
  const ctx = {
    user: currentUser,
    params,
    id: match && match[1] && match[1] !== 'new' ? decodeURIComponent(match[1]) : null,
    alive,
    setTitle: (t) => { if (alive()) { $('#page-title').textContent = t; document.title = `${t} · DT Salon Admin`; } },
    setActions: (html) => { const box = $('#page-actions'); if (alive()) box.innerHTML = html; return box; },
    go: (h) => { location.hash = h; },
    refreshBadge,
    /** Runs [fn] when the admin leaves this page. */
    onLeave: (fn) => { if (alive()) cleanups.push(fn); else fn(); },
    /** Calls [fn] when the live data of [stores] changes while this page is open. */
    live: (stores, fn) => stores.forEach((store) => {
      const off = store.subscribe(() => { if (alive()) fn(); });
      if (alive()) cleanups.push(off); else off();
    }),
  };
  try {
    await entry.page.render(pageEl, ctx);
  } catch (e) {
    console.error(e);
    if (alive()) pageEl.innerHTML = `<div class="card"><h2>Something went wrong</h2><p class="muted">${esc(errorMessage(e))}</p></div>`;
  }
  if (alive()) window.scrollTo(0, 0);
}

window.addEventListener('hashchange', route);

function openPanel(user) {
  currentUser = user;
  startStores();
  renderShell(user);
}

async function start(user) {
  if (knownAdmin(user.uid)) {
    // Returning admin: open at once and confirm access in the background. Firestore rules
    // protect the data either way; if access was removed the panel closes.
    openPanel(user);
    isAdmin(user.uid).then((ok) => {
      if (ok) { ensureOwnerRecorded(user); return; }
      rememberAdmin(null);
      stopStores();
      currentUser = null;
      start(user);
    }).catch(() => { /* offline: keep the cached view */ });
    return;
  }
  renderLoading('Checking admin access…');
  // Start loading the data together with the check; for a non-admin the rules refuse it.
  startStores();
  try {
    if (await isAdmin(user.uid)) {
      rememberAdmin(user.uid);
      ensureOwnerRecorded(user);
      openPanel(user);
      return;
    }
    stopStores();
    if (!(await ownerClaimed())) {
      renderClaim(user);
    } else {
      renderNotAdmin(user);
    }
  } catch (e) {
    stopStores();
    glass(`${brand}<h2>Cannot reach Firebase</h2><p>${esc(errorMessage(e))}</p><button class="btn btn-primary" id="retry">Retry</button>`);
    $('#retry').onclick = () => start(user);
  }
}

async function boot() {
  renderLoading();
  const services = await initFirebase();
  if (!services) {
    renderSetup();
    return;
  }
  renderLoading();
  onAuthStateChanged(services.auth, (user) => {
    if (!user) {
      currentUser = null;
      stopStores();
      if (!signingOut) renderLogin();
    } else if (!currentUser || currentUser.uid !== user.uid) {
      start(user);
    }
  });
}

boot();
