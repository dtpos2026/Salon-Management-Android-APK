// Support inbox: one live thread per salon. Replies appear in the salon's app at once.
// A conversation can be cleared (messages only) or deleted (gone from the list); conversations
// of salons that no longer exist are marked and can be deleted together.
import {
  collection, doc, query, orderBy, limit, onSnapshot, addDoc, updateDoc, serverTimestamp, getDocs, writeBatch,
} from '../../vendor/firebase.js';
import { firebase } from '../fb.js';
import { esc, fmtDateTime, toast, errorMessage, confirmDialog } from '../util.js';
import { getConfig, saveConfig, findAccount, deleteSupportThread } from '../data.js';
import { accountsStore } from '../store.js';

const db = () => firebase().db;
let stop = [];

function cleanup() {
  stop.forEach((fn) => fn());
  stop = [];
}

export async function render(el, ctx) {
  cleanup();
  ctx.onLeave(cleanup);
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

  let threads = [];
  let accounts = {};
  let gone = new Set(); // salons the server confirms no longer exist
  let stopChat = null;
  const nameOf = (t) => accounts[t.id]?.salonName || t.salonName || t.email || t.id;

  /** Empties the right side after its conversation was deleted. */
  function closeChat() {
    stopChat?.();
    stopChat = null;
    current = null;
    delete chatEl.dataset.uid;
    chatEl.className = 'empty';
    chatEl.textContent = 'Choose a conversation.';
    if (location.hash.includes('?')) history.replaceState(null, '', '#/support');
  }

  function renderThreads() {
    const orphans = threads.filter((t) => gone.has(t.id));
    threadsEl.innerHTML = (orphans.length
      ? `<div class="notice info">${orphans.length} conversation(s) of salons that no longer exist. <button class="btn btn-ghost btn-sm" id="delete-orphans">Delete them</button></div>`
      : '') + (threads.length ? `<ul class="thread-list">${threads.map((t) => `
      <li class="${t.id === current ? 'active' : ''}" data-uid="${esc(t.id)}">
        <div class="cell-title">${esc(nameOf(t))} ${t.unreadForAdmin ? '<span class="pill st-PENDING">New</span>' : ''} ${gone.has(t.id) ? '<span class="pill st-EXPIRED">Salon deleted</span>' : ''}</div>
        <div class="cell-sub">${t.lastFrom === 'user' ? '' : (t.lastFrom === 'ai' ? 'AI: ' : 'You: ')}${esc(t.lastMessage || '')}</div>
        <div class="cell-sub">${fmtDateTime(t.lastAt)}</div></li>`).join('')}</ul>`
      : '<div class="empty">No messages yet. Salons write from Help &amp; support in the app.</div>');
    threadsEl.querySelectorAll('[data-uid]').forEach((li) => li.addEventListener('click', () => open(li.dataset.uid, threads.find((t) => t.id === li.dataset.uid))));
    threadsEl.querySelector('#delete-orphans')?.addEventListener('click', async (ev) => {
      if (!(await confirmDialog('Delete old conversations?', `${orphans.length} conversation(s) of salons that no longer exist are deleted with all their messages. This cannot be undone.`, 'Delete', true))) return;
      ev.target.disabled = true;
      try {
        if (orphans.some((t) => t.id === current)) closeChat();
        await Promise.all(orphans.map((t) => deleteSupportThread(t.id)));
        toast('Old conversations deleted', 'success');
      } catch (err) { ev.target.disabled = false; toast(errorMessage(err), 'error'); }
    });
    if (current && !chatEl.dataset.uid) open(current, threads.find((t) => t.id === current));
  }

  // Each update looks up the salons of the conversations; only the newest lookup is drawn.
  let seq = 0;
  const refresh = async () => {
    const mine = ++seq;
    const list = threads;
    const found = await Promise.all(list.map((t) => findAccount(t.id)));
    if (mine !== seq || !ctx.alive()) return;
    accounts = {};
    gone = new Set();
    found.forEach((a, i) => { if (a) accounts[list[i].id] = a; else if (a === null) gone.add(list[i].id); });
    renderThreads();
  };

  stop.push(onSnapshot(query(collection(db(), 'support'), orderBy('lastAt', 'desc'), limit(100)), (snap) => {
    threads = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    refresh();
  }, (e) => { threadsEl.innerHTML = `<div class="empty">${esc(errorMessage(e))}</div>`; }));
  ctx.live([accountsStore], refresh);

  function open(uid, thread) {
    current = uid;
    chatEl.dataset.uid = uid;
    chatEl.className = '';
    threadsEl.querySelectorAll('[data-uid]').forEach((li) => li.classList.toggle('active', li.dataset.uid === uid));
    const name = thread ? nameOf(thread) : uid;
    chatEl.innerHTML = `
      <div class="card-head"><h2>${esc(name)}</h2><div class="spacer"></div>
        <button class="btn btn-ghost btn-sm" id="clear-chat">Clear chat</button>
        <button class="btn btn-danger btn-sm" id="delete-chat">Delete chat</button>
        ${gone.has(uid) ? '' : `<a class="btn btn-ghost btn-sm" href="#/salon/${encodeURIComponent(uid)}">Open salon</a>`}</div>
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
      if (!await confirmDialog('Clear chat?', 'All messages of this conversation are deleted for you and the salon. The conversation stays in the list. This cannot be undone.', 'Clear chat')) return;
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
    chatEl.querySelector('#delete-chat').addEventListener('click', async (ev) => {
      if (!await confirmDialog('Delete this conversation?', `The conversation with ${name} and all its messages are deleted for you and the salon, and it leaves this list. If the salon writes again, a new conversation starts. This cannot be undone.`, 'Delete', true)) return;
      ev.target.disabled = true;
      try {
        closeChat();
        await deleteSupportThread(uid);
        toast('Conversation deleted', 'success');
      } catch (err) {
        toast(errorMessage(err), 'error');
        open(uid, threads.find((t) => t.id === uid));
      }
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
