// DT Salon Management - optional AI assistant (Cloud Functions, Firebase Blaze plan).
// The Claude API key is a Functions secret (ANTHROPIC_API_KEY); it never reaches the app or panel.
//  - supportAutoReply: answers a salon's new support message in its own language (when enabled).
//  - businessAssistant: business growth advice from the salon's summary numbers (callable).
// Both are switched on/off and limited from the Super Admin panel (config/ai).
import Anthropic from '@anthropic-ai/sdk';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { setGlobalOptions } from 'firebase-functions/v2';
import { defineSecret } from 'firebase-functions/params';
import { logger } from 'firebase-functions';
import { supportReply, businessAdvice, DEFAULT_MODEL } from './assistant.js';

initializeApp();
const db = getFirestore();
const ANTHROPIC_API_KEY = defineSecret('ANTHROPIC_API_KEY');

// Keep functions next to the Firestore database (see docs/SETUP_GUIDE.md).
setGlobalOptions({ region: 'asia-south1', maxInstances: 5 });

const today = () => new Date().toISOString().slice(0, 10);

async function aiConfig() {
  const snap = await db.doc('config/ai').get();
  return { enabled: false, supportAutoReply: true, businessAssistant: true, dailyLimitPerSalon: 30, model: DEFAULT_MODEL, ...(snap.data() || {}) };
}

/** Counts a salon's AI requests per day; false when the admin's daily limit is reached. */
async function takeQuota(uid, limit) {
  const ref = db.doc(`aiUsage/${uid}_${today()}`);
  return db.runTransaction(async (tx) => {
    const used = (await tx.get(ref)).data()?.count || 0;
    if (used >= limit) return false;
    tx.set(ref, { uid, day: today(), count: used + 1, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return true;
  });
}

export const supportAutoReply = onDocumentCreated(
  { document: 'support/{uid}/messages/{messageId}', secrets: [ANTHROPIC_API_KEY], timeoutSeconds: 120 },
  async (event) => {
    const message = event.data?.data();
    const { uid } = event.params;
    if (!message || message.from !== 'user') return;
    const config = await aiConfig();
    if (!config.enabled || !config.supportAutoReply) return;
    if (!(await takeQuota(uid, Number(config.dailyLimitPerSalon) || 30))) return;

    const thread = (await db.doc(`support/${uid}`).get()).data() || {};
    const history = (await db.collection(`support/${uid}/messages`).orderBy('at', 'desc').limit(20).get())
      .docs.map((d) => d.data()).reverse();
    try {
      const client = new Anthropic({ apiKey: ANTHROPIC_API_KEY.value() });
      const reply = await supportReply(client, { history, lang: thread.lang, extraInstructions: config.extraInstructions, model: config.model });
      if (!reply?.text) return;
      await db.collection(`support/${uid}/messages`).add({ from: 'ai', text: reply.text.slice(0, 4000), at: FieldValue.serverTimestamp() });
      await db.doc(`support/${uid}`).set({
        lastMessage: reply.text.slice(0, 200), lastFrom: 'ai', lastAt: FieldValue.serverTimestamp(), unreadForUser: true,
      }, { merge: true });
      logger.info('support reply', { uid, model: reply.model, usage: reply.usage });
    } catch (e) {
      logger.error('support reply failed', { uid, error: e?.message, status: e?.status });
    }
  },
);

export const businessAssistant = onCall(
  { secrets: [ANTHROPIC_API_KEY], timeoutSeconds: 180 },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError('unauthenticated', 'Sign in first.');
    const account = (await db.doc(`accounts/${uid}`).get()).data();
    if (!account || account.status !== 'APPROVED') throw new HttpsError('permission-denied', 'Account is not active.');
    const config = await aiConfig();
    if (!config.enabled || !config.businessAssistant) throw new HttpsError('failed-precondition', 'AI is switched off by DT.');
    const question = String(request.data?.question || '').trim();
    if (!question) throw new HttpsError('invalid-argument', 'Ask a question.');
    if (!(await takeQuota(uid, Number(config.dailyLimitPerSalon) || 30))) throw new HttpsError('resource-exhausted', 'Daily AI limit reached.');
    try {
      const client = new Anthropic({ apiKey: ANTHROPIC_API_KEY.value() });
      const result = await businessAdvice(client, { question, stats: request.data?.stats, lang: request.data?.lang, model: config.model });
      if (!result.text) throw new HttpsError('aborted', 'No answer. Please rephrase the question.');
      logger.info('business advice', { uid, model: result.model, usage: result.usage });
      return { answer: result.text };
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      if (e instanceof Anthropic.RateLimitError) throw new HttpsError('resource-exhausted', 'AI is busy. Try again in a minute.');
      if (e instanceof Anthropic.APIError) {
        logger.error('claude error', { uid, status: e.status, error: e.message });
        throw new HttpsError('unavailable', 'AI is not available right now.');
      }
      throw e;
    }
  },
);
