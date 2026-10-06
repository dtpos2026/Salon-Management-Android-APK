import { listAccounts, searchAccounts, effectiveStatus, PLANS, salesStatsMap, salesSummary } from '../data.js';
import { esc, fmtDate, relativeDays, debounce, money, errorMessage } from '../util.js';
import { statusPill } from '../actions.js';
import { accountsStore, statsStore } from '../store.js';

const FILTERS = [
  ['ALL', 'All'], ['PENDING', 'Pending'], ['DEVICE', 'New phone requests'], ['APPROVED', 'Approved'], ['PAYMENT_PENDING', 'Payment pending'],
  ['SOON', 'Expiring soon'], ['LAPSED', 'Licence ended'], ['EXPIRED', 'Marked expired'], ['SUSPENDED', 'Suspended'],
  ['BLOCKED', 'Blocked'], ['REJECTED', 'Rejected'],
];

let stats = {};

function row(a) {
  const plan = PLANS[a.plan]?.label || a.plan || '—';
  const sales = salesSummary(stats[a.id]);
  return `
    <tr class="row-link" data-href="#/salon/${encodeURIComponent(a.id)}">
      <td data-label="Salon"><div class="cell-title">${esc(a.salonName || '—')}</div><div class="cell-sub">${esc(a.ownerName || '')}${a.city ? ` · ${esc(a.city)}` : ''}</div></td>
      <td data-label="Contact"><div>${esc(a.phone || '—')}</div><div class="cell-sub">${esc(a.email || '')}</div></td>
      <td data-label="IDs"><div class="mono">${esc(a.customerId || '—')}</div><div class="cell-sub mono">${esc(a.licenseId || '')}</div></td>
      <td data-label="Plan"><div>${esc(plan)}${a.monthlyFee ? ` · ${money(a.monthlyFee)}` : ''}</div><div class="cell-sub">${a.expiresAt ? `Until ${fmtDate(a.expiresAt)} (${esc(relativeDays(a.expiresAt))})` : a.plan === 'LIFETIME' ? 'No expiry' : ''}</div></td>
      <td data-label="Status">${statusPill(effectiveStatus(a))}${a.pendingDeviceId ? ' <span class="pill st-PENDING">New phone</span>' : ''}</td>
      <td data-label="Sales today"><div>${stats[a.id] ? money(sales.today) : '—'}</div><div class="cell-sub">${stats[a.id] ? `${sales.todayCustomers} customers · month ${money(sales.month)}` : 'Not shared yet'}</div></td>
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
      <table class="list"><thead><tr><th>Salon</th><th>Contact</th><th>IDs</th><th>Plan / licence</th><th>Status</th><th>Sales today</th><th>Last seen</th></tr></thead><tbody id="rows"></tbody></table>
      <div id="state" class="loading"><div class="spinner"></div>Loading…</div>
      <div style="text-align:center;padding:10px"><button class="btn btn-ghost hidden" id="more">Load more</button></div>
    </div>`;
  const rows = el.querySelector('#rows');
  const stateEl = el.querySelector('#state');
  const more = el.querySelector('#more');

  stats = await salesStatsMap().catch(() => ({}));
  let query = '';

  const bindRows = () => rows.querySelectorAll('[data-href]').forEach((r) => { r.onclick = () => ctx.go(r.dataset.href); });

  async function load(reset, pageSize = accountsStore.isReady ? 50 : 20) {
    if (reset) last = null;
    stateEl.className = 'loading';
    stateEl.innerHTML = '<div class="spinner"></div>Loading…';
    more.classList.add('hidden');
    try {
      const page = await listAccounts({ filter, after: last, pageSize });
      last = page.last;
      // Replace the rows only when the new ones are ready, so the list never flashes empty.
      if (reset) rows.innerHTML = '';
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

  async function find(text) {
    query = text;
    if (!text.trim()) { load(true); return; }
    stateEl.className = 'loading';
    stateEl.innerHTML = '<div class="spinner"></div>Searching…';
    more.classList.add('hidden');
    try {
      const items = await searchAccounts(text);
      if (text !== query) return;
      rows.innerHTML = items.map(row).join('');
      bindRows();
      stateEl.className = items.length ? 'hidden' : 'empty';
      stateEl.textContent = items.length ? '' : accountsStore.isReady
        ? 'Nothing found. Try part of the salon or owner name, the email, phone or an ID.'
        : 'Nothing found. Search uses the start of the salon/owner name, or the exact email, phone or ID.';
    } catch (e) {
      stateEl.className = 'empty';
      stateEl.textContent = errorMessage(e);
    }
  }
  // The live copy answers at once; without it each search asks the server, so wait for typing to pause.
  const search = debounce(find, 350);

  el.querySelector('#q').addEventListener('input', (e) => (accountsStore.isReady ? find(e.target.value) : search(e.target.value)));
  el.querySelector('#chips').addEventListener('click', (e) => {
    const f = e.target.closest('[data-f]')?.dataset.f;
    if (!f) return;
    filter = f;
    el.querySelectorAll('.chip').forEach((c) => c.classList.toggle('active', c.dataset.f === f));
    el.querySelector('#q').value = '';
    query = '';
    history.replaceState(null, '', `#/salons${f === 'ALL' ? '' : `?filter=${f}`}`);
    load(true);
  });
  more.onclick = () => load(false);
  await load(true);
  // Keep the open list current while salons sign up or change (same filter, same length).
  ctx.live([accountsStore, statsStore], async () => {
    stats = await salesStatsMap().catch(() => stats);
    if (query.trim()) find(query);
    else load(true, Math.max(50, rows.children.length));
  });
}
