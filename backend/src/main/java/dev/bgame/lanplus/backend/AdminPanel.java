package dev.bgame.lanplus.backend;

final class AdminPanel {

    private AdminPanel() {}

    static final String HTML = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>LAN+ Moderation</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin:0; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
    background:#14131a; color:#e8e8ef; }
  header { padding:16px 20px; border-bottom:1px solid #2a2833; display:flex; align-items:center; gap:10px; }
  header .logo { font-weight:700; letter-spacing:.3px; }
  header .logo span { color:#a78bfa; }
  main { max-width:860px; margin:0 auto; padding:20px; }
  .card { background:#1e1c26; border:1px solid #2a2833; border-radius:12px; padding:16px; margin-bottom:14px; }
  .bar { display:flex; align-items:center; gap:10px; margin:10px 0 16px; }
  .bar h2 { font-size:16px; margin:0; flex:1; }
  input[type=text], input[type=password] { width:100%; padding:10px 12px; border-radius:8px;
    border:1px solid #34313f; background:#14131a; color:#e8e8ef; font-size:14px; }
  button { cursor:pointer; border:none; border-radius:8px; padding:8px 12px; font-size:13px; font-weight:600;
    color:#e8e8ef; background:#34313f; }
  button:hover { filter:brightness(1.15); }
  button.accent { background:#7c3aed; }
  button.danger { background:#dc2626; }
  button.ghost { background:transparent; border:1px solid #34313f; }
  .report { border:1px solid #2a2833; border-radius:10px; padding:14px; margin-bottom:12px; background:#191823; }
  .report .row { display:flex; gap:8px; align-items:baseline; margin:2px 0; }
  .report .k { color:#9a97a8; font-size:12px; width:96px; flex:none; }
  .report .v { font-size:14px; word-break:break-word; }
  .report .bio { background:#14131a; border:1px solid #2a2833; border-radius:8px; padding:8px 10px; margin:6px 0;
    white-space:pre-wrap; word-break:break-word; }
  .report .uuid { font-family: ui-monospace, monospace; font-size:12px; color:#c7c4d4; }
  .actions { display:flex; gap:8px; margin-top:10px; flex-wrap:wrap; }
  .muted { color:#9a97a8; font-size:13px; }
  .hidden { display:none; }
  .reason { color:#f0abfc; font-weight:600; }
  #msg { min-height:18px; font-size:13px; margin-bottom:8px; }
  .grid2 { display:flex; gap:8px; flex-wrap:wrap; }
  .grid2 input { flex:1; min-width:180px; }
  label { font-size:12px; color:#9a97a8; display:block; margin-bottom:6px; }
</style>
</head>
<body>
<header><div class="logo">LAN<span>+</span> Moderation</div></header>
<main>
  <div id="msg" class="muted"></div>

  <div id="login" class="card hidden">
    <label for="key">Admin key</label>
    <div class="grid2">
      <input id="key" type="password" placeholder="X-Admin-Key" autocomplete="off">
      <button class="accent" onclick="saveKey()">Enter</button>
    </div>
  </div>

  <div id="panel" class="hidden">
    <div class="bar">
      <h2>Open reports (<span id="count">0</span>)</h2>
      <button class="ghost" onclick="loadReports()">Refresh</button>
      <button class="ghost" onclick="logout()">Change key</button>
    </div>
    <div id="reports"></div>

    <div class="card">
      <label>Manual action by UUID</label>
      <div class="grid2">
        <input id="manualUuid" type="text" placeholder="target uuid">
        <button onclick="manual('scrub')">Scrub</button>
        <button class="danger" onclick="manual('ban')">Ban</button>
        <button onclick="manual('unban')">Unban</button>
      </div>
    </div>
  </div>
</main>
<script>
const KS = 'lanplus_admin_key';
const key = () => localStorage.getItem(KS) || '';
const msg = (t, err) => { const m=document.getElementById('msg'); m.textContent=t||''; m.style.color=err?'#f87171':'#9a97a8'; };

function show(logged) {
  document.getElementById('login').classList.toggle('hidden', logged);
  document.getElementById('panel').classList.toggle('hidden', !logged);
}
function saveKey() {
  const v = document.getElementById('key').value.trim();
  if (!v) return;
  localStorage.setItem(KS, v);
  show(true); loadReports();
}
function logout() { localStorage.removeItem(KS); show(false); msg(''); }

async function api(path, body) {
  const opts = { headers: { 'X-Admin-Key': key() } };
  if (body) { opts.method='POST'; opts.headers['Content-Type']='application/json'; opts.body=JSON.stringify(body); }
  return fetch(path, opts);
}

async function loadReports() {
  msg('Loading...');
  let r;
  try { r = await api('/admin/reports'); } catch (e) { msg('Network error', true); return; }
  if (r.status === 401) { msg('Invalid or missing admin key', true); show(false); return; }
  if (!r.ok) { msg('Error ' + r.status, true); return; }
  const data = await r.json();
  render(data);
  msg('');
}

function el(tag, cls, txt) { const e=document.createElement(tag); if(cls)e.className=cls; if(txt!=null)e.textContent=txt; return e; }
function field(k, v, vcls) { const row=el('div','row'); row.append(el('div','k',k)); row.append(el('div','v '+(vcls||''), v==null?'':String(v))); return row; }

function render(reports) {
  document.getElementById('count').textContent = reports.length;
  const box = document.getElementById('reports');
  box.textContent = '';
  if (!reports.length) { box.append(el('div','muted','No open reports.')); return; }
  for (const rep of reports) {
    const card = el('div','report');
    const uname = rep.targetUsername || '(unknown)';
    card.append(field('User', uname));
    card.append(field('UUID', rep.targetUuid, 'uuid'));
    const rr = el('div','row'); rr.append(el('div','k','Reason')); rr.append(el('div','v reason', rep.reason)); card.append(rr);
    if (rep.targetBio) { const b=el('div','bio', rep.targetBio); card.append(b); }
    card.append(field('By', rep.reporterUuid, 'uuid'));
    if (rep.createdAt) card.append(field('When', new Date(rep.createdAt).toLocaleString()));

    const act = el('div','actions');
    const scrub = el('button', '', 'Scrub bio');
    scrub.onclick = () => act1('/admin/scrub', { targetUuid: rep.targetUuid }, 'Scrubbed', rep.id);
    const ban = el('button','danger','Ban');
    ban.onclick = () => { if (confirm('Ban ' + uname + '?')) act1('/admin/ban', { targetUuid: rep.targetUuid, reason: rep.reason }, 'Banned', rep.id); };
    const resolve = el('button','accent','Resolve');
    resolve.onclick = () => act1('/admin/reports/resolve', { id: rep.id }, 'Resolved');
    act.append(scrub, ban, resolve);
    card.append(act);
    box.append(card);
  }
}

// Perform an action; if resolveId given, also resolve that report, then refresh.
async function act1(path, body, okText, resolveId) {
  try {
    const r = await api(path, body);
    if (r.status === 401) { msg('Invalid admin key', true); show(false); return; }
    if (!r.ok) { msg('Error ' + r.status, true); return; }
    if (resolveId != null && path !== '/admin/reports/resolve') {
      await api('/admin/reports/resolve', { id: resolveId });
    }
    msg(okText);
    loadReports();
  } catch (e) { msg('Network error', true); }
}

function manual(kind) {
  const uuid = document.getElementById('manualUuid').value.trim();
  if (!uuid) return;
  if (kind === 'ban') { if (!confirm('Ban ' + uuid + '?')) return; return act1('/admin/ban', { targetUuid: uuid, reason: 'manual' }, 'Banned'); }
  if (kind === 'unban') return act1('/admin/unban', { targetUuid: uuid }, 'Unbanned');
  return act1('/admin/scrub', { targetUuid: uuid }, 'Scrubbed');
}

// Boot
if (key()) { show(true); loadReports(); } else { show(false); }
</script>
</body>
</html>
""";
}