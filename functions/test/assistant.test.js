import { test } from 'node:test';
import assert from 'node:assert/strict';
import { chatHistory, cleanStats, supportReply, businessAdvice, supportSystem, DEFAULT_MODEL } from '../assistant.js';

function fakeClient(reply = 'Printer ko restart karein.', stop = 'end_turn') {
  const calls = [];
  return {
    calls,
    beta: { messages: { create: async (params) => { calls.push(params); return { stop_reason: stop, model: params.model, usage: {}, content: [{ type: 'text', text: reply }] }; } } },
  };
}

test('chat history maps salon/admin/AI messages to alternating turns ending with the salon', () => {
  const history = chatHistory([
    { from: 'ai', text: 'Welcome' },
    { from: 'user', text: 'Printer nahi chal raha' },
    { from: 'user', text: '58mm hai' },
    { from: 'admin', text: 'Restart it' },
    { from: 'user', text: 'Ab bhi nahi' },
  ]);
  assert.deepEqual(history.map((m) => m.role), ['user', 'assistant', 'user']);
  assert.match(history[0].content, /Printer nahi chal raha\n\n58mm hai/);
  assert.match(history[1].content, /^\[DT team\] Restart it/);
});

test('support reply uses the cached guide, the language rule and refusal fallbacks', async () => {
  const client = fakeClient();
  const result = await supportReply(client, { history: [{ from: 'user', text: 'printer kaise lagaun?' }], lang: 'ur-Latn' });
  assert.equal(result.text, 'Printer ko restart karein.');
  const call = client.calls[0];
  assert.equal(call.model, DEFAULT_MODEL);
  assert.equal(call.fallbacks, 'default');
  assert.deepEqual(call.betas, ['server-side-fallback-2026-07-01']);
  assert.equal(call.system[0].cache_control.type, 'ephemeral');
  assert.match(call.system[0].text, /Roman Urdu/);
  assert.match(call.system[0].text, /Settings > Printer/);
  assert.match(call.messages.at(-1).content, /App language: ur-Latn/);
});

test('no reply when the last message is not from the salon, or on refusal', async () => {
  assert.equal(await supportReply(fakeClient(), { history: [{ from: 'admin', text: 'Hi' }] }), null);
  const refused = await supportReply(fakeClient('x', 'refusal'), { history: [{ from: 'user', text: 'hi' }] });
  assert.equal(refused.text, null);
});

test('business advice sends only cleaned summary numbers', async () => {
  const client = fakeClient('Increase weekday offers.');
  const stats = { monthSales: 250000, topServices: [{ name: 'Hair cut', amount: 90000 }], nested: { a: { b: { c: { d: 1 } } } }, fn: () => 1 };
  await businessAdvice(client, { question: 'How to grow?', stats, lang: 'en' });
  const content = client.calls[0].messages[0].content;
  assert.match(content, /"monthSales": 250000/);
  assert.match(content, /How to grow\?/);
  assert.doesNotMatch(content, /fn/);
  assert.deepEqual(cleanStats({ s: 'x'.repeat(200) }).s.length, 80);
});

test('extra instructions from the panel are appended after the cached block', () => {
  const system = supportSystem('Eid offer: 10% off until Sunday');
  assert.equal(system.length, 2);
  assert.equal(system[1].cache_control, undefined);
});
