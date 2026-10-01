import { listInvoices } from '../data.js';
import { esc, fmtDate, money, errorMessage } from '../util.js';

const FILTERS = [['ALL', 'All'], ['UNPAID', 'Unpaid'], ['PARTIAL', 'Partly paid'], ['PAID', 'Paid']];

export async function render(el, ctx) {
  ctx.setTitle('Invoices');
  ctx.setActions('<a class="btn btn-primary" href="#/invoice/new">+ New invoice</a>');
  let filter = ctx.params.get('filter') || 'ALL';
  let last = null;
  el.innerHTML = `
    <div class="chips" id="chips" style="margin-bottom:14px">${FILTERS.map(([k, l]) => `<button class="chip ${k === filter ? 'active' : ''}" data-f="${k}">${l}</button>`).join('')}</div>
    <div class="card" style="padding:8px 12px">
      <table class="list"><thead><tr><th>Invoice</th><th>Salon</th><th>Date</th><th>Total</th><th>Paid</th><th>Balance</th><th>Status</th></tr></thead><tbody id="rows"></tbody></table>
      <div id="state" class="loading"><div class="spinner"></div>Loading…</div>
      <div style="text-align:center;padding:10px"><button class="btn btn-ghost hidden" id="more">Load more</button></div>
    </div>`;
  const rows = el.querySelector('#rows');
  const stateEl = el.querySelector('#state');
  const more = el.querySelector('#more');

  async function load(reset) {
    if (reset) { rows.innerHTML = ''; last = null; }
    stateEl.className = 'loading';
    stateEl.innerHTML = '<div class="spinner"></div>Loading…';
    try {
      const page = await listInvoices({ status: filter, after: last });
      last = page.last;
      rows.insertAdjacentHTML('beforeend', page.items.map((i) => `
        <tr class="row-link" data-href="#/invoice/${encodeURIComponent(i.id)}">
          <td data-label="Invoice" class="cell-title">${esc(i.number)}</td>
          <td data-label="Salon"><div>${esc(i.salonName || '')}</div><div class="cell-sub">${esc(i.customerId || '')}</div></td>
          <td data-label="Date">${fmtDate(i.issuedAt)}</td>
          <td data-label="Total">${money(i.total)}</td>
          <td data-label="Paid">${money(i.paid)}</td>
          <td data-label="Balance">${money(i.balance)}</td>
          <td data-label="Status"><span class="pill st-${esc(i.status)}">${esc(i.status)}</span></td>
        </tr>`).join(''));
      rows.querySelectorAll('[data-href]').forEach((r) => { r.onclick = () => ctx.go(r.dataset.href); });
      stateEl.className = rows.children.length ? 'hidden' : 'empty';
      stateEl.textContent = rows.children.length ? '' : 'No invoices here yet.';
      more.classList.toggle('hidden', !page.more);
    } catch (e) {
      stateEl.className = 'empty';
      stateEl.textContent = errorMessage(e);
    }
  }

  el.querySelector('#chips').addEventListener('click', (e) => {
    const f = e.target.closest('[data-f]')?.dataset.f;
    if (!f) return;
    filter = f;
    el.querySelectorAll('.chip').forEach((c) => c.classList.toggle('active', c.dataset.f === f));
    history.replaceState(null, '', `#/invoices${f === 'ALL' ? '' : `?filter=${f}`}`);
    load(true);
  });
  more.onclick = () => load(false);
  await load(true);
}
