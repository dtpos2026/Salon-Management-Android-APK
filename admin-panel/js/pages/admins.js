import { listAdmins, addAdmin, removeAdmin } from '../data.js';
import { esc, fmtDate, toast, errorMessage, modal, confirmDialog, copyText } from '../util.js';

export async function render(el, ctx) {
  ctx.setTitle('Admins');
  const admins = await listAdmins();
  const actions = ctx.setActions('<button class="btn btn-primary" id="add-admin">+ Add admin</button>');
  el.innerHTML = `
    <div class="notice info">An admin can see and change every salon. Add only people you trust. They must sign in to this panel once to get their UID.</div>
    <div class="card">
      <div class="card-head"><h2>Your account</h2></div>
      <dl class="kv"><dt>Email</dt><dd>${esc(ctx.user.email || '')}</dd><dt>UID</dt><dd class="mono">${esc(ctx.user.uid)} <a href="#" id="copy">copy</a></dd></dl>
    </div>
    <div class="card">
      <table class="list"><thead><tr><th>Admin</th><th>UID</th><th>Added</th><th></th></tr></thead><tbody>
      ${admins.map((a) => `<tr>
        <td data-label="Admin"><div class="cell-title">${esc(a.name || a.email || '—')}</div><div class="cell-sub">${esc(a.email || '')}</div></td>
        <td data-label="UID" class="mono">${esc(a.id)}</td>
        <td data-label="Added">${fmtDate(a.createdAt)}</td>
        <td style="text-align:right">${a.id === ctx.user.uid ? '<span class="cell-sub">You</span>' : `<button class="btn btn-ghost btn-sm" data-remove="${esc(a.id)}">Remove</button>`}</td>
      </tr>`).join('')}
      </tbody></table>
    </div>`;
  el.querySelector('#copy').onclick = (e) => { e.preventDefault(); copyText(ctx.user.uid); };
  const addButton = actions.querySelector('#add-admin');
  if (addButton) addButton.onclick = async () => {
    const form = await modal({
      title: 'Add admin',
      confirm: 'Add admin',
      body: `<div class="form-grid">
        <div class="field full"><label>UID</label><input name="uid" required placeholder="Shown to them after they sign in"></div>
        <div class="field"><label>Email</label><input name="email" type="email" required></div>
        <div class="field"><label>Name</label><input name="name"></div></div>`,
    });
    if (!form) return;
    try {
      await addAdmin(String(form.get('uid')), String(form.get('email')), String(form.get('name') || ''), ctx.user.uid);
      toast('Admin added', 'success');
      render(el, ctx);
    } catch (e) { toast(errorMessage(e), 'error'); }
  };
  el.querySelectorAll('[data-remove]').forEach((b) => b.addEventListener('click', async () => {
    if (!(await confirmDialog('Remove admin?', 'They will lose access to the panel immediately.', 'Remove', true))) return;
    try {
      await removeAdmin(b.dataset.remove);
      toast('Admin removed', 'success');
      render(el, ctx);
    } catch (e) { toast(errorMessage(e), 'error'); }
  }));
}
