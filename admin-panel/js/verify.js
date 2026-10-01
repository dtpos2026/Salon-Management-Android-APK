// Public page behind the "Scan to verify" QR code on invoices.
import { initFirebase } from './fb.js';
import { getVerification } from './data.js';
import { esc, fmtDate, money } from './util.js';

const result = document.getElementById('result');
const token = new URLSearchParams(location.search).get('t') || '';

function show(html) { result.innerHTML = html; }

async function run() {
  if (!/^[A-Z0-9]{12,64}$/.test(token)) {
    show('<div class="verify-bad">Invalid verification code</div>');
    return;
  }
  if (!(await initFirebase())) {
    show('<p>This verification page is not configured yet.</p>');
    return;
  }
  try {
    const v = await getVerification(token);
    if (!v) {
      show('<div class="verify-bad">Not found</div><p>This invoice does not exist or was cancelled.</p>');
      return;
    }
    const paid = v.status === 'PAID';
    show(`
      <div class="${paid ? 'verify-ok' : 'verify-bad'}">${paid ? '✓ Genuine invoice · PAID' : `✓ Genuine invoice · ${esc(v.status)}`}</div>
      <div class="verify-result">
        <div class="row"><span>Invoice</span><b>${esc(v.number)}</b></div>
        <div class="row"><span>Issued by</span><b>${esc(v.company || 'Digital Target')}</b></div>
        <div class="row"><span>Client</span><b>${esc(v.salonName)}</b></div>
        <div class="row"><span>Date</span><b>${fmtDate(v.issuedAt)}</b></div>
        <div class="row"><span>Total</span><b>${money(v.total)}</b></div>
        <div class="row"><span>Paid</span><b>${money(v.paid)}</b></div>
        <div class="row"><span>Balance</span><b>${money(v.balance)}</b></div>
      </div>`);
  } catch (e) {
    show(`<div class="verify-bad">Could not check right now</div><p>${esc(e.message)}</p>`);
  }
}

run();
