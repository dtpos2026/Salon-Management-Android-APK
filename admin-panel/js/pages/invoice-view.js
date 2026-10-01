import { getInvoice, getConfig, getAccount, deleteInvoice } from '../data.js';
import { esc, toast, errorMessage, confirmDialog } from '../util.js';
import {
  renderA4, renderReceipt, elementToBlob, downloadBlob, printElement, shareInvoice, openWhatsApp,
  invoiceMessage, invoiceFileName,
} from '../invoice-render.js';
import { paymentDialog } from '../actions.js';

const DESIGN_KEY = 'dt-admin-invoice-design';

export async function render(el, ctx) {
  const [inv, billing] = await Promise.all([getInvoice(ctx.id), getConfig('billing')]);
  if (!inv) { ctx.setTitle('Invoice'); el.innerHTML = '<div class="card empty">Invoice not found.</div>'; return; }
  ctx.setTitle(inv.number);
  let design = localStorage.getItem(DESIGN_KEY) || 'a4';

  el.innerHTML = `
    <div class="toolbar">
      <div class="chips" id="design">
        <button class="chip" data-d="a4">A4 invoice</button>
        <button class="chip" data-d="receipt">Receipt (WhatsApp)</button>
      </div>
      <div class="spacer" style="flex:1"></div>
      <div class="actions">
        ${inv.balance > 0 ? '<button class="btn btn-success" id="pay">Record payment</button>' : ''}
        <button class="btn btn-ghost" id="png">Download PNG</button>
        <button class="btn btn-ghost" id="print">Print / PDF</button>
        <button class="btn btn-ghost" id="share">Share</button>
        <button class="btn btn-wa" id="wa">WhatsApp</button>
        <a class="btn btn-ghost" href="#/invoice/${encodeURIComponent(inv.id)}/edit">Edit</a>
      </div>
    </div>
    <div class="preview-wrap"><div id="scale"><div id="doc"></div></div></div>
    <div style="margin-top:16px" class="actions">
      ${inv.accountUid ? `<a class="btn btn-ghost btn-sm" href="#/salon/${encodeURIComponent(inv.accountUid)}">Open salon</a>` : ''}
      <button class="btn btn-ghost btn-sm" id="delete" style="color:var(--red)">Delete invoice</button>
    </div>
    <div id="export-host" style="position:fixed;left:-10000px;top:0"></div>`;

  const docEl = el.querySelector('#doc');
  const scaleEl = el.querySelector('#scale');
  const draw = () => {
    docEl.innerHTML = design === 'a4' ? renderA4(inv, billing) : renderReceipt(inv, billing);
    el.querySelectorAll('#design .chip').forEach((c) => c.classList.toggle('active', c.dataset.d === design));
    fit();
  };
  // Scale the A4 page down to fit small screens (exports always use full size).
  const fit = () => {
    if (!document.body.contains(docEl)) { window.removeEventListener('resize', fit); return; }
    const wrapWidth = el.querySelector('.preview-wrap').clientWidth - 24;
    const natural = design === 'a4' ? 794 : 380;
    const scale = Math.min(1, wrapWidth / natural);
    docEl.style.transform = scale < 1 ? `scale(${scale})` : '';
    docEl.style.transformOrigin = 'top left';
    scaleEl.style.width = `${natural * scale}px`;
    scaleEl.style.height = `${docEl.firstElementChild.offsetHeight * scale}px`;
  };
  window.addEventListener('resize', fit, { passive: true });
  el.querySelector('#design').addEventListener('click', (e) => {
    const d = e.target.closest('[data-d]')?.dataset.d;
    if (!d) return;
    design = d;
    localStorage.setItem(DESIGN_KEY, d);
    draw();
  });
  draw();
  // Images (logo, QR) change the height once loaded.
  docEl.querySelectorAll('img').forEach((img) => img.addEventListener('load', fit));

  const exportBlob = async () => {
    const host = el.querySelector('#export-host');
    host.innerHTML = design === 'a4' ? renderA4(inv, billing) : renderReceipt(inv, billing);
    await Promise.all([...host.querySelectorAll('img')].map((img) => (img.complete ? null : new Promise((r) => { img.onload = r; img.onerror = r; }))));
    try {
      return await elementToBlob(host.firstElementChild);
    } finally {
      host.innerHTML = '';
    }
  };

  const busy = async (btn, work) => {
    btn.disabled = true;
    try { await work(); } catch (e) { toast(errorMessage(e), 'error'); } finally { btn.disabled = false; }
  };

  el.querySelector('#png').onclick = (e) => busy(e.currentTarget, async () => {
    downloadBlob(await exportBlob(), invoiceFileName(inv, design));
  });
  el.querySelector('#print').onclick = () => printElement(docEl.firstElementChild, design);
  el.querySelector('#share').onclick = (e) => busy(e.currentTarget, async () => {
    const result = await shareInvoice(await exportBlob(), invoiceFileName(inv, design), inv, billing);
    if (result === 'downloaded') toast('Image downloaded - attach it in WhatsApp');
  });
  el.querySelector('#wa').onclick = () => {
    if (!inv.phone) toast('No phone number on this invoice; choose the chat in WhatsApp');
    openWhatsApp(inv.phone, invoiceMessage(inv, billing));
  };
  el.querySelector('#pay')?.addEventListener('click', async () => {
    const account = inv.accountUid ? await getAccount(inv.accountUid) : null;
    if (await paymentDialog(inv, billing, account)) render(el, ctx);
  });
  el.querySelector('#delete').onclick = async () => {
    if (!(await confirmDialog('Delete invoice?', `Delete ${inv.number}? Its QR code will stop verifying.`, 'Delete', true))) return;
    try {
      await deleteInvoice(inv);
      toast('Invoice deleted', 'success');
      ctx.go('#/invoices');
    } catch (err) { toast(errorMessage(err), 'error'); }
  };
}

export { esc };
