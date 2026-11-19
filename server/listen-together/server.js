// NikaMusic Listen Together server (protocol v2).
// Rooms live in memory. The host's playback state and queue are relayed to everyone; guests can suggest songs,
// the host can remove members, and a dropped connection has a grace period to come back with its session token.
// The app needs wss://, so run it anywhere that gives you HTTPS (Render, Fly.io, Railway, Hugging Face Spaces...).
const http = require('http');
const crypto = require('crypto');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8080;
const MAX_MEMBERS = 20;
const MAX_QUEUE = 50;
const GRACE_MS = Number(process.env.GRACE_MS || 90_000); // how long a dropped member (or host) can come back
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no 0/O/1/I
const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;
const BASE64 = /^[A-Za-z0-9+/=]*$/;

const rooms = new Map(); // code -> room

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('NikaMusic Listen Together server is running\n');
});
const wss = new WebSocketServer({ server, maxPayload: 16 * 1024 });

const send = (ws, obj) => { if (ws && ws.readyState === 1) ws.send(JSON.stringify(obj)); };
const clean = (s, n) => String(s ?? '').replace(/[\u0000-\u001f]/g, '').trim().slice(0, n);
const cleanName = (n) => clean(n, 24) || 'Guest';
const cleanAvatar = (a) => (typeof a === 'string' && a.length > 0 && a.length <= 4000 && BASE64.test(a)) ? a : null;
const newId = () => crypto.randomBytes(4).toString('hex');
const newToken = () => crypto.randomBytes(16).toString('hex');

function newCode() {
  for (;;) {
    let code = '';
    for (let i = 0; i < 6; i++) code += ALPHABET[crypto.randomInt(ALPHABET.length)];
    if (!rooms.has(code)) return code;
  }
}

const memberList = (room) => [...room.members.values()].map((m) => ({
  id: m.id, name: m.name, avatar: m.avatar, host: m.id === room.hostId, online: !!m.ws,
}));
const broadcast = (room, obj, exceptId) => {
  for (const m of room.members.values()) if (m.id !== exceptId) send(m.ws, obj);
};
const sendMembers = (room) => broadcast(room, { t: 'members', members: memberList(room) });

function snapshot(room, code, member) {
  const out = {
    code, you: member.id, token: member.token, hostId: room.hostId, members: memberList(room), max: MAX_MEMBERS,
  };
  if (room.state) out.state = { ...room.state, age: Date.now() - room.stateAt };
  if (room.queue) out.queue = room.queue;
  return out;
}

function closeRoom(room, why) {
  rooms.delete(room.code);
  for (const m of room.members.values()) {
    clearTimeout(m.timer);
    send(m.ws, { t: 'closed', reason: why });
    if (m.ws) { m.ws.member = null; m.ws.room = null; try { m.ws.close(1000, 'closed'); } catch {} }
  }
}

function removeMember(room, member, notify) {
  clearTimeout(member.timer);
  room.members.delete(member.id);
  if (member.ws) { member.ws.member = null; member.ws.room = null; }
  if (notify) sendMembers(room);
}

// the socket went away without a goodbye: keep the seat for a while
function dropped(ws) {
  const { member, room } = ws;
  ws.member = null; ws.room = null;
  if (!member || !room || !rooms.has(room.code) || member.ws !== ws) return;
  member.ws = null;
  sendMembers(room);
  member.timer = setTimeout(() => {
    if (!rooms.has(room.code) || member.ws) return;
    if (member.id === room.hostId) closeRoom(room, 'host_gone');
    else removeMember(room, member, true);
  }, GRACE_MS);
}

wss.on('connection', (ws) => {
  ws.isAlive = true; ws.member = null; ws.room = null; ws.tokens = 20;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (data) => {
    if (--ws.tokens < 0) return; // rate limit: a burst of 20, refilled over time
    let msg;
    try { msg = JSON.parse(data.toString()); } catch { return; }
    if (!msg || typeof msg.t !== 'string') return;
    const { member, room } = ws;
    const isHost = !!(room && member && room.hostId === member.id);

    switch (msg.t) {
      case 'create': {
        if (member) return;
        const code = newCode();
        const m = { id: newId(), token: newToken(), name: cleanName(msg.name), avatar: cleanAvatar(msg.avatar), ws, timer: null };
        const r = { code, hostId: m.id, members: new Map([[m.id, m]]), banned: new Set(), state: null, stateAt: 0, queue: null };
        rooms.set(code, r);
        ws.member = m; ws.room = r;
        send(ws, { t: 'created', ...snapshot(r, code, m) });
        break;
      }
      case 'join': {
        if (member) return;
        const code = clean(msg.code, 6).toUpperCase();
        const r = rooms.get(code);
        if (!r) return send(ws, { t: 'error', code: 'no_room', msg: 'Room not found. Check the code.' });
        if (r.members.size >= MAX_MEMBERS) return send(ws, { t: 'error', code: 'full', msg: 'This room is full.' });
        const m = { id: newId(), token: newToken(), name: cleanName(msg.name), avatar: cleanAvatar(msg.avatar), ws, timer: null };
        r.members.set(m.id, m);
        ws.member = m; ws.room = r;
        send(ws, { t: 'joined', ...snapshot(r, code, m) });
        broadcast(r, { t: 'members', members: memberList(r) }, m.id);
        break;
      }
      case 'rejoin': {
        if (member) return;
        const code = clean(msg.code, 6).toUpperCase();
        const r = rooms.get(code);
        const token = clean(msg.token, 64);
        const m = r && [...r.members.values()].find((x) => x.token === token);
        if (!r || !m) return send(ws, { t: 'error', code: 'expired', msg: 'The session is over.' });
        clearTimeout(m.timer);
        if (m.ws && m.ws !== ws) { m.ws.member = null; m.ws.room = null; try { m.ws.close(1000, 'replaced'); } catch {} }
        m.ws = ws; ws.member = m; ws.room = r;
        send(ws, { t: 'rejoined', ...snapshot(r, code, m) });
        sendMembers(r);
        break;
      }
      case 'state': {
        if (!isHost || !VIDEO_ID.test(String(msg.songId))) return;
        const pos = Number(msg.pos);
        room.state = {
          t: 'state', songId: String(msg.songId), title: clean(msg.title, 200), artist: clean(msg.artist, 200),
          playing: msg.playing === true, pos: Number.isFinite(pos) && pos >= 0 ? Math.floor(pos) : 0,
        };
        room.stateAt = Date.now();
        broadcast(room, { ...room.state, age: 0 }, member.id);
        break;
      }
      case 'queue': {
        if (!isHost || !Array.isArray(msg.items)) return;
        room.queue = msg.items.slice(0, MAX_QUEUE)
          .filter((i) => i && VIDEO_ID.test(String(i.id)))
          .map((i) => ({ id: String(i.id), title: clean(i.title, 120), artist: clean(i.artist, 120) }));
        broadcast(room, { t: 'queue', items: room.queue }, member.id);
        break;
      }
      case 'suggest': {
        if (!room || !member || isHost || !VIDEO_ID.test(String(msg.songId))) return;
        const now = Date.now();
        if (member.lastSuggest && now - member.lastSuggest < 3000) return send(ws, { t: 'error', code: 'slow', msg: 'Slow down a little.' });
        member.lastSuggest = now;
        const host = room.members.get(room.hostId);
        send(host && host.ws, {
          t: 'suggestion', from: member.id, name: member.name, songId: String(msg.songId),
          title: clean(msg.title, 120), artist: clean(msg.artist, 120),
        });
        break;
      }
      case 'kick': {
        if (!isHost) return;
        const target = room.members.get(String(msg.id));
        if (!target || target.id === member.id) return;
        send(target.ws, { t: 'kicked' });
        const tws = target.ws;
        room.banned.add(target.token);
        removeMember(room, target, true);
        if (tws) { try { tws.close(1000, 'kicked'); } catch {} }
        break;
      }
      case 'leave': {
        if (!room || !member) return;
        if (isHost) closeRoom(room, 'host_left');
        else { const w = ws; removeMember(room, member, true); try { w.close(1000, 'bye'); } catch {} }
        break;
      }
    }
  });

  ws.on('close', () => dropped(ws));
  ws.on('error', () => dropped(ws));
});

setInterval(() => {
  for (const ws of wss.clients) {
    ws.tokens = Math.min(20, ws.tokens + 5);
    if (!ws.isAlive) { ws.terminate(); continue; }
    ws.isAlive = false;
    try { ws.ping(); } catch {}
  }
}, 5000);

server.listen(PORT, () => console.log(`Listen Together server (v2) on :${PORT}`));
