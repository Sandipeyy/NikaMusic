# Listen Together server

A tiny Node.js server that lets NikaMusic users listen in sync. It only relays "which song, playing or paused, at what position". It never touches audio or accounts, and rooms live in memory only.

## Deploy (free options)

The app needs a **wss://** address, so use a host that gives you HTTPS. For example **Render**:

1. Push this repo to GitHub, then on [render.com](https://render.com) choose *New → Web Service* and pick the repo.
2. Set **Root Directory** to `server/listen-together`, **Build Command** to `npm install`, **Start Command** to `npm start`.
3. When it is live, your address is `wss://YOUR-SERVICE.onrender.com`.

Free tiers go to sleep when idle, so the first connection after a break can take about a minute. Fly.io, Railway and Hugging Face Spaces (Docker) work the same way, a `Dockerfile` is included.

## Use it in the app

*Settings tab → Listen together* → paste your `wss://…` address, pick a name, then **Create a room**. Friends open the same dialog with the same address and type the 6-character code.

## Run locally

```bash
npm install
npm start    # listens on :8080
```

For a quick test on your own network you need a TLS proxy in front of it, because the app only accepts `wss://`.
