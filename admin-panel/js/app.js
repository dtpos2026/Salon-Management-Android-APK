// Super Admin panel shell: configuration, Google sign-in, admin check, navigation and routing.
import {
  GoogleAuthProvider, signInWithPopup, signInWithRedirect, getRedirectResult, onAuthStateChanged,
  signOut, signInWithCredential,
} from '../vendor/firebase.js';
import { firebase, initFirebase, parseConfigText, saveConfigInBrowser, forgetBrowserConfig, usingEmulators } from './fb.js';
import { isAdmin, dashboardCounts } from './data.js';
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
    case 'auth/unauthorized-domain':
      return `This website (${location.hostname}) is not authorised. Firebase console → Authentication → Settings → Authorized domains → Add domain.`;
    case 'auth/operation-not-allowed':
      return 'Google sign-in is not enabled. Firebase console → Authentication → Sign-in method → Google → Enable.';
    case 'auth/network-request-failed':
      return 'No internet connection.';
    default:
      return e?.message || String(e);
  }
}

function renderLogin(error) {
  glass(`${brand}
    <h2>Super Admin</h2>
    <p>Sign in with the Google account that has admin access.</p>
    <button class="btn-google" id="google">${ICONS.google}<span>Continue with Google</span></button>
    ${usingEmulators ? `<div style="margin-top:14px"><input id="emu-email" placeholder="admin@example.com" style="width:100%;height:40px;border-radius:10px;border:0;padding:0 10px;color:#111"><button class="btn btn-primary" id="emu" style="width:100%">Sign in (emulator)</button></div>` : ''}
    ${error ? `<p style="color:#ffb4ab;margin-top:16px">${esc(error)}</p>` : ''}
    <div class="glass-note">Only accounts listed as admins in Firestore can open the panel.</div>`);
  $('#google').onclick = googleSignIn;
  if (usingEmulators) {
    $('#emu').onclick = async () => {
      const email = $('#emu-email').value.trim() || 'admin@example.com';
      const token = JSON.stringify({ sub: email, email, email_verified: true, name: email.split('@')[0] });
      await signInWithCredential(firebase().auth, GoogleAuthProvider.credential(token)).catch((e) => renderLogin(authErrorText(e)));
    };
  }
}

async function googleSignIn() {
  const { auth } = firebase();
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  try {
    await signInWithPopup(auth, provider);
  } catch (e) {
    if (['auth/popup-blocked', 'auth/operation-not-supported-in-this-environment'].includes(e.code)) {
      await signInWithRedirect(auth, provider);
    } else if (!['auth/popup-closed-by-user', 'auth/cancelled-popup-request'].includes(e.code)) {
      renderLogin(authErrorText(e));
    }
  }
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
      <li>Document ID: paste the UID above. Add field <b>email</b> (string) = your Gmail. Save.</li>
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
  { re: /^#\/settings/, nav: 'settings', page: settings },
  { re: /^#\/admins/, nav: 'admins', page: admins },
  { re: /^(#\/?(dashboard)?)?$/, nav: 'dashboard', page: dashboard },
];

let currentUser = null;

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
  $('#sign-out').onclick = () => signOut(firebase().auth);
  $('#menu').onclick = () => $('.shell').classList.toggle('nav-open');
  $('#forget-cfg')?.addEventListener('click', (e) => {
    e.preventDefault();
    forgetBrowserConfig();
    location.reload();
  });
  $$('.nav a').forEach((a) => a.addEventListener('click', () => $('.shell').classList.remove('nav-open')));
  refreshBadge();
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

async function route() {
  const content = $('#content');
  if (!content) return;
  const token = ++routeToken;
  const alive = () => token === routeToken;
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

async function start(user) {
  renderLoading('Checking admin access…');
  try {
    if (await isAdmin(user.uid)) {
      currentUser = user;
      renderShell(user);
    } else {
      renderNotAdmin(user);
    }
  } catch (e) {
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
  getRedirectResult(services.auth).catch((e) => toast(authErrorText(e), 'error'));
  onAuthStateChanged(services.auth, (user) => {
    if (!user) {
      currentUser = null;
      renderLogin();
    } else if (!currentUser || currentUser.uid !== user.uid) {
      start(user);
    }
  });
}

boot();
