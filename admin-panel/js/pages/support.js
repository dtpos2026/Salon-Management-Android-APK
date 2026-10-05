// Support inbox: one live thread per salon. Replies appear in the salon's app at once.
import {
  collection, doc, query, orderBy, limit, onSnapshot, addDoc, updateDoc, serverTimestamp, getDocs, writeBatch,
} from '../../vendor/firebase.js';
import { firebase } from '../fb.js';
import { esc, fmtDateTime, toast, errorMessage, confirmDialog } from '../util.js';
import { getConfig, saveConfig } from '../data.js';

const db = () => firebase().db;
let stop = [];

function cleanup() {
  stop.forEach((fn) => fn());
  stop = [];
}

export async function render(el, ctx) {
  cleanup();
  window.addEventListener('hashchange', () => { if (!location.hash.startsWith('#/support')) cleanup(); }, { once: true });
  ctx.setTitle('Support');
  const ai = await getConfig('ai').catch(() => ({}));
  ctx.setActions(`<label class="cell-sub" style="display:flex;gap:8px;align-items:center">
    <input type="checkbox" id="ai-reply" ${ai.supportAutoReply ? 'checked' : ''}> AI replies first</label>`);
  document.getElementById('ai-reply')?.addEventListener('change', async (e) => {
    try {
      await saveConfig('ai', { ...ai, supportAutoReply: e.target.checked });
      toast(e.target.checked ? 'AI will answer new questions first' : 'AI auto-reply off', 'success');
    } catch (err) { toast(errorMessage(err), 'error'); }
  });
  let current = ctx.params.get('uid');
  el.innerHTML = `
    <div class="support-grid">
      <div class="card support-threads"><div class="card-head"><h2>Conversations</h2></div><div id="threads"><div class="loading"><div class="spinner"></div>Loading…</div></div></div>
      <div class="card support-chat"><div id="chat" class="empty">Choose a conversation.</div></div>
    </div>`;
  const threadsEl = el.querySelector('#threads');
  const chatEl = el.querySelector('#chat');

  stop.push(onSnapshot(query(collection(db(), 'support'), orderBy('lastAt', 'desc'), limit(100)), (snap) => {
    const threads = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    threadsEl.innerHTML = threads.length ? `<ul class="thread-list">${threads.map((t) => `
      <li class="${t.id === current ? 'active' : ''}" data-uid="${esc(t.id)}">
        <div class="cell-title">${esc(t.salonName || t.email || t.id)} ${t.unreadForAdmin ? '<span class="pill st-PENDING">New</span>' : ''}</div>
        <div class="cell-sub">${t.lastFrom === 'user' ? '' : (t.lastFrom === 'ai' ? 'AI: ' : 'You: ')}${esc(t.lastMessage || '')}</div>
        <div class="cell-sub">${fmtDateTime(t.lastAt)}</div></li>`).join('')}</ul>`
      : '<div class="empty">No messages yet. Salons write from Help &amp; support in the app.</div>';
    threadsEl.querySelectorAll('[data-uid]').forEach((li) => li.addEventListener('click', () => open(li.dataset.uid, threads.find((t) => t.id === li.dataset.uid))));
    if (current && !chatEl.dataset.uid) open(current, threads.find((t) => t.id === current));
  }, (e) => { threadsEl.innerHTML = `<div class="empty">${esc(errorMessage(e))}</div>`; }));

  let stopChat = null;
  function open(uid, thread) {
    current = uid;
    chatEl.dataset.uid = uid;
    chatEl.className = '';
    threadsEl.querySelectorAll('[data-uid]').forEach((li) => li.classList.toggle('active', li.dataset.uid === uid));
    chatEl.innerHTML = `
      <div class="card-head"><h2>${esc(thread?.salonName || uid)}</h2><div class="spacer"></div>
        <button class="btn btn-ghost btn-sm" id="clear-chat">Clear chat</button>
        <a class="btn btn-ghost btn-sm" href="#/salon/${encodeURIComponent(uid)}">Open salon</a></div>
      <div class="bubbles" id="bubbles"></div>
      <form id="reply" class="reply-box"><textarea name="text" maxlength="2000" placeholder="Write a reply…" required></textarea>
        <button class="btn btn-primary" type="submit">Send</button></form>`;
    if (thread?.unreadForAdmin) updateDoc(doc(db(), 'support', uid), { unreadForAdmin: false }).catch(() => null);
    stopChat?.();
    stopChat = onSnapshot(query(collection(db(), 'support', uid, 'messages'), orderBy('at', 'asc'), limit(300)), (snap) => {
      const box = chatEl.querySelector('#bubbles');
      if (!box) return;
      box.innerHTML = snap.docs.map((d) => {
        const m = d.data();
        const who = m.from === 'user' ? 'Salon' : m.from === 'ai' ? 'AI assistant' : 'You';
        return `<div class="bubble from-${esc(m.from)}"><div class="bubble-who">${who}</div>${esc(m.text).replace(/\n/g, '<br>')}<div class="bubble-at">${fmtDateTime(m.at)}</div></div>`;
      }).join('') || '<div class="empty">No messages.</div>';
      box.scrollTop = box.scrollHeight;
    });
    stop.push(() => stopChat?.());
    chatEl.querySelector('#clear-chat').addEventListener('click', async () => {
      if (!await confirmDialog('Clear chat?', 'All messages of this conversation are deleted for you and the salon. This cannot be undone.', 'Clear chat')) return;
      try {
        const snap = await getDocs(collection(db(), 'support', uid, 'messages'));
        for (let i = 0; i < snap.docs.length; i += 400) {
          const batch = writeBatch(db());
          snap.docs.slice(i, i + 400).forEach((d) => batch.delete(d.ref));
          await batch.commit();
        }
        await updateDoc(doc(db(), 'support', uid), { lastMessage: '', lastAt: serverTimestamp(), unreadForAdmin: false });
        toast('Chat cleared', 'success');
      } catch (err) { toast(errorMessage(err), 'error'); }
    });
    chatEl.querySelector('#reply').addEventListener('submit', async (e) => {
      e.preventDefault();
      const text = e.target.text.value.trim();
      if (!text) return;
      e.target.text.value = '';
      try {
        await addDoc(collection(db(), 'support', uid, 'messages'), { from: 'admin', text, at: serverTimestamp() });
        await updateDoc(doc(db(), 'support', uid), { lastMessage: text.slice(0, 200), lastFrom: 'admin', lastAt: serverTimestamp(), unreadForUser: true, unreadForAdmin: false });
      } catch (err) {
        e.target.text.value = text;
        toast(errorMessage(err), 'error');
      }
    });
  }
}
