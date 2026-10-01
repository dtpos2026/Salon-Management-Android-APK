// Renders subscription invoices in two designs - A4 (office / PDF) and a receipt strip
// (WhatsApp friendly) - and exports them as PNG, print/PDF or a share.
/* global qrcode, html2canvas */
import { esc, fmtDate, money, whatsappNumber, toDate } from './util.js';

const DEFAULT_LOGO = 'assets/digital-target.png';

export function verifyUrl(token) {
  const base = location.href.split('#')[0].split('?')[0].replace(/[^/]*$/, '');
  return `${base}verify.html?t=${encodeURIComponent(token)}`;
}

export function qrDataUrl(text, cellSize = 4) {
  const qr = qrcode(0, 'M');
  qr.addData(text);
  qr.make();
  return qr.createDataURL(cellSize, 1);
}

const STATUS_TEXT = { PAID: 'PAID', PARTIAL: 'PARTIALLY PAID', UNPAID: 'UNPAID' };

function period(inv) {
  if (!inv.periodFrom && !inv.periodTo) return '';
  return `${fmtDate(inv.periodFrom)} → ${fmtDate(inv.periodTo)}`;
}

function lines(text) {
  return String(text || '').split('\n').map((t) => t.trim()).filter(Boolean);
}

export function renderA4(inv, billing) {
  const logo = billing.logoDataUrl || DEFAULT_LOGO;
  const items = (inv.items || []).map((it, i) => `
    <tr>
      <td class="c-num">${i + 1}</td>
      <td>${esc(it.description)}</td>
      <td class="c-qty">${esc(it.qty)}</td>
      <td class="c-amt">${money(it.rate, false)}</td>
      <td class="c-amt strong">${money((Number(it.qty) || 0) * (Number(it.rate) || 0), false)}</td>
    </tr>`).join('');
  const meta = [
    ['Invoice date', fmtDate(inv.issuedAt)],
    ['Due date', inv.dueText || 'On receipt'],
    ['Service', inv.category],
    ['Payment', inv.paymentMethod],
    ['Package', inv.packageName],
    ['Period', period(inv)],
  ].filter(([, v]) => v).map(([k, v]) => `<div class="meta-row"><span>${esc(k)}</span><b>${esc(v)}</b></div>`).join('');
  const bank = [billing.bankAccountTitle, billing.bankName, billing.accountNumber, billing.iban].filter(Boolean)
    .map((v) => `<div>${esc(v)}</div>`).join('');
  const terms = lines(billing.terms).map((t, i) => `<div>${i + 1}. ${esc(t)}</div>`).join('');
  return `
  <div class="inv-a4">
    <header class="a4-head">
      <div class="a4-logo"><img src="${esc(logo)}" alt=""></div>
      <div class="a4-title">
        <div class="a4-word">INVOICE</div>
        <div class="a4-number">${esc(inv.number)}</div>
        <span class="pill pill-${esc(inv.status)}">${esc(STATUS_TEXT[inv.status] || inv.status)}</span>
      </div>
    </header>
    <section class="a4-parties">
      <div>
        <div class="cap">FROM</div>
        <div class="party">${esc(billing.companyName)}</div>
        ${billing.phone ? `<div class="muted">Phone: ${esc(billing.phone)}</div>` : ''}
        ${billing.address ? `<div class="muted">${esc(billing.address)}</div>` : ''}
      </div>
      <div>
        <div class="cap">BILL TO</div>
        <div class="party">${esc(inv.salonName)}</div>
        ${inv.phone ? `<div class="muted">${esc(inv.phone)}</div>` : ''}
        ${inv.customerId ? `<div class="muted">Customer ID: ${esc(inv.customerId)}</div>` : ''}
        ${inv.project ? `<div class="project"><span class="muted">Project:</span> <b>${esc(inv.project)}</b></div>` : ''}
      </div>
      <div class="a4-meta">${meta}</div>
    </section>
    <table class="a4-items">
      <thead><tr><th class="c-num">#</th><th>DESCRIPTION</th><th class="c-qty">QTY</th><th class="c-amt">RATE</th><th class="c-amt">AMOUNT</th></tr></thead>
      <tbody>${items}</tbody>
    </table>
    <section class="a4-bottom">
      <div class="a4-pay">
        ${billing.paymentQrDataUrl ? `<img class="pay-qr" src="${esc(billing.paymentQrDataUrl)}" alt="">` : ''}
        <div class="pay-text">
          <div class="cap">PAYMENT DETAILS</div>
          ${bank || '<div class="muted">Add bank details in Settings</div>'}
        </div>
        ${inv.verifyToken ? `<div class="verify"><img src="${qrDataUrl(verifyUrl(inv.verifyToken), 3)}" alt=""><div>Scan to verify</div></div>` : ''}
      </div>
      <div class="a4-totals">
        <div class="t-row"><span>Subtotal</span><span>${money(inv.subtotal)}</span></div>
        ${inv.discount ? `<div class="t-row"><span>Discount</span><span>- ${money(inv.discount)}</span></div>` : ''}
        <div class="t-total"><span>Total</span><span>${money(inv.total)}</span></div>
        <div class="t-row t-paid"><span>Paid</span><span>${money(inv.paid)}</span></div>
        <div class="t-row t-due"><span>Balance Due</span><span>${money(inv.balance)}</span></div>
      </div>
    </section>
    <section class="a4-sign">
      <div class="terms">${terms ? `<div class="cap">TERMS &amp; CONDITIONS</div>${terms}` : ''}${inv.notes ? `<div class="note">${esc(inv.notes)}</div>` : ''}</div>
      <div class="signature">
        ${billing.signatureDataUrl ? `<img src="${esc(billing.signatureDataUrl)}" alt="">` : '<div class="sig-space"></div>'}
        <div class="sig-line"></div>
        <div class="sig-name">${esc(billing.signatoryName || billing.companyName)}</div>
        <div class="muted">${esc(billing.signatoryTitle || 'Authorized Signatory')}</div>
      </div>
    </section>
    <footer class="a4-foot">
      <span>${esc(billing.footerNote || 'Thank you for your business!')}</span>
      <span>${esc([billing.companyName, billing.phone ? `Phone: ${billing.phone}` : ''].filter(Boolean).join(' | '))}</span>
    </footer>
  </div>`;
}

export function renderReceipt(inv, billing) {
  const logo = billing.logoDataUrl || DEFAULT_LOGO;
  const rows = [
    ['Invoice #', inv.number, true],
    ['Date', fmtDate(inv.issuedAt)],
    ['Client', inv.salonName, true],
    ['Phone', inv.phone],
    ['Project', inv.project],
    ['Category', inv.category],
    ['Package', inv.packageName],
    ['Period', period(inv)],
  ].filter(([, v]) => v).map(([k, v, b]) => `<div class="r-row"><span>${esc(k)}</span><span class="${b ? 'strong' : ''}">${esc(v)}</span></div>`).join('');
  const items = (inv.items || []).map((it) => `
    <div class="r-item">
      <div class="strong">${esc(it.description)}</div>
      <div class="r-row"><span class="muted">${esc(it.qty)} × ${money(it.rate)}</span><span class="strong">${money((Number(it.qty) || 0) * (Number(it.rate) || 0), false)}</span></div>
    </div>`).join('');
  return `
  <div class="inv-receipt">
    <img class="r-logo" src="${esc(logo)}" alt="">
    <div class="r-company">${esc(String(billing.companyName || '').toUpperCase())}</div>
    ${billing.tagline ? `<div class="r-tag">${esc(billing.tagline)}</div>` : ''}
    ${billing.phone ? `<div class="r-tag">${esc(billing.phone)}</div>` : ''}
    <div class="r-bar">INVOICE</div>
    ${rows}
    <div class="r-dash"></div>
    <div class="r-row r-head"><span>ITEM</span><span>AMOUNT</span></div>
    ${items}
    <div class="r-dash"></div>
    <div class="r-row"><span>Subtotal</span><span>${money(inv.subtotal, false)}</span></div>
    ${inv.discount ? `<div class="r-row"><span>Discount</span><span>- ${money(inv.discount, false)}</span></div>` : ''}
    <div class="r-total"><span>TOTAL</span><span>${money(inv.total)}</span></div>
    <div class="r-row"><span>Paid${inv.paymentMethod ? ` (${esc(inv.paymentMethod)})` : ''}</span><span>${money(inv.paid, false)}</span></div>
    <div class="r-row strong"><span>Balance</span><span>${money(inv.balance)}</span></div>
    <div class="r-stamp r-stamp-${esc(inv.status)}">${esc(STATUS_TEXT[inv.status] || inv.status)}</div>
    ${inv.verifyToken ? `<img class="r-qr" src="${qrDataUrl(verifyUrl(inv.verifyToken), 4)}" alt=""><div class="r-scan">Scan to verify this receipt</div>` : ''}
    <div class="r-dash"></div>
    <div class="r-thanks">${esc(billing.thankYou || 'Shukriya! Thank you')}</div>
    <div class="r-tag">${esc([billing.companyName, billing.phone ? `Phone: ${billing.phone}` : ''].filter(Boolean).join(' | '))}</div>
    <div class="r-powered">Powered by ${esc(billing.companyName || 'Digital Target')}</div>
  </div>`;
}

export async function elementToBlob(el) {
  const canvas = await html2canvas(el, { scale: 2, backgroundColor: '#ffffff', useCORS: true, logging: false });
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/png'));
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

/** Prints only [el] (the browser's dialog also offers "Save as PDF"). */
export function printElement(el, kind) {
  const holder = document.createElement('div');
  holder.id = 'print-root';
  holder.className = `print-${kind}`;
  holder.appendChild(el.cloneNode(true));
  document.body.appendChild(holder);
  document.body.classList.add('printing', `printing-${kind}`);
  const done = () => {
    document.body.classList.remove('printing', `printing-${kind}`);
    holder.remove();
    window.removeEventListener('afterprint', done);
  };
  window.addEventListener('afterprint', done);
  // Give images a moment to decode before printing.
  setTimeout(() => window.print(), 150);
  setTimeout(done, 60000);
}

export function invoiceMessage(inv, billing) {
  const status = inv.balance > 0 ? `Balance due: ${money(inv.balance)}` : 'Paid in full. Thank you!';
  return [
    'Assalam-o-Alaikum,',
    `${billing.companyName || 'DT Salon Management'} invoice ${inv.number} for ${inv.salonName}.`,
    `Total: ${money(inv.total)}. ${status}`,
    inv.verifyToken ? `Verify: ${verifyUrl(inv.verifyToken)}` : '',
  ].filter(Boolean).join('\n');
}

/** Shares the image (phones: WhatsApp/any app); otherwise downloads it and opens WhatsApp chat. */
export async function shareInvoice(blob, filename, inv, billing) {
  const text = invoiceMessage(inv, billing);
  const file = new File([blob], filename, { type: 'image/png' });
  if (navigator.canShare && navigator.canShare({ files: [file] })) {
    try {
      await navigator.share({ files: [file], text, title: inv.number });
      return 'shared';
    } catch (e) {
      if (e.name === 'AbortError') return 'cancelled';
    }
  }
  downloadBlob(blob, filename);
  openWhatsApp(inv.phone, text);
  return 'downloaded';
}

export function openWhatsApp(phone, text) {
  const number = whatsappNumber(phone);
  window.open(`https://wa.me/${number}?text=${encodeURIComponent(text)}`, '_blank', 'noopener');
}

export function invoiceFileName(inv, kind) {
  const date = toDate(inv.issuedAt) || new Date();
  return `${inv.number}_${kind === 'receipt' ? 'receipt' : 'A4'}_${fmtDate(date)}.png`;
}
