import {
  getAccount, getNotes, saveNotes, updateAccount, effectiveStatus, PLANS, STATUS_LABELS, planExpiry,
  listInvoices, deleteAccount, ensureIds, approveDevice, dismissDeviceRequest,
  approvedDevices, deviceLimit, deviceName, removeDevice, setDeviceLimit,
} from '../data.js';
import {
  esc, fmtDate, fmtDateTime, inputDate, fromInputDate, money, relativeDays, toast, errorMessage,
  confirmDialog, copyText, whatsappNumber, toDate,
} from '../util.js';
import { approveDialog, statusDialog, statusPill } from '../actions.js';

function initials(name) {
  return String(name || '?').trim().split(/\s+/).slice(0, 2).map((w) => w[0]?.toUpperCase() || '').join('') || '?';
}

export async function render(el, ctx) {
  const account = await getAccount(ctx.id);
  if (!account) {
    ctx.setTitle('Salon');
    el.innerHTML = '<div class="card empty">This salon account does not exist (it may have been deleted).</div>';
    return;
  }
  const [notes, invoices] = await Promise.all([getNotes(account.id), listInvoices({ accountUid: account.id, pageSize: 10 }).catch(() => ({ items: [] }))]);
  const status = effectiveStatus(account);
  ctx.setTitle(account.salonName || 'Salon');
  ctx.setActions(`<a class="btn btn-primary" href="#/invoice/new?uid=${encodeURIComponent(account.id)}">+ Invoice</a>`);

  const statusButtons = [];
  if (['PENDING', 'REJECTED'].includes(account.status)) statusButtons.push('<button class="btn btn-success" data-act="approve">Approve</button>');
  if (!['PENDING', 'REJECTED', 'APPROVED'].includes(account.status) || status === 'EXPIRED') statusButtons.push('<button class="btn btn-success" data-st="APPROVED">Re-activate</button>');
  if (account.status === 'APPROVED') statusButtons.push('<button class="btn btn-ghost" data-st="PAYMENT_PENDING">Payment pending</button>');
  if (account.status !== 'SUSPENDED' && account.status !== 'PENDING') statusButtons.push('<button class="btn btn-ghost" data-st="SUSPENDED">Suspend</button>');
  if (account.status === 'APPROVED') statusButtons.push('<button class="btn btn-ghost" data-st="EXPIRED">Mark expired</button>');
  if (account.status === 'PENDING') statusButtons.push('<button class="btn btn-ghost" data-st="REJECTED">Reject</button>');
  if (account.status !== 'BLOCKED') statusButtons.push('<button class="btn btn-danger" data-st="BLOCKED">Block</button>');
  const wa = whatsappNumber(account.phone);
  const devices = approvedDevices(account).filter((d) => d !== 'none');
  const limit = deviceLimit(account);

  el.innerHTML = `
    <div class="card">
      <div class="hero-head">
        <div class="avatar">${esc(initials(account.salonName))}</div>
        <div style="flex:1;min-width:200px">
          <h2>${esc(account.salonName || '—')}</h2>
          <div class="muted">${esc(account.ownerName || '')} · ${esc(account.email || '')}</div>
        </div>
        ${statusPill(status)}
      </div>
      ${account.messageToUser ? `<div class="notice info">Message shown in the app: ${esc(account.messageToUser)}</div>` : ''}
      ${account.pendingDeviceId ? `<div class="notice warn" id="device-request">
        <b>New phone wants to use this account:</b> ${esc(account.pendingDeviceModel || 'Unknown phone')}
        <span class="cell-sub">(asked ${fmtDateTime(account.pendingDeviceAt)}; ${devices.length} of ${limit} phone(s) in use)</span>
        <div class="actions" style="margin-top:10px">
          ${devices.length < limit ? '<button class="btn btn-success btn-sm" data-act="approve-device">Approve this phone</button>' : ''}
          <button class="btn ${devices.length < limit ? 'btn-ghost' : 'btn-success'} btn-sm" data-act="replace-device">Move account to this phone</button>
          <button class="btn btn-ghost btn-sm" data-act="dismiss-device">Ignore</button>
        </div>
        <div class="help">${devices.length < limit ? 'Approve adds it next to the current phone(s). ' : 'The phone limit is reached. '}Move = only this phone works, the others stop.</div>
      </div>` : ''}
      <div class="actions">
        ${statusButtons.join('')}
        ${wa ? `<a class="btn btn-wa" target="_blank" rel="noopener" href="https://wa.me/${wa}">WhatsApp</a>` : ''}
        ${account.phone ? `<a class="btn btn-ghost" href="tel:${esc(account.phone)}">Call</a>` : ''}
      </div>
    </div>

    <div class="grid grid-2" style="margin-top:16px">
      <form class="card" id="licence">
        <div class="card-head"><h2>Licence &amp; billing</h2></div>
        <div class="form-grid">
          <div class="field"><label>Plan</label><select name="plan">${Object.entries(PLANS).map(([k, p]) => `<option value="${k}" ${k === account.plan ? 'selected' : ''}>${esc(p.label)}</option>`).join('')}<option value="" ${account.plan ? '' : 'selected'}>—</option></select></div>
          <div class="field"><label>Valid until</label><input name="expiresAt" type="date" value="${inputDate(account.expiresAt)}"><div class="help">${account.expiresAt ? esc(relativeDays(account.expiresAt)) : 'Empty = no expiry'}</div></div>
          <div class="field"><label>Monthly fee (Rs)</label><input name="monthlyFee" type="number" min="0" step="1" value="${esc(account.monthlyFee ?? '')}"></div>
          <div class="field"><label>Payment status</label><select name="paymentStatus">${['PAID', 'PENDING', 'PARTIAL', 'OVERDUE'].map((s) => `<option ${s === account.paymentStatus ? 'selected' : ''}>${s}</option>`).join('')}</select></div>
          <div class="field"><label>Pending amount (Rs)</label><input name="pendingAmount" type="number" min="0" step="1" value="${esc(account.pendingAmount ?? '')}"></div>
          <div class="field"><label>Last payment</label><input name="lastPaymentAt" type="date" value="${inputDate(account.lastPaymentAt)}"></div>
          <div class="field full"><label>Message shown in the salon's app</label><textarea name="messageToUser" placeholder="Shown on the approval / payment screens">${esc(account.messageToUser || '')}</textarea></div>
        </div>
        <div class="actions" style="margin-top:14px">
          <button class="btn btn-primary" type="submit">Save</button>
          <button class="btn btn-ghost" type="button" id="renew">Renew by plan</button>
        </div>
      </form>

      <div class="card">
        <div class="card-head"><h2>IDs &amp; device</h2><div class="spacer"></div>${account.customerId ? '' : '<button class="btn btn-ghost btn-sm" id="ids">Create IDs</button>'}</div>
        <dl class="kv">
          <dt>Customer ID</dt><dd class="mono">${esc(account.customerId || '—')}</dd>
          <dt>Business ID</dt><dd class="mono">${esc(account.businessId || '—')}</dd>
          <dt>License ID</dt><dd class="mono">${esc(account.licenseId || '—')}</dd>
          <dt>Firebase UID</dt><dd class="mono">${esc(account.id)} <a href="#" id="copy-uid">copy</a></dd>
          <dt>Email</dt><dd>${esc(account.email || '—')}</dd>
          <dt>Registered</dt><dd>${fmtDateTime(account.createdAt)}</dd>
          <dt>Approved</dt><dd>${fmtDateTime(account.approvedAt)}</dd>
          <dt>Last seen</dt><dd>${fmtDateTime(account.lastSeenAt)}</dd>
          <dt>App version</dt><dd>${esc(account.appVersion || '—')}</dd>
        </dl>
        <div class="card-head" style="margin-top:14px"><h2>Phones</h2><div class="spacer"></div>
          <label class="cell-sub" style="display:flex;gap:6px;align-items:center">Allowed
            <input id="max-devices" type="number" min="1" max="20" value="${limit}" style="width:64px">
            <button class="btn btn-ghost btn-sm" id="save-max">Save</button></label></div>
        ${devices.length ? `<ul class="device-list">${devices.map((d) => `<li><span>📱 ${esc(deviceName(account, d))} <span class="cell-sub mono">${esc(String(d).slice(-8))}</span></span>
          <button class="btn btn-ghost btn-sm" data-remove-device="${esc(d)}">Remove</button></li>`).join('')}</ul>`
    : '<div class="empty">No phone approved. The next phone that signs in asks for approval.</div>'}
      </div>
    </div>

    <div class="grid grid-2" style="margin-top:16px">
      <form class="card" id="profile">
        <div class="card-head"><h2>Salon details</h2></div>
        <div class="form-grid">
          <div class="field"><label>Salon name</label><input name="salonName" required maxlength="120" value="${esc(account.salonName || '')}"></div>
          <div class="field"><label>Owner name</label><input name="ownerName" maxlength="120" value="${esc(account.ownerName || '')}"></div>
          <div class="field"><label>Phone / WhatsApp</label><input name="phone" maxlength="40" value="${esc(account.phone || '')}"></div>
          <div class="field"><label>City</label><input name="city" maxlength="80" value="${esc(account.city || '')}"></div>
          <div class="field full"><label>Address</label><input name="address" maxlength="300" value="${esc(account.address || '')}"></div>
        </div>
        <div class="actions" style="margin-top:14px"><button class="btn btn-primary" type="submit">Save details</button></div>
      </form>
      <form class="card" id="notes">
        <div class="card-head"><h2>Private notes</h2><div class="spacer"></div><span class="cell-sub">Only admins see these</span></div>
        <div class="field"><textarea name="notes" style="min-height:150px" placeholder="Payment habits, agreements, contact person…">${esc(notes)}</textarea></div>
        <div class="actions" style="margin-top:14px"><button class="btn btn-primary" type="submit">Save notes</button></div>
      </form>
    </div>

    <div class="card" style="margin-top:16px">
      <div class="card-head"><h2>Invoices</h2><div class="spacer"></div><a class="btn btn-ghost btn-sm" href="#/invoice/new?uid=${encodeURIComponent(account.id)}">+ New invoice</a></div>
      ${invoices.items.length ? `<table class="list"><thead><tr><th>Invoice</th><th>Date</th><th>Total</th><th>Paid</th><th>Balance</th><th>Status</th></tr></thead><tbody>${invoices.items.map((i) => `
        <tr class="row-link" data-href="#/invoice/${encodeURIComponent(i.id)}">
          <td data-label="Invoice" class="cell-title">${esc(i.number)}</td>
          <td data-label="Date">${fmtDate(i.issuedAt)}</td>
          <td data-label="Total">${money(i.total)}</td>
          <td data-label="Paid">${money(i.paid)}</td>
          <td data-label="Balance">${money(i.balance)}</td>
          <td data-label="Status"><span class="pill st-${esc(i.status)}">${esc(i.status)}</span></td>
        </tr>`).join('')}</tbody></table>` : '<div class="empty">No invoices for this salon yet.</div>'}
    </div>

    <div class="card" style="margin-top:16px">
      <div class="card-head"><h2>Danger zone</h2></div>
      <p class="muted">Deleting removes the online account only (the salon's data stays on their phone). If they sign in again they register as a new pending account.</p>
      <button class="btn btn-danger" id="delete">Delete account</button>
    </div>`;

  const reload = () => render(el, ctx).catch((e) => toast(errorMessage(e), 'error'));

  el.querySelectorAll('[data-href]').forEach((r) => r.addEventListener('click', () => ctx.go(r.dataset.href)));
  el.querySelector('[data-act="approve"]')?.addEventListener('click', async () => {
    if (await approveDialog(account)) { ctx.refreshBadge(); reload(); }
  });
  el.querySelectorAll('[data-st]').forEach((b) => b.addEventListener('click', async () => {
    if (await statusDialog(account, b.dataset.st)) { ctx.refreshBadge(); reload(); }
  }));
  el.querySelector('#copy-uid').addEventListener('click', (e) => { e.preventDefault(); copyText(account.id); });
  const deviceAction = (sel, title, text, label, fn, done) => el.querySelector(sel)?.addEventListener('click', async () => {
    if (title && !(await confirmDialog(title, text, label))) return;
    try {
      await fn();
      toast(done, 'success');
      ctx.refreshBadge();
      reload();
    } catch (e) { toast(errorMessage(e), 'error'); }
  });
  deviceAction('[data-act="approve-device"]', 'Approve this phone?',
    `${account.salonName || 'This salon'} will also open on ${account.pendingDeviceModel || 'the new phone'}.`, 'Approve',
    () => approveDevice(account), 'Phone approved');
  deviceAction('[data-act="replace-device"]', 'Move the account to this phone?',
    `Only ${account.pendingDeviceModel || 'the new phone'} will open ${account.salonName || 'this salon'}. The other phone(s) stop at their next check.`, 'Move',
    () => approveDevice(account, { replace: true }), 'Account moved to the new phone');
  el.querySelectorAll('[data-remove-device]').forEach((b) => b.addEventListener('click', async () => {
    const id = b.dataset.removeDevice;
    if (!(await confirmDialog('Remove this phone?', `${deviceName(account, id)} will stop opening the app at its next check.`, 'Remove', true))) return;
    try { await removeDevice(account, id); toast('Phone removed', 'success'); reload(); } catch (e) { toast(errorMessage(e), 'error'); }
  }));
  el.querySelector('#save-max')?.addEventListener('click', async () => {
    try { await setDeviceLimit(account, Number(el.querySelector('#max-devices').value)); toast('Phone limit saved', 'success'); reload(); } catch (e) { toast(errorMessage(e), 'error'); }
  });
  el.querySelector('[data-act="dismiss-device"]')?.addEventListener('click', async () => {
    try {
      await dismissDeviceRequest(account);
      toast('Request ignored');
      ctx.refreshBadge();
      reload();
    } catch (e) { toast(errorMessage(e), 'error'); }
  });
  el.querySelector('#ids')?.addEventListener('click', async () => {
    try {
      await updateAccount(account.id, await ensureIds(account));
      toast('IDs created', 'success');
      reload();
    } catch (e) { toast(errorMessage(e), 'error'); }
  });

  el.querySelector('#licence').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    try {
      await updateAccount(account.id, {
        plan: f.get('plan') || null,
        expiresAt: fromInputDate(f.get('expiresAt')),
        monthlyFee: Number(f.get('monthlyFee')) || 0,
        paymentStatus: f.get('paymentStatus'),
        pendingAmount: Number(f.get('pendingAmount')) || 0,
        lastPaymentAt: fromInputDate(f.get('lastPaymentAt')),
        messageToUser: String(f.get('messageToUser') || '').trim(),
      });
      toast('Licence saved', 'success');
      reload();
    } catch (err) { toast(errorMessage(err), 'error'); }
  });

  el.querySelector('#renew').addEventListener('click', async () => {
    const plan = el.querySelector('[name=plan]').value;
    if (!plan || plan === 'CUSTOM' || PLANS[plan]?.lifetime) { toast('Choose a monthly / yearly plan first, or set the date by hand', 'error'); return; }
    const current = toDate(account.expiresAt);
    const from = current && current > new Date() ? current : new Date();
    const until = planExpiry(plan, from);
    if (!(await confirmDialog('Renew licence', `Extend ${account.salonName} (${PLANS[plan].label}) until ${fmtDate(until)}?`, 'Renew'))) return;
    try {
      const patch = { plan, expiresAt: until };
      if (['EXPIRED', 'PAYMENT_PENDING'].includes(account.status)) Object.assign(patch, { status: 'APPROVED', messageToUser: '' });
      await updateAccount(account.id, patch);
      toast(`Valid until ${fmtDate(until)}`, 'success');
      reload();
    } catch (err) { toast(errorMessage(err), 'error'); }
  });

  el.querySelector('#profile').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    try {
      await updateAccount(account.id, Object.fromEntries(['salonName', 'ownerName', 'phone', 'city', 'address'].map((k) => [k, String(f.get(k) || '').trim()])));
      toast('Details saved', 'success');
      reload();
    } catch (err) { toast(errorMessage(err), 'error'); }
  });

  el.querySelector('#notes').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await saveNotes(account.id, String(new FormData(e.target).get('notes') || ''));
      toast('Notes saved', 'success');
    } catch (err) { toast(errorMessage(err), 'error'); }
  });

  el.querySelector('#delete').addEventListener('click', async () => {
    if (!(await confirmDialog('Delete account?', `Delete the online account of ${account.salonName || account.email}? This cannot be undone.`, 'Delete', true))) return;
    try {
      await deleteAccount(account.id);
      toast('Account deleted', 'success');
      ctx.refreshBadge();
      ctx.go('#/salons');
    } catch (err) { toast(errorMessage(err), 'error'); }
  });
}

export { STATUS_LABELS };
