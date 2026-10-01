// Small UI and formatting helpers shared by all pages. All user data is escaped with esc().

export function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

export function $(selector, root = document) { return root.querySelector(selector); }
export function $$(selector, root = document) { return [...root.querySelectorAll(selector)]; }

export function toDate(value) {
  if (!value) return null;
  if (value instanceof Date) return value;
  if (typeof value.toDate === 'function') return value.toDate();
  if (typeof value === 'number' || typeof value === 'string') {
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  return null;
}

const pad = (n) => String(n).padStart(2, '0');

/** 2026-09-29 */
export function fmtDate(value) {
  const d = toDate(value);
  return d ? `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` : '—';
}

export function fmtDateTime(value) {
  const d = toDate(value);
  return d ? `${fmtDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}` : '—';
}

/** Value for <input type="date">. */
export function inputDate(value) {
  const d = toDate(value);
  return d ? fmtDate(d) : '';
}

/** End of the chosen day in local time. */
export function fromInputDate(text) {
  if (!text) return null;
  const [y, m, d] = text.split('-').map(Number);
  return new Date(y, m - 1, d, 23, 59, 59);
}

export function addMonths(date, months) {
  const d = new Date(date.getTime());
  const day = d.getDate();
  d.setMonth(d.getMonth() + months);
  if (d.getDate() < day) d.setDate(0);
  return d;
}

export function addDays(date, days) {
  return new Date(date.getTime() + days * 86400000);
}

export function endOfDay(date) {
  const d = new Date(date.getTime());
  d.setHours(23, 59, 59, 0);
  return d;
}

export function daysBetween(from, to) {
  return Math.round((toDate(to) - toDate(from)) / 86400000);
}

export function relativeDays(value) {
  const d = toDate(value);
  if (!d) return '';
  const days = Math.ceil((d - new Date()) / 86400000);
  if (days === 0) return 'today';
  if (days > 0) return `in ${days} day${days === 1 ? '' : 's'}`;
  return `${-days} day${days === -1 ? '' : 's'} ago`;
}

/** Rs 4,000 */
export function money(value, withSymbol = true) {
  const n = Math.round(Number(value) || 0);
  const text = n.toLocaleString('en-US');
  return withSymbol ? `Rs ${text}` : text;
}

export function num(value) {
  const n = Number(String(value ?? '').replace(/[^0-9.-]/g, ''));
  return Number.isFinite(n) ? n : 0;
}

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/** Unguessable code from the browser's cryptographic random generator. */
export function randomCode(length) {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return [...bytes].map((b) => ALPHABET[b % ALPHABET.length]).join('');
}

/** 03001234567 / +92 300 1234567 -> 923001234567 (WhatsApp format). */
export function whatsappNumber(raw, countryCode = '92') {
  const trimmed = String(raw || '').trim();
  let digits = trimmed.replace(/\D/g, '');
  if (!digits) return '';
  if (trimmed.startsWith('+')) { /* already international */ } else if (digits.startsWith('00')) digits = digits.slice(2);
  else if (digits.startsWith(countryCode) && digits.length >= 11) { /* ok */ } else if (digits.startsWith('0')) digits = countryCode + digits.slice(1);
  else if (digits.length === 10 && digits.startsWith('3')) digits = countryCode + digits;
  return digits.length >= 10 && digits.length <= 15 ? digits : '';
}

export function debounce(fn, ms = 300) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}

export function toast(message, kind = 'info') {
  let host = $('#toasts');
  if (!host) {
    host = document.createElement('div');
    host.id = 'toasts';
    document.body.appendChild(host);
  }
  const el = document.createElement('div');
  el.className = `toast toast-${kind}`;
  el.textContent = message;
  host.appendChild(el);
  setTimeout(() => el.classList.add('show'), 10);
  setTimeout(() => {
    el.classList.remove('show');
    setTimeout(() => el.remove(), 300);
  }, kind === 'error' ? 6000 : 3200);
}

/**
 * Modal dialog. `body` is trusted HTML built with esc(). Resolves with the FormData of the
 * dialog's form when confirmed, or null when cancelled.
 */
export function modal({ title, body, confirm = 'Save', danger = false, wide = false, onOpen }) {
  return new Promise((resolve) => {
    const wrap = document.createElement('div');
    wrap.className = 'modal-backdrop';
    wrap.innerHTML = `
      <form class="modal ${wide ? 'modal-wide' : ''}" novalidate>
        <div class="modal-head"><h3>${esc(title)}</h3><button type="button" class="icon-btn" data-close aria-label="Close">✕</button></div>
        <div class="modal-body">${body}</div>
        <div class="modal-foot">
          <button type="button" class="btn btn-ghost" data-close>Cancel</button>
          <button type="submit" class="btn ${danger ? 'btn-danger' : 'btn-primary'}">${esc(confirm)}</button>
        </div>
      </form>`;
    const close = (value) => {
      wrap.remove();
      document.removeEventListener('keydown', onKey);
      resolve(value);
    };
    const onKey = (e) => { if (e.key === 'Escape') close(null); };
    document.addEventListener('keydown', onKey);
    wrap.addEventListener('click', (e) => { if (e.target === wrap || e.target.closest('[data-close]')) close(null); });
    const form = $('form', wrap);
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      if (!form.reportValidity()) return;
      close(new FormData(form));
    });
    document.body.appendChild(wrap);
    onOpen?.(form);
    setTimeout(() => $('input:not([type=hidden]),select,textarea', form)?.focus(), 30);
  });
}

export async function confirmDialog(title, message, confirm = 'Confirm', danger = false) {
  const result = await modal({ title, body: `<p>${esc(message)}</p>`, confirm, danger });
  return result !== null;
}

/** Shrinks an uploaded image and returns a PNG/JPEG data URL (stored in Firestore). */
export function imageFileToDataUrl(file, maxSize = 480, type = 'image/png') {
  return new Promise((resolve, reject) => {
    if (!file || !file.type.startsWith('image/')) { reject(new Error('Please choose an image file')); return; }
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => {
      const img = new Image();
      img.onerror = () => reject(new Error('Could not read the image'));
      img.onload = () => {
        const scale = Math.min(1, maxSize / Math.max(img.width, img.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(img.width * scale);
        canvas.height = Math.round(img.height * scale);
        canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL(type, 0.9));
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });
}

export function copyText(text) {
  navigator.clipboard?.writeText(text).then(() => toast('Copied'), () => toast('Copy failed', 'error'));
}

/** Friendly message for Firestore errors (missing index links are kept clickable in the console). */
export function errorMessage(error) {
  const code = error?.code || '';
  if (code === 'permission-denied') return 'Permission denied. Are the Firestore rules published and is this account an admin?';
  if (code === 'unavailable') return 'Cannot reach Firebase. Check the internet connection.';
  if (code === 'failed-precondition' && /index/i.test(error.message)) {
    console.warn(error.message);
    return 'This list needs a Firestore index. Deploy firebase/firestore.indexes.json (see the guide) or open the link in the browser console.';
  }
  return error?.message || String(error);
}
