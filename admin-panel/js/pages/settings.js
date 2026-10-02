import { getConfig, saveConfig } from '../data.js';
import { esc, toast, errorMessage, imageFileToDataUrl } from '../util.js';

const TABS = [
  ['branding', 'App branding'],
  ['billing', 'Billing & invoices'],
  ['app', 'App control'],
];

function field(name, label, value, { type = 'text', help = '', full = false, textarea = false, placeholder = '' } = {}) {
  const input = textarea
    ? `<textarea name="${name}" placeholder="${esc(placeholder)}">${esc(value ?? '')}</textarea>`
    : `<input name="${name}" type="${type}" value="${esc(value ?? '')}" placeholder="${esc(placeholder)}" ${type === 'number' ? 'step="1"' : ''}>`;
  return `<div class="field ${full ? 'full' : ''}"><label>${esc(label)}</label>${input}${help ? `<div class="help">${esc(help)}</div>` : ''}</div>`;
}

function imageField(name, label, value, help) {
  return `<div class="field full"><label>${esc(label)}</label>
    <div class="img-pick">
      <img data-preview="${name}" src="${esc(value || '')}" class="${value ? '' : 'hidden'}" alt="">
      <input type="file" accept="image/*" data-file="${name}">
      <button type="button" class="btn btn-ghost btn-sm ${value ? '' : 'hidden'}" data-clear="${name}">Remove</button>
      <input type="hidden" name="${name}" value="${esc(value || '')}">
    </div>${help ? `<div class="help">${esc(help)}</div>` : ''}</div>`;
}

const FORMS = {
  branding: (c) => `
    <p class="muted">Shown in the salon app (login, approval and About screens) and used for support buttons.</p>
    <div class="form-grid">
      ${field('appName', 'App name', c.appName)}
      ${field('companyName', 'Company name', c.companyName)}
      ${field('contactNumber', 'Contact number', c.contactNumber, { placeholder: '0345-1873354' })}
      ${field('whatsapp', 'WhatsApp number', c.whatsapp, { placeholder: '0345-1873354' })}
      ${field('email', 'Support email', c.email, { type: 'email' })}
      ${field('website', 'Website', c.website, { placeholder: 'https://…' })}
      ${field('supportText', 'Support message', c.supportText, { full: true, textarea: true, placeholder: 'Support: 10am - 10pm, Monday to Saturday' })}
      ${imageField('logoDataUrl', 'Logo', c.logoDataUrl, 'Stored with the settings; keep it small (it is resized automatically).')}
    </div>`,
  billing: (c) => `
    <p class="muted">Printed on your subscription invoices to salon owners. Bank details are visible to admins only (and on invoices you send).</p>
    <div class="form-grid">
      ${field('companyName', 'Company name', c.companyName)}
      ${field('tagline', 'Tagline', c.tagline)}
      ${field('phone', 'Phone', c.phone, { placeholder: '+923451873354' })}
      ${field('address', 'Address', c.address)}
      ${field('bankAccountTitle', 'Account title', c.bankAccountTitle)}
      ${field('bankName', 'Bank name', c.bankName)}
      ${field('accountNumber', 'Account number', c.accountNumber)}
      ${field('iban', 'IBAN (optional)', c.iban)}
      ${field('signatoryName', 'Signatory name', c.signatoryName)}
      ${field('signatoryTitle', 'Signatory title', c.signatoryTitle)}
      ${field('invoicePrefix', 'Invoice number prefix', c.invoicePrefix, { help: 'Numbers look like DT-INV-2026-0001' })}
      ${field('defaultService', 'Default service name', c.defaultService)}
      ${field('terms', 'Terms & conditions (one per line)', c.terms, { full: true, textarea: true })}
      ${field('footerNote', 'A4 footer note', c.footerNote)}
      ${field('thankYou', 'Receipt thank-you line', c.thankYou)}
      ${imageField('logoDataUrl', 'Invoice logo', c.logoDataUrl, 'Leave empty to use the Digital Target logo.')}
      ${imageField('paymentQrDataUrl', 'Payment QR (e.g. Raast / bank QR)', c.paymentQrDataUrl, '')}
      ${imageField('signatureDataUrl', 'Signature', c.signatureDataUrl, 'A photo of your signature on white paper works well.')}
    </div>`,
  app: (c) => `
    <p class="muted">Controls every installed copy of the salon app the next time it goes online.</p>
    <div class="form-grid">
      ${field('latestVersionName', 'Latest version name', c.latestVersionName, { placeholder: '2.1.0' })}
      ${field('latestVersionCode', 'Latest version code', c.latestVersionCode, { type: 'number', help: 'Apps below this see an "update available" message' })}
      ${field('minVersionCode', 'Minimum version code', c.minVersionCode, { type: 'number', help: 'Apps below this must update before they can be used (0 = off)' })}
      ${field('offlineGraceDays', 'Offline days allowed', c.offlineGraceDays, { type: 'number', help: 'How often each phone must check its account online (default 7 = weekly). Changes in the panel reach phones instantly when online.' })}
      ${field('updateUrl', 'Update download link', c.updateUrl, { full: true, placeholder: 'https://… (APK download page)' })}
      ${field('updateMessage', 'Update message', c.updateMessage, { full: true, textarea: true })}
      ${field('notice', 'Notice to all salons', c.notice, { full: true, textarea: true, help: 'Shown once in every salon app (change the text to show a new notice; empty = none)' })}
    </div>`,
};

const NUMBER_FIELDS = ['latestVersionCode', 'minVersionCode', 'offlineGraceDays'];

export async function render(el, ctx) {
  ctx.setTitle('Settings');
  let tab = ctx.params.get('tab') || 'branding';
  el.innerHTML = `<div class="tabs" id="tabs">${TABS.map(([k, l]) => `<button data-t="${k}">${l}</button>`).join('')}</div><form class="card" id="form"></form>`;
  const form = el.querySelector('#form');

  async function show(name) {
    tab = name;
    el.querySelectorAll('#tabs button').forEach((b) => b.classList.toggle('active', b.dataset.t === name));
    history.replaceState(null, '', `#/settings?tab=${name}`);
    form.innerHTML = '<div class="loading"><div class="spinner"></div>Loading…</div>';
    const config = await getConfig(name);
    form.innerHTML = `${FORMS[name](config)}<div class="actions" style="margin-top:18px"><button class="btn btn-primary" type="submit">Save</button></div>`;
    form.querySelectorAll('[data-file]').forEach((input) => input.addEventListener('change', async () => {
      const key = input.dataset.file;
      try {
        const max = key === 'paymentQrDataUrl' ? 420 : 480;
        const dataUrl = await imageFileToDataUrl(input.files[0], max, key === 'signatureDataUrl' || key === 'logoDataUrl' ? 'image/png' : 'image/jpeg');
        form.querySelector(`[name="${key}"]`).value = dataUrl;
        const img = form.querySelector(`[data-preview="${key}"]`);
        img.src = dataUrl;
        img.classList.remove('hidden');
        form.querySelector(`[data-clear="${key}"]`).classList.remove('hidden');
      } catch (e) { toast(e.message, 'error'); }
    }));
    form.querySelectorAll('[data-clear]').forEach((b) => b.addEventListener('click', () => {
      const key = b.dataset.clear;
      form.querySelector(`[name="${key}"]`).value = '';
      form.querySelector(`[data-preview="${key}"]`).classList.add('hidden');
      b.classList.add('hidden');
    }));
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const data = {};
    new FormData(form).forEach((value, key) => {
      if (key.startsWith('__')) return;
      data[key] = NUMBER_FIELDS.includes(key) ? Number(value) || 0 : String(value).trim();
    });
    delete data[''];
    if (tab === 'app') data.offlineGraceDays = Math.min(365, Math.max(1, data.offlineGraceDays || 7));
    const size = JSON.stringify(data).length;
    if (size > 900000) { toast('Images are too large; choose smaller pictures', 'error'); return; }
    try {
      await saveConfig(tab, data);
      toast('Settings saved', 'success');
    } catch (err) { toast(errorMessage(err), 'error'); }
  });
  el.querySelector('#tabs').addEventListener('click', (e) => {
    const t = e.target.closest('[data-t]')?.dataset.t;
    if (t) show(t);
  });
  await show(tab);
}
