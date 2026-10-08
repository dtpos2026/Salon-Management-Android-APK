// The dashboard as the admin last saw it, kept in this browser. On the next visit it shows at
// once while Firebase connects, then the live dashboard replaces it (usually within a second or
// two). Removed on sign-out, so a signed-out browser never shows salon data.
const KEY = 'dt-admin-snapshot';
const MAX_AGE_MS = 7 * 24 * 3600 * 1000;

/** Saves the current panel (shell + dashboard) for the next visit. */
export function saveSnapshot() {
  try {
    const root = document.getElementById('app');
    if (!root || !root.querySelector('.shell') || root.querySelector('.shell.snapshot')) return;
    localStorage.setItem(KEY, JSON.stringify({ at: Date.now(), html: root.innerHTML }));
  } catch (e) { /* storage full or blocked: the panel simply loads normally */ }
}

/** Shows the saved dashboard (read-only) when [allowed]; returns true if it did. */
export function showSnapshot(allowed) {
  try {
    if (!allowed) return false;
    // Already shown by the small script in index.html.
    if (document.querySelector('#app .shell.snapshot')) return true;
    const hash = location.hash || '#/dashboard';
    if (!/^(#\/?(dashboard)?)?$/.test(hash)) return false;
    const saved = JSON.parse(localStorage.getItem(KEY) || 'null');
    if (!saved || !saved.html || Date.now() - saved.at > MAX_AGE_MS) return false;
    const root = document.getElementById('app');
    root.innerHTML = saved.html;
    const shell = root.querySelector('.shell');
    if (!shell) return false;
    shell.classList.add('snapshot');
    const actions = root.querySelector('#page-actions');
    if (actions) actions.innerHTML = '<span class="cell-sub"><span class="spinner"></span>Updating…</span>';
    return true;
  } catch (e) {
    return false;
  }
}

export function forgetSnapshot() {
  try { localStorage.removeItem(KEY); } catch (e) { /* ignore */ }
}
