// NikaMusic Listen Together server: keeps rooms in memory and relays the host's playback state to guests.
// Run it anywhere that gives you HTTPS (Render, Fly.io, Railway, Hugging Face Spaces...): the app needs wss://.
const http = require('http');
const crypto = require('crypto');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8080;
const MAX_MEMBERS = 20;
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no 0/O/1/I
const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;

const rooms = new Map(); // code -> { host, members: Map<ws, name>, state, stateAt }

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('NikaMusic Listen Together server is running\n');
});
const wss = new WebSocketServer({ server, maxPayload: 8 * 1024 });

const send = (ws, obj) => { if (ws.readyState === 1) ws.send(JSON.stringify(obj)); };
const cleanName = (n) => String(n || 'Guest').replace(/[\u0000-\u001f]/g, '').trim().slice(0, 24) || 'Guest';
const memberNames = (room) => [...room.members.values()];

function newCode() {
  for (;;) {
    let code = '';
    for (let i = 0; i < 6; i++) code += ALPHABET[crypto.randomInt(ALPHABET.length)];
    if (!rooms.has(code)) return code;
  }
}

function broadcast(room, obj, except) {
  for (const ws of room.members.keys()) if (ws !== except) send(ws, obj);
}

function leave(ws) {
  const room = ws.room;
  if (!room) return;
  ws.room = null;
  if (room.host === ws) {
    broadcast(room, { t: 'closed' }, ws);
    for (const m of room.members.keys()) { m.room = null; try { m.close(1000, 'host left'); } catch {} }
    rooms.delete(ws.code);
  } else {
    room.members.delete(ws);
    broadcast(room, { t: 'members', members: memberNames(room) });
  }
}

wss.on('connection', (ws) => {
  ws.isAlive = true;
  ws.room = null;
  ws.tokens = 20;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (data) => {
    // simple rate limit: 20 messages burst, refilled over time
    if (--ws.tokens < 0) return;
    let msg;
    try { msg = JSON.parse(data.toString()); } catch { return; }
    if (!msg || typeof msg.t !== 'string') return;

    if (msg.t === 'create' && !ws.room) {
      const code = newCode();
      const room = { host: ws, members: new Map([[ws, cleanName(msg.name)]]), state: null, stateAt: 0 };
      rooms.set(code, room);
      ws.room = room; ws.code = code;
      send(ws, { t: 'created', code, members: memberNames(room) });
    } else if (msg.t === 'join' && !ws.room) {
      const code = String(msg.code || '').toUpperCase();
      const room = rooms.get(code);
      if (!room) return send(ws, { t: 'error', msg: 'Room not found. Check the code.' });
      if (room.members.size >= MAX_MEMBERS) return send(ws, { t: 'error', msg: 'This room is full.' });
      room.members.set(ws, cleanName(msg.name));
      ws.room = room; ws.code = code;
      const joined = { t: 'joined', code, members: memberNames(room) };
      if (room.state) joined.state = { ...room.state, age: Date.now() - room.stateAt };
      send(ws, joined);
      broadcast(room, { t: 'members', members: memberNames(room) }, ws);
    } else if (msg.t === 'state' && ws.room && ws.room.host === ws) {
      if (!VIDEO_ID.test(String(msg.songId))) return;
      const pos = Number(msg.pos);
      const state = {
        t: 'state',
        songId: String(msg.songId),
        title: String(msg.title || '').slice(0, 200),
        artist: String(msg.artist || '').slice(0, 200),
        playing: msg.playing === true,
        pos: Number.isFinite(pos) && pos >= 0 ? Math.floor(pos) : 0,
      };
      ws.room.state = state;
      ws.room.stateAt = Date.now();
      broadcast(ws.room, { ...state, age: 0 }, ws);
    }
  });

  ws.on('close', () => leave(ws));
  ws.on('error', () => leave(ws));
});

// refill rate-limit tokens, drop dead connections
setInterval(() => {
  for (const ws of wss.clients) {
    ws.tokens = Math.min(20, ws.tokens + 5);
    if (!ws.isAlive) { ws.terminate(); continue; }
    ws.isAlive = false;
    try { ws.ping(); } catch {}
  }
}, 5000);

server.listen(PORT, () => console.log(`Listen Together server on :${PORT}`));
