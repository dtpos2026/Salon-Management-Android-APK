// Prompt building and the Claude call for the DT Salon assistant. Kept free of Firebase so it can be
// unit tested with a fake client (test/assistant.test.js).
import { readFileSync } from 'node:fs';

export const DEFAULT_MODEL = 'claude-opus-5-5';
const GUIDE = readFileSync(new URL('./assistant-guide.md', import.meta.url), 'utf8');

const LANGUAGE_RULE = `Language: reply in the same language and script as the user's latest message.
- Urdu written in Urdu script -> reply in Urdu script.
- Urdu written in English letters (Roman Urdu, e.g. "printer kaise lagaun") -> reply in Roman Urdu.
- English -> reply in English.
If the message mixes languages, use the one most of it is written in. The app language hint is only a tie-breaker.`;

/** System prompt for support chat. The guide is frozen text, so it is cached across requests. */
export function supportSystem(extraInstructions = '') {
  const blocks = [{
    type: 'text',
    text: `You are "DT Assistant", the support helper inside DT Salon Management, an offline salon and barber POS app by Digital Target (Pakistan).
Answer salon owners' questions about using the app, step by step, briefly and kindly. Use the app guide below; never invent features or menu names that are not in it.
If something needs the company (payment, approval, account changes, a bug, refunds) say a DT team member will reply in this chat soon.
Never ask for passwords. Keep answers short (under 150 words) unless steps are needed. Plain text only, no markdown tables.

${LANGUAGE_RULE}

<app_guide>
${GUIDE}
</app_guide>`,
    cache_control: { type: 'ephemeral' },
  }];
  if (extraInstructions?.trim()) blocks.push({ type: 'text', text: `Notes from the DT team:\n${extraInstructions.trim().slice(0, 2000)}` });
  return blocks;
}

/** System prompt for the business growth assistant. */
export function businessSystem() {
  return [{
    type: 'text',
    text: `You are "DT Assistant", a practical business coach for a small salon or barber shop in Pakistan, inside the DT Salon Management app.
You receive a summary of the salon's own numbers (amounts in Pakistani rupees) and a question. Give concrete, realistic advice the owner can act on this week: pricing, packages, promotions (WhatsApp offers, Eid/wedding season, loyalty), staff targets, slow days, expenses, customer retention and pending bills (udhaar).
Base every claim on the numbers given; if a number is missing, say what to track instead of guessing. Use short headings and bullet points. Mention which app feature helps (Promotions, Udhaar, Targets, Budget, Reports, Tokens & bookings) when relevant.

${LANGUAGE_RULE}

<app_guide>
${GUIDE}
</app_guide>`,
    cache_control: { type: 'ephemeral' },
  }];
}

/** Converts stored chat messages to API messages: salon -> user, admin/AI -> assistant, merged per turn. */
export function chatHistory(messages, limit = 20) {
  const out = [];
  for (const m of messages.slice(-limit)) {
    const role = m.from === 'user' ? 'user' : 'assistant';
    const text = String(m.text || '').trim();
    if (!text) continue;
    const prefix = m.from === 'admin' ? '[DT team] ' : '';
    if (out.length && out[out.length - 1].role === role) out[out.length - 1].content += `\n\n${prefix}${text}`;
    else out.push({ role, content: prefix + text });
  }
  while (out.length && out[0].role !== 'user') out.shift();
  return out;
}

/** Keeps only plain numbers and short strings from the app's business summary. */
export function cleanStats(stats) {
  const clean = (value, depth) => {
    if (depth > 3) return undefined;
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value === 'string') return value.slice(0, 80);
    if (typeof value === 'boolean') return value;
    if (Array.isArray(value)) return value.slice(0, 12).map((v) => clean(v, depth + 1)).filter((v) => v !== undefined);
    if (value && typeof value === 'object') {
      return Object.fromEntries(Object.entries(value).slice(0, 40).map(([k, v]) => [k.slice(0, 40), clean(v, depth + 1)]).filter(([, v]) => v !== undefined));
    }
    return undefined;
  };
  return clean(stats ?? {}, 0) ?? {};
}

function textOf(response) {
  if (response.stop_reason === 'refusal') return null;
  return response.content.filter((b) => b.type === 'text').map((b) => b.text).join('\n').trim() || null;
}

/**
 * One Claude request. Uses server-side refusal fallbacks ("default" routing) so a policy decline
 * is retried on a suitable model in the same call.
 */
export async function ask(client, { system, messages, model = DEFAULT_MODEL, effort = 'low', maxTokens = 4000 }) {
  const response = await client.beta.messages.create({
    model,
    max_tokens: maxTokens,
    betas: ['server-side-fallback-2026-07-01'],
    fallbacks: 'default',
    output_config: { effort },
    system,
    messages,
  });
  return { text: textOf(response), usage: response.usage, model: response.model };
}

export async function supportReply(client, { history, lang, extraInstructions, model }) {
  const messages = chatHistory(history);
  if (!messages.length) return null;
  const last = messages[messages.length - 1];
  if (last.role !== 'user') return null;
  if (lang) last.content += `\n\n(App language: ${lang})`;
  return ask(client, { system: supportSystem(extraInstructions), messages, model, effort: 'low', maxTokens: 2000 });
}

export async function businessAdvice(client, { question, stats, lang, model }) {
  const content = `Salon summary (from the app, amounts in Rs):\n${JSON.stringify(cleanStats(stats), null, 1)}\n\nQuestion: ${String(question || '').slice(0, 1500)}\n\n(App language: ${lang || 'en'})`;
  return ask(client, { system: businessSystem(), messages: [{ role: 'user', content }], model, effort: 'medium', maxTokens: 6000 });
}
