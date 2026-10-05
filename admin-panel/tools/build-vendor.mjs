// Rebuilds vendor/*.js from npm packages: `npm install && npm run vendor`.
import { build } from 'esbuild';
import { copyFileSync, mkdirSync, readdirSync } from 'node:fs';

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
// Leaflet (BSD-2) for the phones map; tiles come from OpenStreetMap in the admin's browser.
mkdirSync('vendor/leaflet/images', { recursive: true });
for (const f of ['leaflet.js', 'leaflet.css']) copyFileSync(`node_modules/leaflet/dist/${f}`, `vendor/leaflet/${f}`);
for (const f of readdirSync('node_modules/leaflet/dist/images')) copyFileSync(`node_modules/leaflet/dist/images/${f}`, `vendor/leaflet/images/${f}`);
copyFileSync('node_modules/leaflet/LICENSE', 'vendor/leaflet/LICENSE');
console.log('vendor bundles written');
