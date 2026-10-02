// "npm run build": the Super Admin panel is a static site, so nothing is compiled. This checks
// that everything the deploy needs is present and tells you what to run next.
import { existsSync, readFileSync } from 'node:fs';

const required = [
  'firebase.json',
  '.firebaserc',
  'firebase/firestore.rules',
  'firebase/firestore.indexes.json',
  'admin-panel/index.html',
  'admin-panel/verify.html',
  'admin-panel/js/app.js',
  'admin-panel/vendor/firebase.js',
  'admin-panel/css/panel.css',
];
const missing = required.filter((f) => !existsSync(f));
if (missing.length) {
  console.error(`Missing files:\n  ${missing.join('\n  ')}\nUnzip the full project again and run this inside its main folder.`);
  process.exit(1);
}
const project = JSON.parse(readFileSync('.firebaserc', 'utf8')).projects?.default;
console.log('Build OK: the Super Admin panel is ready to deploy (no compiling needed).');
console.log(`Firebase project: ${project}`);
console.log('Next:\n  npx firebase-tools login\n  npx firebase-tools deploy');
