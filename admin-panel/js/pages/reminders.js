// Reminders and messages to salon owners on WhatsApp: payment due, licence expiring or ended,
// and free-text announcements. WhatsApp opens each chat with the message ready; the admin sends.
import { listAccounts, getConfig } from '../data.js';
import { esc, fmtDate, money, whatsappNumber, errorMessage } from '../util.js';

const GROUPS = [
  ['PAYMENT_PENDING', 'Payment pending'],
  ['SOON', 'Expiring in 7 days'],
  ['LAPSED', 'Licence ended'],
  ['APPROVED', 'All active salons'],
];

const TEMPLATES = {
  PAYMENT_PENDING: 'Assalam o Alaikum {owner},\nYour {app} subscription payment of {amount} for {salon} is pending. Please pay to keep the app running.\nCustomer ID: {customerId}\nThank you, {company}',
  SOON: 'Assalam o Alaikum {owner},\nYour {app} licence for {salon} ends on {expiry}. Please renew to avoid interruption.\nCustomer ID: {customerId}\nThank you, {company}',
  LAPSED: 'Assalam o Alaikum {owner},\nYour {app} licence for {salon} ended on {expiry}. Your data is safe on your phone; renew now to continue.\nCustomer ID: {customerId}\nThank you, {company}',
  APPROVED: 'Assalam o Alaikum {owner},\n\n— {company}',
};

function fill(template, a, brand) {
  return template
    .replaceAll('{owner}', a.ownerName || a.salonName || '')
    .replaceAll('{salon}', a.salonName || '')
    .replaceAll('{amount}', money(a.pendingAmount || a.monthlyFee || 0))
    .replaceAll('{expiry}', a.expiresAt ? fmtDate(a.expiresAt) : '—')
    .replaceAll('{customerId}', a.customerId || '—')
    .replaceAll('{app}', brand.appName || 'DT Salon Management')
    .replaceAll('{company}', brand.companyName || 'Digital Target');
}

export async function render(el, ctx) {
  ctx.setTitle('Reminders');
  let group = ctx.params.get('group') || 'PAYMENT_PENDING';
  const brand = await getConfig('branding').catch(() => ({}));
  const sent = new Set();
  el.innerHTML = `
    <div class="chips" id="chips" style="margin-bottom:14px">${GROUPS.map(([k, l]) => `<button class="chip ${k === group ? 'active' : ''}" data-g="${k}">${l}</button>`).join('')}</div>
    <div class="grid grid-2">
      <div class="card">
        <div class="card-head"><h2>Message</h2></div>
        <div class="field"><textarea id="msg" style="min-height:190px"></textarea>
          <div class="help">Placeholders: {owner} {salon} {amount} {expiry} {customerId} {app} {company}</div></div>
        <div class="actions" style="margin-top:10px"><button class="btn btn-ghost btn-sm" id="reset">Reset to template</button></div>
      </div>
      <div class="card">
        <div class="card-head"><h2>Salons</h2><div class="spacer"></div><span class="cell-sub" id="count"></span></div>
        <div id="list"><div class="loading"><div class="spinner"></div>Loading…</div></div>
        <div class="help" style="margin-top:8px">Each button opens WhatsApp with the message for that salon. Press send in WhatsApp.</div>
      </div>
    </div>`;
  const msg = el.querySelector('#msg');
  const list = el.querySelector('#list');
  let accounts = [];

  const draw = () => {
    const withPhone = accounts.filter((a) => whatsappNumber(a.phone));
    el.querySelector('#count').textContent = `${withPhone.length} with WhatsApp · ${sent.size} sent`;
    list.innerHTML = withPhone.length ? `<ul class="device-list">${withPhone.map((a) => `
      <li><span><b>${esc(a.salonName || '—')}</b> <span class="cell-sub">${esc(a.ownerName || '')} · ${esc(a.phone || '')}${a.expiresAt ? ` · until ${fmtDate(a.expiresAt)}` : ''}</span></span>
        <button class="btn ${sent.has(a.id) ? 'btn-ghost' : 'btn-wa'} btn-sm" data-send="${esc(a.id)}">${sent.has(a.id) ? 'Sent ✓ again' : 'WhatsApp'}</button></li>`).join('')}</ul>`
      : '<div class="empty">No salons in this group have a phone number.</div>';
    list.querySelectorAll('[data-send]').forEach((b) => b.addEventListener('click', () => {
      const a = accounts.find((x) => x.id === b.dataset.send);
      window.open(`https://wa.me/${whatsappNumber(a.phone)}?text=${encodeURIComponent(fill(msg.value, a, brand))}`, '_blank', 'noopener');
      sent.add(a.id);
      draw();
    }));
  };

  const load = async () => {
    msg.value = TEMPLATES[group];
    list.innerHTML = '<div class="loading"><div class="spinner"></div>Loading…</div>';
    try {
      const page = await listAccounts({ filter: group, pageSize: 200 });
      accounts = page.items;
      draw();
    } catch (e) {
      list.innerHTML = `<div class="empty">${esc(errorMessage(e))}</div>`;
    }
  };
  el.querySelector('#reset').onclick = () => { msg.value = TEMPLATES[group]; };
  el.querySelectorAll('[data-g]').forEach((c) => c.addEventListener('click', () => {
    group = c.dataset.g;
    el.querySelectorAll('[data-g]').forEach((x) => x.classList.toggle('active', x === c));
    sent.clear();
    load();
  }));
  await load();
}
