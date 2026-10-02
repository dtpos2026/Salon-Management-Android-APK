import {
  getAccount, getInvoice, createInvoice, updateInvoice, getConfig, searchAccounts, invoiceTotals, PLANS, planExpiry,
} from '../data.js';
import { esc, money, inputDate, fromInputDate, toDate, toast, errorMessage, debounce, fmtDate } from '../util.js';

const METHODS = ['Bank Transfer', 'Cash', 'JazzCash', 'Easypaisa', 'Raast', 'Other'];

function itemRow(item = {}) {
  return `<tr>
    <td style="width:55%"><input name="desc" placeholder="Description" value="${esc(item.description || '')}" required></td>
    <td style="width:12%"><input name="qty" type="number" min="0" step="1" value="${esc(item.qty ?? 1)}"></td>
    <td style="width:18%"><input name="rate" type="number" min="0" step="1" value="${esc(item.rate ?? 0)}"></td>
    <td style="width:12%;text-align:right" class="amt strong"></td>
    <td><button type="button" class="icon-btn" data-remove title="Remove">✕</button></td>
  </tr>`;
}

/** Sensible defaults for a salon's next subscription invoice. */
function draftFor(account, billing) {
  const plan = PLANS[account.plan] || PLANS.MONTHLY;
  const months = plan.months || 1;
  const current = toDate(account.expiresAt);
  const from = current && current > new Date() ? current : new Date();
  const to = account.plan && !plan.lifetime && account.plan !== 'CUSTOM' ? planExpiry(account.plan, from) : null;
  return {
    accountUid: account.id,
    salonName: account.salonName || '',
    ownerName: account.ownerName || '',
    phone: account.phone || '',
    email: account.email || '',
    customerId: account.customerId || '',
    project: `${account.salonName || ''} - DT Salon Management`,
    category: billing.defaultService || 'DT Salon Management subscription',
    packageName: plan.label,
    periodFrom: from,
    periodTo: to,
    items: [{ description: `DT Salon Management - ${plan.label} subscription`, qty: 1, rate: (Number(account.monthlyFee) || 0) * months }],
  };
}

export async function render(el, ctx) {
  const billing = await getConfig('billing');
  const editing = ctx.id ? await getInvoice(ctx.id) : null;
  if (ctx.id && !editing) { el.innerHTML = '<div class="card empty">Invoice not found.</div>'; return; }
  let inv = editing || { items: [{ description: billing.defaultService || '', qty: 1, rate: 0 }], paid: 0, discount: 0, paymentMethod: 'Bank Transfer', dueText: 'On receipt' };
  const presetUid = ctx.params.get('uid');
  if (!editing && presetUid) {
    const account = await getAccount(presetUid);
    if (account) inv = { ...inv, ...draftFor(account, billing) };
  }
  ctx.setTitle(editing ? `Edit ${editing.number}` : 'New invoice');

  el.innerHTML = `
    <form id="form" class="card" novalidate>
      <div class="card-head"><h2>Bill to</h2></div>
      <div class="form-grid">
        <div class="field full"><label>Find salon</label>
          <div class="search"><input id="find" placeholder="Type salon name, email, phone or Customer ID…" autocomplete="off"></div>
          <div id="found" class="chips" style="margin-top:8px"></div>
        </div>
        <div class="field"><label>Salon / client name</label><input name="salonName" required value="${esc(inv.salonName || '')}"></div>
        <div class="field"><label>Phone / WhatsApp</label><input name="phone" value="${esc(inv.phone || '')}"></div>
        <div class="field"><label>Customer ID</label><input name="customerId" value="${esc(inv.customerId || '')}" readonly></div>
        <div class="field"><label>Owner</label><input name="ownerName" value="${esc(inv.ownerName || '')}"></div>
        <input type="hidden" name="accountUid" value="${esc(inv.accountUid || '')}">
        <input type="hidden" name="email" value="${esc(inv.email || '')}">
      </div>

      <div class="card-head" style="margin-top:22px"><h2>Invoice</h2></div>
      <div class="form-grid">
        <div class="field full"><label>Project / title</label><input name="project" value="${esc(inv.project || '')}" placeholder="Royal Cuts - DT Salon Management"></div>
        <div class="field"><label>Service / category</label><input name="category" value="${esc(inv.category || '')}"></div>
        <div class="field"><label>Package</label><input name="packageName" value="${esc(inv.packageName || '')}" placeholder="Monthly"></div>
        <div class="field"><label>Period from</label><input type="date" name="periodFrom" value="${inputDate(inv.periodFrom)}"></div>
        <div class="field"><label>Period to</label><input type="date" name="periodTo" value="${inputDate(inv.periodTo)}"></div>
        <div class="field"><label>Invoice date</label><input type="date" name="issuedAt" value="${inputDate(inv.issuedAt || new Date())}"></div>
        <div class="field"><label>Due</label><input name="dueText" value="${esc(inv.dueText || 'On receipt')}"></div>
        <div class="field"><label>Payment method</label><select name="paymentMethod">${METHODS.map((m) => `<option ${m === inv.paymentMethod ? 'selected' : ''}>${m}</option>`).join('')}</select></div>
      </div>

      <div class="card-head" style="margin-top:22px"><h2>Items</h2><div class="spacer"></div><button type="button" class="btn btn-ghost btn-sm" id="add">+ Add item</button></div>
      <table class="items-table" style="width:100%"><thead><tr class="cell-sub"><th align="left">Description</th><th align="left">Qty</th><th align="left">Rate (Rs)</th><th align="right">Amount</th><th></th></tr></thead>
        <tbody id="items">${(inv.items || []).map(itemRow).join('')}</tbody></table>

      <div class="grid grid-2" style="margin-top:18px;align-items:start">
        <div class="field"><label>Notes on the invoice (optional)</label><textarea name="notes">${esc(inv.notes || '')}</textarea></div>
        <div class="card" style="box-shadow:none;border:1px solid var(--line)">
          <div class="form-grid">
            <div class="field"><label>Discount (Rs)</label><input type="number" min="0" step="1" name="discount" value="${esc(inv.discount || 0)}"></div>
            <div class="field"><label>Paid so far (Rs)</label><input type="number" min="0" step="1" name="paid" value="${esc(inv.paid || 0)}"></div>
          </div>
          <dl class="kv" style="margin-top:12px" id="totals"></dl>
        </div>
      </div>
      <div class="actions" style="margin-top:18px">
        <button class="btn btn-primary" type="submit" id="save">${editing ? 'Save changes' : 'Create invoice'}</button>
        <a class="btn btn-ghost" href="${editing ? `#/invoice/${encodeURIComponent(editing.id)}` : '#/invoices'}">Cancel</a>
      </div>
    </form>`;

  const form = el.querySelector('#form');
  const items = el.querySelector('#items');

  const readItems = () => [...items.querySelectorAll('tr')].map((tr) => ({
    description: tr.querySelector('[name=desc]').value.trim(),
    qty: Number(tr.querySelector('[name=qty]').value) || 0,
    rate: Number(tr.querySelector('[name=rate]').value) || 0,
  }));

  const recalc = () => {
    items.querySelectorAll('tr').forEach((tr) => {
      const amount = (Number(tr.querySelector('[name=qty]').value) || 0) * (Number(tr.querySelector('[name=rate]').value) || 0);
      tr.querySelector('.amt').textContent = money(amount, false);
    });
    const t = invoiceTotals(readItems(), form.discount.value, form.paid.value);
    el.querySelector('#totals').innerHTML = `
      <dt>Subtotal</dt><dd>${money(t.subtotal)}</dd><dt>Total</dt><dd>${money(t.total)}</dd>
      <dt>Balance</dt><dd style="color:${t.balance > 0 ? 'var(--red)' : 'var(--green)'}">${money(t.balance)}</dd>
      <dt>Status</dt><dd><span class="pill st-${t.status}">${t.status}</span></dd>`;
  };
  form.addEventListener('input', recalc);
  items.addEventListener('click', (e) => {
    if (e.target.closest('[data-remove]') && items.children.length > 1) {
      e.target.closest('tr').remove();
      recalc();
    }
  });
  el.querySelector('#add').onclick = () => { items.insertAdjacentHTML('beforeend', itemRow()); recalc(); };
  recalc();

  // Salon search to fill the "bill to" block.
  const found = el.querySelector('#found');
  const search = debounce(async (text) => {
    if (text.trim().length < 2) { found.innerHTML = ''; return; }
    try {
      const results = await searchAccounts(text);
      found.innerHTML = results.slice(0, 8).map((a) => `<button type="button" class="chip" data-uid="${esc(a.id)}">${esc(a.salonName || a.email)} ${a.customerId ? `· ${esc(a.customerId)}` : ''}</button>`).join('') || '<span class="cell-sub">No match</span>';
      found.querySelectorAll('[data-uid]').forEach((b) => b.addEventListener('click', () => {
        const a = results.find((x) => x.id === b.dataset.uid);
        const d = draftFor(a, billing);
        ['salonName', 'phone', 'customerId', 'ownerName', 'accountUid', 'email', 'project', 'category', 'packageName'].forEach((k) => { form[k].value = d[k] || ''; });
        form.periodFrom.value = inputDate(d.periodFrom);
        form.periodTo.value = inputDate(d.periodTo);
        if (!editing) items.innerHTML = d.items.map(itemRow).join('');
        found.innerHTML = `<span class="cell-sub">Selected: ${esc(a.salonName)} (valid until ${fmtDate(a.expiresAt)})</span>`;
        recalc();
      }));
    } catch (e) { found.innerHTML = `<span class="cell-sub">${esc(errorMessage(e))}</span>`; }
  }, 350);
  el.querySelector('#find').addEventListener('input', (e) => search(e.target.value));

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!form.reportValidity()) return;
    const data = {
      accountUid: form.accountUid.value || null,
      email: form.email.value || '',
      salonName: form.salonName.value.trim(),
      ownerName: form.ownerName.value.trim(),
      phone: form.phone.value.trim(),
      customerId: form.customerId.value.trim(),
      project: form.project.value.trim(),
      category: form.category.value.trim(),
      packageName: form.packageName.value.trim(),
      periodFrom: fromInputDate(form.periodFrom.value),
      periodTo: fromInputDate(form.periodTo.value),
      issuedAt: fromInputDate(form.issuedAt.value) || new Date(),
      dueText: form.dueText.value.trim(),
      paymentMethod: form.paymentMethod.value,
      items: readItems().filter((i) => i.description),
      discount: Number(form.discount.value) || 0,
      paid: Number(form.paid.value) || 0,
      notes: form.notes.value.trim(),
    };
    if (!data.items.length) { toast('Add at least one item', 'error'); return; }
    const save = el.querySelector('#save');
    save.disabled = true;
    try {
      if (editing) {
        await updateInvoice(editing.id, data, billing);
        toast('Invoice saved', 'success');
        ctx.go(`#/invoice/${encodeURIComponent(editing.id)}`);
      } else {
        const id = await createInvoice(data, billing, ctx.user.uid);
        toast('Invoice created', 'success');
        ctx.go(`#/invoice/${encodeURIComponent(id)}`);
      }
    } catch (err) {
      toast(errorMessage(err), 'error');
      save.disabled = false;
    }
  });
}
