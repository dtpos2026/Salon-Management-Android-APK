// Rebuilds vendor/*.js from npm packages: `npm install && npm run vendor`.
import { build } from 'esbuild';
import { copyFileSync } from 'node:fs';

await build({
  entryPoints: ['tools/firebase-entry.js'],
  bundle: true,
  format: 'esm',
  minify: true,
  target: 'es2020',
  outfile: 'vendor/firebase.js',
  legalComments: 'eof',
});
copyFileSync('node_modules/html2canvas/dist/html2canvas.min.js', 'vendor/html2canvas.min.js');
copyFileSync('node_modules/qrcode-generator/qrcode.js', 'vendor/qrcode.js');
console.log('vendor bundles written');
