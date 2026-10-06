import { dashboardCounts, recentPending, expiringSoon, revenueSummary, listInvoices, effectiveStatus, salesStatsMap, salesSummary } from '../data.js';
import { esc, fmtDate, money, relativeDays } from '../util.js';
import { approveDialog, statusPill } from '../actions.js';
import { accountsStore, statsStore } from '../store.js';

function stat(label, value, hint, href, tint, hero = false) {
  return `<a class="stat ${hero ? 'hero' : ''}" href="${href}" style="--tint:${tint}">
    <div class="label">${esc(label)}</div><div class="value">${esc(value)}</div>${hint ? `<div class="hint">${esc(hint)}</div>` : ''}</a>`;
}

const waiting = '<span class="spinner"></span>Loading invoices…';

export async function render(el, ctx) {
  ctx.setTitle('Dashboard');
  ctx.setActions('<a class="btn btn-primary" href="#/invoice/new">+ New invoice</a>');
  // Salon numbers come from the live copy (instant); invoice money totals load beside them.
  const invoiceData = { revenue: undefined, latest: undefined };
  let data = null;
  const load = async () => {
    const [counts, pending, renewals, stats] = await Promise.all([
      dashboardCounts(), recentPending(6), expiringSoon(6), salesStatsMap().catch(() => ({})),
    ]);
    data = { counts, pending, renewals, stats };
  };
  const draw = () => { if (data && ctx.alive()) paint(el, ctx, data, invoiceData, () => { load().then(draw); }); };
  revenueSummary().catch(() => null).then((r) => { invoiceData.revenue = r; draw(); });
  listInvoices({ pageSize: 5 }).catch(() => ({ items: [] })).then((l) => { invoiceData.latest = l; draw(); });
  await load();
  draw();
  ctx.live([accountsStore, statsStore], () => load().then(draw).catch(() => null));
}

function paint(el, ctx, { counts, pending, renewals, stats }, invoiceData, reload) {
  const { revenue, latest } = invoiceData;
  const totals = Object.values(stats).map((s) => salesSummary(s)).reduce((acc, s) => ({
    today: acc.today + s.today, customers: acc.customers + s.todayCustomers, month: acc.month + s.month,
  }), { today: 0, customers: 0, month: 0 });
  const topToday = Object.entries(stats).map(([uid, s]) => ({ uid, name: s.salonName, ...salesSummary(s) }))
    .filter((s) => s.today > 0 || s.month > 0).sort((a, b) => b.today - a.today || b.month - a.month).slice(0, 8);
  el.innerHTML = `
    <div class="stats">
      ${stat('Total salons', counts.total, 'All registered accounts', '#/salons', 'rgba(255,255,255,.12)', true)}
      ${stat('Waiting for approval', counts.PENDING, 'New sign-ups', '#/salons?filter=PENDING', 'rgba(36,87,197,.12)')}
      ${stat('New phone requests', counts.deviceRequests, 'Approve on the salon page', '#/salons?filter=DEVICE', 'rgba(233,201,135,.35)')}
      ${stat('Active', counts.active, 'Approved and valid', '#/salons?filter=APPROVED', 'rgba(30,138,74,.12)')}
      ${stat('Payment pending', counts.PAYMENT_PENDING, '', '#/salons?filter=PAYMENT_PENDING', 'rgba(138,90,0,.14)')}
      ${stat('Expired', counts.expiredTotal, `${counts.lapsed} licence date passed`, '#/salons?filter=LAPSED', 'rgba(198,40,40,.12)')}
      ${stat('Expiring in 7 days', counts.expiringSoon, '', '#/salons?filter=SOON', 'rgba(233,201,135,.35)')}
      ${stat('Suspended', counts.SUSPENDED, '', '#/salons?filter=SUSPENDED', 'rgba(90,63,138,.14)')}
      ${stat('Blocked', counts.BLOCKED, `${counts.REJECTED} rejected`, '#/salons?filter=BLOCKED', 'rgba(198,40,40,.12)')}
      ${revenue === undefined ? stat('Collected this month', '…', 'Loading invoices', '#/invoices', 'rgba(30,138,74,.12)') : revenue ? stat('Collected this month', money(revenue.collectedThisMonth), `Invoiced ${money(revenue.invoicedThisMonth)}`, '#/invoices', 'rgba(30,138,74,.12)') : ''}
      ${stat("All salons' sales today", money(totals.today), `${totals.customers} customers · month ${money(totals.month)}`, '#/salons', 'rgba(30,138,74,.12)')}
      ${revenue === undefined ? stat('Outstanding', '…', 'Loading invoices', '#/invoices?filter=UNPAID', 'rgba(198,40,40,.12)') : revenue ? stat('Outstanding', money(revenue.outstanding), `${revenue.unpaidCount} unpaid invoices`, '#/invoices?filter=UNPAID', 'rgba(198,40,40,.12)') : ''}
    </div>
    <div class="grid grid-2">
      <div class="card">
        <div class="card-head"><h2>Waiting for approval</h2><div class="spacer"></div><a href="#/salons?filter=PENDING">View all</a></div>
        ${pending.length ? `<table class="list"><tbody>${pending.map((a) => `
          <tr>
            <td data-label="Salon"><a class="cell-title" href="#/salon/${encodeURIComponent(a.id)}">${esc(a.salonName || '—')}</a><div class="cell-sub">${esc(a.ownerName || '')} · ${esc(a.phone || '')}</div><div class="cell-sub">${esc(a.email || '')}</div></td>
            <td data-label="Registered" class="cell-sub">${fmtDate(a.createdAt)}</td>
            <td style="text-align:right"><button class="btn btn-primary btn-sm" data-approve="${esc(a.id)}">Approve</button></td>
          </tr>`).join('')}</tbody></table>` : '<div class="empty">No salons waiting. New sign-ups appear here.</div>'}
      </div>
      <div class="card">
        <div class="card-head"><h2>Renewals due</h2><div class="spacer"></div><a href="#/salons?filter=SOON">View all</a></div>
        ${renewals.length ? `<table class="list"><tbody>${renewals.map((a) => `
          <tr class="row-link" data-href="#/salon/${encodeURIComponent(a.id)}">
            <td data-label="Salon"><div class="cell-title">${esc(a.salonName || '—')}</div><div class="cell-sub">${esc(a.customerId || '')}</div></td>
            <td data-label="Licence ends"><div>${fmtDate(a.expiresAt)}</div><div class="cell-sub">${esc(relativeDays(a.expiresAt))}</div></td>
            <td data-label="Status">${statusPill(effectiveStatus(a))}</td>
          </tr>`).join('')}</tbody></table>` : '<div class="empty">No licences ending in the next 7 days.</div>'}
      </div>
    </div>
    <div class="card" style="margin-top:16px">
      <div class="card-head"><h2>Sales by salon</h2><div class="spacer"></div><span class="cell-sub">Shared by the app: totals only</span></div>
      ${topToday.length ? `<table class="list"><thead><tr><th>Salon</th><th>Today</th><th>Yesterday</th><th>This month</th></tr></thead><tbody>${topToday.map((s) => `
        <tr class="row-link" data-href="#/salon/${encodeURIComponent(s.uid)}">
          <td data-label="Salon" class="cell-title">${esc(s.name || s.uid)}</td>
          <td data-label="Today">${money(s.today)}<div class="cell-sub">${s.todayCustomers} customers</div></td>
          <td data-label="Yesterday">${money(s.yesterday)}<div class="cell-sub">${s.yesterdayCustomers} customers</div></td>
          <td data-label="This month">${money(s.month)}<div class="cell-sub">${s.monthCustomers} customers</div></td>
        </tr>`).join('')}</tbody></table>` : '<div class="empty">No sales shared yet. Salons on app 2.1.0 or newer share their daily totals when online.</div>'}
    </div>
    <div class="card" style="margin-top:16px">
      <div class="card-head"><h2>Latest invoices</h2><div class="spacer"></div><a href="#/invoices">All invoices</a></div>
      ${latest === undefined ? `<div class="empty">${waiting}</div>` : latest.items.length ? `<table class="list"><thead><tr><th>Invoice</th><th>Salon</th><th>Date</th><th>Total</th><th>Balance</th><th>Status</th></tr></thead><tbody>${latest.items.map((i) => `
        <tr class="row-link" data-href="#/invoice/${encodeURIComponent(i.id)}">
          <td data-label="Invoice" class="cell-title">${esc(i.number)}</td>
          <td data-label="Salon">${esc(i.salonName || '')}</td>
          <td data-label="Date">${fmtDate(i.issuedAt)}</td>
          <td data-label="Total">${money(i.total)}</td>
          <td data-label="Balance">${money(i.balance)}</td>
          <td data-label="Status"><span class="pill st-${esc(i.status)}">${esc(i.status)}</span></td>
        </tr>`).join('')}</tbody></table>` : '<div class="empty">No invoices yet.</div>'}
    </div>`;
  el.querySelectorAll('[data-href]').forEach((row) => row.addEventListener('click', () => ctx.go(row.dataset.href)));
  el.querySelectorAll('[data-approve]').forEach((btn) => btn.addEventListener('click', async () => {
    const account = pending.find((a) => a.id === btn.dataset.approve);
    if (await approveDialog(account)) {
      ctx.refreshBadge();
      reload();
    }
  }));
}
