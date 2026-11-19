# Listen Together server

A tiny Node.js server that lets NikaMusic users listen in sync (protocol v2). It relays the host's "which song, playing or paused, at what position" and queue, member names and tiny profile pictures, song suggestions from guests, and "remove this member". It never touches audio or accounts, and rooms live in memory only. A dropped member keeps their seat for 90 seconds (`GRACE_MS` changes it), so the app can reconnect.

## Deploy (free options)

The app needs a **wss://** address, so use a host that gives you HTTPS. For example **Render**:

1. Push this repo to GitHub, then on [render.com](https://render.com) choose *New → Web Service* and pick the repo.
2. Set **Root Directory** to `server/listen-together`, **Build Command** to `npm install`, **Start Command** to `npm start`.
3. When it is live, your address is `wss://YOUR-SERVICE.onrender.com`.

Free tiers go to sleep when idle, so the first connection after a break can take about a minute. Fly.io, Railway and Hugging Face Spaces (Docker) work the same way, a `Dockerfile` is included. The repo root has a `render.yaml` blueprint if you would rather not click through the settings.

## Connect the app

The address is built into the app, so people never see or type it. Add it as the repository secret `LISTEN_TOGETHER_SERVER` (with `wss://`, e.g. `wss://YOUR-SERVICE.onrender.com`) before building, or put it in `local.properties` for a local build. Without it, the app's Listen together page says it isn't available.

On a free tier the service sleeps when idle, so the first connection after a break can take up to a minute. The app waits for it and retries once on its own.

## Run locally

```bash
npm install
npm start    # listens on :8080
```

For a quick test on your own network you need a TLS proxy in front of it, because the app only accepts `wss://`.
