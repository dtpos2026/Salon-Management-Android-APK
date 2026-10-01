// CI helper: writes firebase-config.js from the FIREBASE_WEB_CONFIG secret (JSON, or the
// `const firebaseConfig = {...}` snippet copied from the Firebase console).
import { writeFileSync } from 'node:fs';

const raw = process.env.FIREBASE_WEB_CONFIG || '';
if (!raw.trim()) {
  console.log('FIREBASE_WEB_CONFIG is empty: the panel will ask for the config on first open.');
  process.exit(0);
}
const body = raw.slice(raw.indexOf('{'), raw.lastIndexOf('}') + 1)
  .replace(/\/\/.*$/gm, '')
  .replace(/([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)\s*:/g, '$1"$2":')
  .replace(/'/g, '"')
  .replace(/,\s*}/g, '}');
const config = JSON.parse(body);
for (const key of ['apiKey', 'authDomain', 'projectId', 'appId']) {
  if (!config[key]) throw new Error(`FIREBASE_WEB_CONFIG is missing ${key}`);
}
writeFileSync('firebase-config.js', `window.DT_FIREBASE_CONFIG = ${JSON.stringify(config, null, 2)};\n`);
console.log(`firebase-config.js written for project ${config.projectId}`);
