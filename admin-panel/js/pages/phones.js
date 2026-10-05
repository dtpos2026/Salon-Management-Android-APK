// Phones: every phone using the app, its status, versions, last seen and (when the salon shares
// it) the approximate location on an OpenStreetMap map. Block / unblock a phone from here.
import { listDevices, listAccountsByIds, setDeviceBlocked } from '../data.js';
import { esc, fmtDateTime, toast, errorMessage, confirmDialog, toDate } from '../util.js';

const HOUR = 3600 * 1000;

function seen(device) {
  const at = toDate(device.lastSeenAt);
  if (!at) return { label: 'Never', cls: 'st-EXPIRED' };
  const age = Date.now() - at.getTime();
  if (age < HOUR) return { label: 'Active now', cls: 'st-APPROVED' };
  if (age < 24 * HOUR) return { label: 'Today', cls: 'st-PAYMENT_PENDING' };
  return { label: `${Math.floor(age / (24 * HOUR))} days ago`, cls: 'st-EXPIRED' };
}

export async function render(el, ctx) {
  ctx.setTitle('Phones');
  const devices = (await listDevices()).sort((a, b) => (toDate(b.lastSeenAt)?.getTime() || 0) - (toDate(a.lastSeenAt)?.getTime() || 0));
  const accounts = await listAccountsByIds([...new Set(devices.map((d) => d.uid))]);
  const located = devices.filter((d) => typeof d.lat === 'number' && typeof d.lng === 'number');
  el.innerHTML = `
    <div class="card">
      <div class="card-head"><h2>Map</h2><div class="spacer"></div><span class="cell-sub">${located.length} of ${devices.length} phones share their location (salon's choice)</span></div>
      <div id="map" class="phone-map">${located.length ? '' : '<div class="empty">No phone shares its location. Salons can turn it on in the app: Settings › Account › Share this phone’s location.</div>'}</div>
      <div class="help">Map © OpenStreetMap contributors. Locations are approximate (network based) and only from phones whose owner allowed it. IP addresses are not collected.</div>
    </div>
    <div class="card" style="margin-top:16px;padding:8px 12px">
      ${devices.length ? `<table class="list"><thead><tr><th>Salon</th><th>Phone</th><th>App</th><th>Last seen</th><th>Location</th><th>Status</th><th></th></tr></thead><tbody>${devices.map((d) => {
        const a = accounts[d.uid];
        const blocked = (a?.blockedDeviceIds || []).includes(d.deviceId);
        const s = seen(d);
        return `<tr>
          <td data-label="Salon"><a class="cell-title" href="#/salon/${encodeURIComponent(d.uid)}">${esc(a?.salonName || d.uid)}</a><div class="cell-sub">${esc(a?.city || '')}</div></td>
          <td data-label="Phone"><div>${esc(d.model || '—')}</div><div class="cell-sub">${esc(d.osVersion || '')} · <span class="mono">${esc(String(d.deviceId || '').slice(-8))}</span></div></td>
          <td data-label="App">${esc(d.appVersion || '—')}</td>
          <td data-label="Last seen"><span class="pill ${s.cls}">${esc(s.label)}</span><div class="cell-sub">${fmtDateTime(d.lastSeenAt)}</div></td>
          <td data-label="Location">${typeof d.lat === 'number' ? `<a target="_blank" rel="noopener" href="https://www.openstreetmap.org/?mlat=${d.lat}&mlon=${d.lng}#map=16/${d.lat}/${d.lng}">${d.lat.toFixed(4)}, ${d.lng.toFixed(4)}</a><div class="cell-sub">±${Math.round(d.accuracyM || 0)} m · ${fmtDateTime(d.locationAt)}</div>` : `<span class="cell-sub">${d.locationPermission === 'denied' ? 'Permission denied on the phone' : 'Not shared'}</span>`}</td>
          <td data-label="Status">${blocked ? '<span class="pill st-BLOCKED">Blocked</span>' : '<span class="pill st-APPROVED">Allowed</span>'}</td>
          <td style="text-align:right">${a ? `<button class="btn ${blocked ? 'btn-success' : 'btn-danger'} btn-sm" data-block="${esc(d.id)}">${blocked ? 'Unblock' : 'Block'}</button>` : ''}</td>
        </tr>`;
      }).join('')}</tbody></table>` : '<div class="empty">No phone has reported yet. Phones on app 2.1.0+ report when they are online.</div>'}
    </div>`;

  if (located.length && window.L) {
    const map = window.L.map(el.querySelector('#map'), { scrollWheelZoom: false });
    window.L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(map);
    const markers = located.map((d) => {
      const a = accounts[d.uid];
      return window.L.marker([d.lat, d.lng]).addTo(map).bindPopup(
        `<b>${esc(a?.salonName || d.uid)}</b><br>${esc(d.model || '')}<br>Last seen ${esc(fmtDateTime(d.lastSeenAt))}<br><a href="#/salon/${encodeURIComponent(d.uid)}">Open salon</a>`,
      );
    });
    map.fitBounds(window.L.featureGroup(markers).getBounds().pad(0.3), { maxZoom: 14 });
  }

  el.querySelectorAll('[data-block]').forEach((btn) => btn.addEventListener('click', async () => {
    const d = devices.find((x) => x.id === btn.dataset.block);
    const a = accounts[d.uid];
    const blocked = (a.blockedDeviceIds || []).includes(d.deviceId);
    const ok = await confirmDialog(
      blocked ? 'Unblock this phone?' : 'Block this phone?',
      blocked ? `${d.model || 'This phone'} can open the app again at its next check.` : `${d.model || 'This phone'} stops opening the app at its next online check. The salon's data on it is not deleted.`,
      blocked ? 'Unblock' : 'Block',
      !blocked,
    );
    if (!ok) return;
    try {
      await setDeviceBlocked({ ...a, id: d.uid }, d.deviceId, !blocked);
      toast(blocked ? 'Phone unblocked' : 'Phone blocked', 'success');
      render(el, ctx);
    } catch (e) { toast(errorMessage(e), 'error'); }
  }));
}
