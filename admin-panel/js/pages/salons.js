import { listAccounts, searchAccounts, effectiveStatus, PLANS } from '../data.js';
import { esc, fmtDate, relativeDays, debounce, money, errorMessage } from '../util.js';
import { statusPill } from '../actions.js';

const FILTERS = [
  ['ALL', 'All'], ['PENDING', 'Pending'], ['DEVICE', 'New phone requests'], ['APPROVED', 'Approved'], ['PAYMENT_PENDING', 'Payment pending'],
  ['SOON', 'Expiring soon'], ['LAPSED', 'Licence ended'], ['EXPIRED', 'Marked expired'], ['SUSPENDED', 'Suspended'],
  ['BLOCKED', 'Blocked'], ['REJECTED', 'Rejected'],
];

function row(a) {
  const plan = PLANS[a.plan]?.label || a.plan || '—';
  return `
    <tr class="row-link" data-href="#/salon/${encodeURIComponent(a.id)}">
      <td data-label="Salon"><div class="cell-title">${esc(a.salonName || '—')}</div><div class="cell-sub">${esc(a.ownerName || '')}${a.city ? ` · ${esc(a.city)}` : ''}</div></td>
      <td data-label="Contact"><div>${esc(a.phone || '—')}</div><div class="cell-sub">${esc(a.email || '')}</div></td>
      <td data-label="IDs"><div class="mono">${esc(a.customerId || '—')}</div><div class="cell-sub mono">${esc(a.licenseId || '')}</div></td>
      <td data-label="Plan"><div>${esc(plan)}${a.monthlyFee ? ` · ${money(a.monthlyFee)}` : ''}</div><div class="cell-sub">${a.expiresAt ? `Until ${fmtDate(a.expiresAt)} (${esc(relativeDays(a.expiresAt))})` : a.plan === 'LIFETIME' ? 'No expiry' : ''}</div></td>
      <td data-label="Status">${statusPill(effectiveStatus(a))}${a.pendingDeviceId ? ' <span class="pill st-PENDING">New phone</span>' : ''}</td>
      <td data-label="Last seen" class="cell-sub">${a.lastSeenAt ? fmtDate(a.lastSeenAt) : '—'}</td>
    </tr>`;
}

export async function render(el, ctx) {
  ctx.setTitle('Salons');
  let filter = ctx.params.get('filter') || 'ALL';
  let last = null;
  el.innerHTML = `
    <div class="toolbar">
      <div class="search"><input id="q" placeholder="Search name, email, phone, Customer ID, License ID, UID…" autocomplete="off"></div>
    </div>
    <div class="chips" id="chips" style="margin-bottom:14px">${FILTERS.map(([k, l]) => `<button class="chip ${k === filter ? 'active' : ''}" data-f="${k}">${l}</button>`).join('')}</div>
    <div class="card" style="padding:8px 12px">
      <table class="list"><thead><tr><th>Salon</th><th>Contact</th><th>IDs</th><th>Plan / licence</th><th>Status</th><th>Last seen</th></tr></thead><tbody id="rows"></tbody></table>
      <div id="state" class="loading"><div class="spinner"></div>Loading…</div>
      <div style="text-align:center;padding:10px"><button class="btn btn-ghost hidden" id="more">Load more</button></div>
    </div>`;
  const rows = el.querySelector('#rows');
  const stateEl = el.querySelector('#state');
  const more = el.querySelector('#more');

  const bindRows = () => rows.querySelectorAll('[data-href]').forEach((r) => { r.onclick = () => ctx.go(r.dataset.href); });

  async function load(reset) {
    if (reset) { rows.innerHTML = ''; last = null; }
    stateEl.className = 'loading';
    stateEl.innerHTML = '<div class="spinner"></div>Loading…';
    more.classList.add('hidden');
    try {
      const page = await listAccounts({ filter, after: last });
      last = page.last;
      rows.insertAdjacentHTML('beforeend', page.items.map(row).join(''));
      bindRows();
      stateEl.className = rows.children.length ? 'hidden' : 'empty';
      stateEl.textContent = rows.children.length ? '' : 'No salons in this list.';
      more.classList.toggle('hidden', !page.more);
    } catch (e) {
      stateEl.className = 'empty';
      stateEl.textContent = errorMessage(e);
    }
  }

  const search = debounce(async (text) => {
    if (!text.trim()) { load(true); return; }
    rows.innerHTML = '';
    stateEl.className = 'loading';
    stateEl.innerHTML = '<div class="spinner"></div>Searching…';
    more.classList.add('hidden');
    try {
      const items = await searchAccounts(text);
      rows.innerHTML = items.map(row).join('');
      bindRows();
      stateEl.className = items.length ? 'hidden' : 'empty';
      stateEl.textContent = items.length ? '' : 'Nothing found. Search uses the start of the salon/owner name, or the exact email, phone or ID.';
    } catch (e) {
      stateEl.className = 'empty';
      stateEl.textContent = errorMessage(e);
    }
  }, 350);

  el.querySelector('#q').addEventListener('input', (e) => search(e.target.value));
  el.querySelector('#chips').addEventListener('click', (e) => {
    const f = e.target.closest('[data-f]')?.dataset.f;
    if (!f) return;
    filter = f;
    el.querySelectorAll('.chip').forEach((c) => c.classList.toggle('active', c.dataset.f === f));
    el.querySelector('#q').value = '';
    history.replaceState(null, '', `#/salons${f === 'ALL' ? '' : `?filter=${f}`}`);
    load(true);
  });
  more.onclick = () => load(false);
  await load(true);
}
