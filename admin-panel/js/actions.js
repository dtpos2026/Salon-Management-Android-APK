// Dialogs shared by the dashboard, the salon list and the salon page.
import { modal, esc, toast, errorMessage, inputDate, fromInputDate, money, fmtDate, toDate } from './util.js';
import { PLANS, STATUS_LABELS, approveAccount, setStatus, planExpiry, recordPayment } from './data.js';

function planOptions(selected) {
  return Object.entries(PLANS).map(([key, p]) => `<option value="${key}" ${key === selected ? 'selected' : ''}>${esc(p.label)}</option>`).join('');
}

export async function approveDialog(account) {
  const form = await modal({
    title: `Approve ${account.salonName || account.email}`,
    confirm: 'Approve account',
    body: `
      <p class="muted">The salon can open the app as soon as you approve. Customer, Business and License IDs are created automatically.</p>
      <div class="form-grid">
        <div class="field"><label>Plan</label><select name="plan" required>${planOptions(account.plan || 'MONTHLY')}</select></div>
        <div class="field"><label>Monthly fee (Rs)</label><input name="monthlyFee" type="number" min="0" step="1" value="${esc(account.monthlyFee ?? '')}" placeholder="e.g. 1500"></div>
        <div class="field full custom-date hidden"><label>Valid until</label><input name="customDate" type="date" value="${inputDate(account.expiresAt)}"></div>
        <div class="field full"><label>Licence ends</label><div class="expiry-preview strong"></div></div>
        <div class="field full"><label>Message to the salon (optional)</label><textarea name="message" placeholder="Welcome to DT Salon Management!"></textarea></div>
      </div>`,
    onOpen: (f) => {
      const update = () => {
        const plan = f.plan.value;
        f.querySelector('.custom-date').classList.toggle('hidden', plan !== 'CUSTOM');
        const until = planExpiry(plan, new Date(), fromInputDate(f.customDate.value));
        f.querySelector('.expiry-preview').textContent = plan === 'LIFETIME' ? 'No expiry' : until ? fmtDate(until) : 'Choose a date';
      };
      f.plan.addEventListener('change', update);
      f.customDate.addEventListener('change', update);
      update();
    },
  });
  if (!form) return false;
  if (form.get('plan') === 'CUSTOM' && !form.get('customDate')) {
    toast('Choose the "valid until" date for a custom plan', 'error');
    return false;
  }
  try {
    await approveAccount(account, {
      plan: form.get('plan'),
      customDate: fromInputDate(form.get('customDate')),
      monthlyFee: form.get('monthlyFee'),
      message: String(form.get('message') || '').trim(),
    });
    toast('Account approved', 'success');
    return true;
  } catch (e) {
    toast(errorMessage(e), 'error');
    return false;
  }
}

const STATUS_HELP = {
  APPROVED: 'The salon can use the app again.',
  PAYMENT_PENDING: 'The app shows "payment pending" and stays closed until you re-activate it.',
  SUSPENDED: 'The app closes for this salon until you re-activate it. Data stays on their phone.',
  BLOCKED: 'The app closes permanently for this account.',
  EXPIRED: 'Marks the subscription as expired; the app closes.',
  REJECTED: 'The registration is declined.',
};

export async function statusDialog(account, status) {
  const form = await modal({
    title: `${STATUS_LABELS[status]}: ${account.salonName || account.email}`,
    confirm: status === 'APPROVED' ? 'Re-activate' : `Set ${STATUS_LABELS[status].toLowerCase()}`,
    danger: ['BLOCKED', 'REJECTED', 'SUSPENDED'].includes(status),
    body: `
      <p>${esc(STATUS_HELP[status] || '')}</p>
      <div class="field"><label>Message shown in the salon's app (optional)</label>
      <textarea name="message" placeholder="${status === 'PAYMENT_PENDING' ? 'Please pay Rs 1,500 by 5th to continue.' : ''}">${esc(account.messageToUser || '')}</textarea></div>`,
  });
  if (!form) return false;
  try {
    await setStatus(account, status, String(form.get('message') || '').trim());
    toast(`Status: ${STATUS_LABELS[status]}`, 'success');
    return true;
  } catch (e) {
    toast(errorMessage(e), 'error');
    return false;
  }
}

export async function paymentDialog(invoice, billing, account) {
  const plan = account?.plan && PLANS[account.plan];
  const canExtend = plan && !plan.lifetime && account.plan !== 'CUSTOM';
  const current = toDate(account?.expiresAt);
  const from = current && current > new Date() ? current : new Date();
  const form = await modal({
    title: `Record payment · ${invoice.number}`,
    confirm: 'Save payment',
    body: `
      <div class="form-grid">
        <div class="field"><label>Amount received (Rs)</label><input name="amount" type="number" min="1" step="1" required value="${esc(invoice.balance || '')}"></div>
        <div class="field"><label>Method</label><select name="method">
          ${['Cash', 'Bank Transfer', 'JazzCash', 'Easypaisa', 'Raast', 'Other'].map((m) => `<option ${m === invoice.paymentMethod ? 'selected' : ''}>${m}</option>`).join('')}
        </select></div>
        ${canExtend ? `<label class="check field full"><input type="checkbox" name="extend" checked> Extend licence by the plan (${esc(plan.label)}) to <b>${fmtDate(planExpiry(account.plan, from))}</b> and re-activate the app</label>` : ''}
      </div>
      <p class="muted">Balance before this payment: ${money(invoice.balance)}</p>`,
  });
  if (!form) return false;
  try {
    await recordPayment(invoice, { amount: Number(form.get('amount')), method: form.get('method'), extend: form.get('extend') === 'on', billing });
    toast('Payment recorded', 'success');
    return true;
  } catch (e) {
    toast(errorMessage(e), 'error');
    return false;
  }
}

export function statusPill(status) {
  return `<span class="pill st-${esc(status)}">${esc(STATUS_LABELS[status] || status)}</span>`;
}
