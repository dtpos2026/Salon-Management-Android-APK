// Phones: the phones of registered salons, their status, versions, last seen and live location
// on an OpenStreetMap map. Block / unblock or delete a phone's report from here. The list is
// live: phones that report while the page is open move on the map without reloading.
import { listDevices, listAccountsByIds, setDeviceBlocked, deleteDeviceReport } from '../data.js';
import { esc, fmtDateTime, toast, errorMessage, confirmDialog, toDate, loadScript, loadStyle } from '../util.js';
import { accountsStore, devicesStore } from '../store.js';

const HOUR = 3600 * 1000;
const MINUTE = 60 * 1000;

function ago(value) {
  const at = toDate(value);
  if (!at) return '';
  const min = Math.max(0, Math.round((Date.now() - at.getTime()) / MINUTE));
  if (min < 1) return 'just now';
  if (min < 60) return `${min} min ago`;
  if (min < 24 * 60) return `${Math.round(min / 60)} h ago`;
  return `${Math.floor(min / (24 * 60))} days ago`;
}

function seen(device) {
  const at = toDate(device.lastSeenAt);
  if (!at) return { label: 'Never', cls: 'st-EXPIRED' };
  const age = Date.now() - at.getTime();
  if (age < 15 * MINUTE) return { label: 'Active now', cls: 'st-APPROVED' };
  if (age < HOUR) return { label: ago(at), cls: 'st-APPROVED' };
  if (age < 24 * HOUR) return { label: 'Today', cls: 'st-PAYMENT_PENDING' };
  return { label: `${Math.floor(age / (24 * HOUR))} days ago`, cls: 'st-EXPIRED' };
}

const hasLocation = (d) => typeof d.lat === 'number' && typeof d.lng === 'number';

/** Leaflet (map library) is loaded the first time the map is needed. */
function loadLeaflet() {
  return Promise.all([loadStyle('vendor/leaflet/leaflet.css'), loadScript('vendor/leaflet/leaflet.js')]).then(() => window.L);
}

function locationCell(d) {
  if (hasLocation(d)) {
    return `<a target="_blank" rel="noopener" href="https://www.openstreetmap.org/?mlat=${d.lat}&mlon=${d.lng}#map=16/${d.lat}/${d.lng}">${d.lat.toFixed(4)}, ${d.lng.toFixed(4)}</a><div class="cell-sub">±${Math.round(d.accuracyM || 0)} m · ${esc(ago(d.locationAt) || fmtDateTime(d.locationAt))}</div>`;
  }
  const why = d.locationPermission === 'denied' ? 'Permission not given yet'
    : d.locationPermission === 'off' ? 'Location switched off' : 'Waiting for the first fix';
  return `<span class="cell-sub">${why}</span>`;
}

function tableHtml(devices, accounts) {
  if (!devices.length) return '<div class="empty">No phone has reported yet. Phones on app 2.1.0+ report when they are online.</div>';
  return `<table class="list"><thead><tr><th>Salon</th><th>Phone</th><th>App</th><th>Last seen</th><th>Location</th><th>Status</th><th></th></tr></thead><tbody>${devices.map((d) => {
    const a = accounts[d.uid];
    const blocked = (a?.blockedDeviceIds || []).includes(d.deviceId);
    const s = seen(d);
    return `<tr>
      <td data-label="Salon"><a class="cell-title" href="#/salon/${encodeURIComponent(d.uid)}">${esc(a?.salonName || d.uid)}</a><div class="cell-sub">${esc(a?.city || '')}</div></td>
      <td data-label="Phone"><div>${esc(d.model || '—')}</div><div class="cell-sub">${esc(d.osVersion || '')} · <span class="mono">${esc(String(d.deviceId || '').slice(-8))}</span></div></td>
      <td data-label="App">${esc(d.appVersion || '—')}</td>
      <td data-label="Last seen"><span class="pill ${s.cls}">${esc(s.label)}</span><div class="cell-sub">${fmtDateTime(d.lastSeenAt)}</div></td>
      <td data-label="Location">${locationCell(d)}</td>
      <td data-label="Status">${blocked ? '<span class="pill st-BLOCKED">Blocked</span>' : '<span class="pill st-APPROVED">Allowed</span>'}</td>
      <td style="text-align:right;white-space:nowrap">${a ? `<button class="btn ${blocked ? 'btn-success' : 'btn-danger'} btn-sm" data-block="${esc(d.id)}">${blocked ? 'Unblock' : 'Block'}</button>` : ''}
        <button class="btn btn-ghost btn-sm" data-delete="${esc(d.id)}" title="Delete this phone's location and report">Delete</button></td>
    </tr>`;
  }).join('')}</tbody></table>`;
}

export async function render(el, ctx) {
  ctx.setTitle('Phones');
  el.innerHTML = `
    <div class="card">
      <div class="card-head"><h2>Map</h2><div class="spacer"></div><span class="cell-sub" id="map-count"></span></div>
      <div id="map" class="phone-map"></div>
      <div class="help">Live: a phone reports every 2 minutes while the app is open (and when it is opened), from GPS when available; the circle shows the accuracy. Map © OpenStreetMap contributors. Locations come from the phone (network or GPS, accuracy shown). IP addresses are not collected.</div>
    </div>
    <div id="orphans"></div>
    <div class="card" style="margin-top:16px;padding:8px 12px" id="phone-list"></div>`;
  const mapEl = el.querySelector('#map');
  const listEl = el.querySelector('#phone-list');
  let devices = [];
  let accounts = {};
  let map = null;
  let layer = null;
  let fitted = false;
  ctx.onLeave(() => { if (map) map.remove(); });

  const drawMap = async () => {
    const located = devices.filter(hasLocation);
    el.querySelector('#map-count').textContent = `${located.length} of ${devices.length} phones have reported a location`;
    if (!located.length) {
      if (map) { map.remove(); map = null; layer = null; fitted = false; }
      mapEl.innerHTML = '<div class="empty">No location reported yet. The app asks for location before it opens (version 2.1.1+) and reports it when online.</div>';
      return;
    }
    let L;
    try {
      L = await loadLeaflet();
    } catch (e) {
      mapEl.innerHTML = `<div class="empty">${esc(errorMessage(e))}</div>`;
      return;
    }
    if (!ctx.alive()) return;
    if (!map) {
      mapEl.innerHTML = '';
      map = L.map(mapEl, { scrollWheelZoom: false });
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      }).addTo(map);
      layer = L.featureGroup().addTo(map);
    }
    // Zoom to the phones once (before drawing: circles need a map view); later updates keep the admin's own view.
    if (!fitted) {
      map.fitBounds(L.latLngBounds(located.map((d) => [d.lat, d.lng])).pad(0.3), { maxZoom: 14 });
      fitted = true;
    }
    layer.clearLayers();
    located.forEach((d) => {
      const a = accounts[d.uid];
      // The circle shows how exact the reported position is (GPS: a few metres).
      if (d.accuracyM > 0) {
        L.circle([d.lat, d.lng], { radius: Math.min(d.accuracyM, 3000), color: '#6a2bd9', weight: 1, fillOpacity: 0.12, interactive: false }).addTo(layer);
      }
      L.marker([d.lat, d.lng]).addTo(layer).bindPopup(
        `<b>${esc(a?.salonName || d.uid)}</b><br>${esc(d.model || '')}<br>Location ${esc(ago(d.locationAt) || '—')} · ±${Math.round(d.accuracyM || 0)} m<br><a href="#/salon/${encodeURIComponent(d.uid)}">Open salon</a>`,
      );
    });
  };

  let orphans = [];
  const load = async () => {
    const all = (await listDevices()).sort((a, b) => (toDate(b.lastSeenAt)?.getTime() || 0) - (toDate(a.lastSeenAt)?.getTime() || 0));
    accounts = await listAccountsByIds([...new Set(all.map((d) => d.uid))]);
    if (!ctx.alive()) return;
    // Only phones of registered salons; reports left by deleted salons are hidden (and can be cleared).
    devices = all.filter((d) => accounts[d.uid]);
    orphans = all.filter((d) => !accounts[d.uid]);
    el.querySelector('#orphans').innerHTML = orphans.length
      ? `<div class="notice info" style="margin-top:16px">${orphans.length} phone report(s) of deleted salons are hidden. <button class="btn btn-ghost btn-sm" id="clear-orphans">Delete them</button></div>`
      : '';
    el.querySelector('#clear-orphans')?.addEventListener('click', async (ev) => {
      if (!(await confirmDialog('Delete old phone reports?', `${orphans.length} phone(s) of salons that no longer exist are removed from the list and the map.`, 'Delete', true))) return;
      ev.target.disabled = true;
      try {
        await Promise.all(orphans.map((d) => deleteDeviceReport(d.id)));
        toast('Old phone reports deleted', 'success');
        await load();
      } catch (e) { ev.target.disabled = false; toast(errorMessage(e), 'error'); }
    });
    listEl.innerHTML = tableHtml(devices, accounts);
    drawMap();
  };

  listEl.addEventListener('click', async (ev) => {
    const del = ev.target.closest('[data-delete]');
    if (del) {
      const d = devices.find((x) => x.id === del.dataset.delete);
      if (!d) return;
      const ok = await confirmDialog(
        'Delete this phone from the list and map?',
        `${d.model || 'This phone'}: its location and report are deleted. If it is still approved and in use, it appears again at its next report. To stop it opening the app, Block it or remove it on the salon page.`,
        'Delete',
        true,
      );
      if (!ok) return;
      del.disabled = true;
      try {
        await deleteDeviceReport(d.id);
        toast('Phone report deleted', 'success');
        await load();
      } catch (e) { del.disabled = false; toast(errorMessage(e), 'error'); }
      return;
    }
    const btn = ev.target.closest('[data-block]');
    if (!btn) return;
    const d = devices.find((x) => x.id === btn.dataset.block);
    const a = d && accounts[d.uid];
    if (!a) return;
    const blocked = (a.blockedDeviceIds || []).includes(d.deviceId);
    const ok = await confirmDialog(
      blocked ? 'Unblock this phone?' : 'Block this phone?',
      blocked ? `${d.model || 'This phone'} can open the app again at its next check.` : `${d.model || 'This phone'} stops opening the app at its next online check. The salon's data on it is not deleted.`,
      blocked ? 'Unblock' : 'Block',
      !blocked,
    );
    if (!ok) return;
    btn.disabled = true;
    try {
      await setDeviceBlocked({ ...a, id: d.uid }, d.deviceId, !blocked);
      toast(blocked ? 'Phone unblocked' : 'Phone blocked', 'success');
      await load();
    } catch (e) {
      btn.disabled = false;
      toast(errorMessage(e), 'error');
    }
  });

  await load();
  ctx.live([devicesStore, accountsStore], () => load().catch(() => null));
  // "x min ago" stays current while the page is open.
  const tick = setInterval(() => { if (ctx.alive()) listEl.innerHTML = tableHtml(devices, accounts); }, 60000);
  ctx.onLeave(() => clearInterval(tick));
}
