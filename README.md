<div align="center">

<img src="assets/icon.png" width="140" alt="NikaMusic logo" />

# NikaMusic

**A free, open-source music player for Android.**
Stream from YouTube Music, sync lyrics, download for offline and customise almost everything.

[![Build](https://github.com/Sandipeyy/NikaMusic/actions/workflows/build.yml/badge.svg)](https://github.com/Sandipeyy/NikaMusic/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)

[Download](https://github.com/Sandipeyy/NikaMusic/releases) · [Report a bug](https://github.com/Sandipeyy/NikaMusic/issues) · [Discord](https://discord.com/users/716445719807918160)

</div>

---

## Features

**Playback**
- Streaming through YouTube Music, no account needed
- Crossfade, sleep timer, data saver and lossless/high-bitrate options
- Equalizer with DSP and AutoEq profiles, USB audio output
- Animated canvas visuals

**Lyrics**
- Synced and word-by-word lyrics from several providers
- Lyrics translation

**Library**
- Offline downloads, playlists, backup and restore
- Import playlists from Spotify and CSV files
- Last.fm scrobbling and Discord rich presence

**Extras**
- **Liquid glass** floating navigation bar: the content behind it is bent at the edges like thick glass, with light highlights and a lens that follows the selected tab (Android 13+; a lighter frosted look on Android 12 and with "Disable blur" on). Slide a finger across the bar to switch tabs
- **Listen together:** host a room, share a code, and friends follow your playback in sync (needs the small server in `server/listen-together`)
- A **Recognize** tab with its own page and history, plus a Quick Settings tile
- Set any song (or a trimmed part of it) as your ringtone
- Home-screen widgets in six styles: Modern Flow, Compact Glow, Showcase Card, Slim Strip, Vinyl Square and Vintage Turntable

## Install

1. Open the [Releases](https://github.com/Sandipeyy/NikaMusic/releases) page.
2. Download the APK for your phone. Use `arm64` for almost every modern device, or `universal` if you are unsure.
3. Open the file and allow installing from your browser or file manager when Android asks.

## Build it yourself

**With GitHub Actions (no setup):**
fork the repo → *Actions* → *Build NikaMusic* → *Run workflow* → download the APK from the run's artifacts.

**Locally:** you need JDK 21, the Android SDK (API 36), NDK `27.0.12077973` and CMake `3.22.1`.

```bash
git clone https://github.com/Sandipeyy/NikaMusic.git
cd NikaMusic
./gradlew assembleArm64FossRelease      # or assembleUniversalFossRelease
```

The APK ends up in `app/build/outputs/apk/`.

**Signed releases (optional):** add these repository secrets so Actions signs the APK with your own key. Without them the APK is signed with a debug key, which installs fine but is not suitable for public updates.

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | your keystore, base64-encoded |
| `STORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Never commit your keystore. It is already in `.gitignore`.

## Logins (Last.fm and Discord)

Both logins need keys that are built into the app, so they only work in builds that were given them. Without them the app simply says the login is not set up. Add the keys as **repository secrets** (Settings → Secrets and variables → Actions) before you build.

**Last.fm** (scrobbling and loves)
1. Go to [last.fm/api/account/create](https://www.last.fm/api/account/create) and create an API account.
2. Add the secrets `LASTFM_API_KEY` (the API key) and `LASTFM_SECRET` (the shared secret).
3. Users then sign in inside the app with their Last.fm username and password.

**Discord** (rich presence)
1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and create an application (name it NikaMusic).
2. Copy its **Application ID** into the secret `DISCORD_CLIENT_ID`.
3. In *OAuth2 → Redirects*, add exactly `discord-YOUR_APPLICATION_ID:/authorize/callback`, and turn on *Public Client*, because the app signs in with PKCE and has no client secret.
4. The app asks for the `sdk.social_layer_presence` scope, which belongs to Discord's Social SDK, so also enable the Social SDK / rich presence for the application in the portal. Discord changes this part of the portal now and then, so follow its current wording.

Building locally instead? Put `LASTFM_API_KEY`, `LASTFM_SECRET` and `DISCORD_CLIENT_ID` in `local.properties`.

## Listen together

Open **Profile → Listen together** (or **Now Playing → ⋯ → Listen together**), then:

1. **Create a room** or **join** one with its code
2. **Share** the invite link or the 6-character code
3. **Listen together**: everyone hears what the host plays, in sync

Inside a room: members with their names and profile pictures, live; the host's play, pause, skip and seek reach everyone; a shared queue that guests can add to from any song menu (**Add to room queue**); the host can remove members; and a dropped connection reconnects on its own for about 90 seconds. Nobody ever types a server address.

**For whoever builds the app (one-time setup):** Listen together talks to a small relay server that you host once and build into the app. It never touches audio or accounts.
1. Deploy [`server/listen-together`](server/listen-together): on [Render](https://render.com) use *New → Blueprint* and pick this repo, the included `render.yaml` does the rest.
2. Add the repository secret `LISTEN_TOGETHER_SERVER` with the service address, using `wss://` (for example `wss://nikamusic-listen-together.onrender.com`).
3. Build the app. In a build without it the page just says Listen together isn't available right now.

If you hosted an older version of the server, redeploy it: this version speaks a newer protocol.

## Updates

NikaMusic checks this repo's releases for a newer version. Open *Settings → version card* to check by hand, read what's new, and download and install the update right inside the app.

## Releasing a new version

1. Add a section called `## v1.0.1` (use your new version) at the top of [CHANGELOG.md](CHANGELOG.md) with what changed.
2. Commit and push, then tag it:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
3. GitHub Actions builds the `arm64` and `universal` APKs, sets the version from the tag, and publishes a GitHub Release with your changelog section as the notes. Users then see the update and the notes inside the app.

Release builds need the four signing secrets above. Every release has to be signed with the same key, otherwise Android refuses to install the update over the old version.

## Project layout

| Folder | What is inside |
|---|---|
| `app/` | the Android app (Jetpack Compose UI, Hilt, Room, Media3) |
| `providers/` | YouTube Music client, lyrics sources, Shazam and Last.fm |
| `modules/` | shared library modules |
| `server/listen-together/` | the Node.js backend for Listen together |
| `.github/workflows/` | the build workflow |

## Credits

NikaMusic would not exist without these projects and their authors:

- **[Mochy Music](https://github.com/realidkroo/Mochy-Music)** by realidkroo, the base of this app
- **[ViVi Music](https://github.com/vivizzz007/vivi-music)** by vivizzz007
- **[Metrolist](https://github.com/mostafaalagamy/Metrolist)** by Mostafa Alagamy
- **[Echo Music](https://github.com/iad1tya/Echo-Music)** by iad1tya, source of the widget skins, ringtone dialog and recognition tile
- [SimpMusic](https://github.com/maxrave-dev/SimpMusic), [ArchiveTune](https://github.com/rukamori/ArchiveTune), [RiMusic](https://github.com/fast4x/RiMusic)
- [LRCLIB](https://github.com/tranxuanthang/lrclib), [LyricsPlus](https://github.com/ibratabian17/LyricsPlus), [better-lyrics](https://github.com/better-lyrics/api) and [AutoEq](https://github.com/jaakkopasanen/AutoEq)

See [NOTICE.md](NOTICE.md) for details on what was changed.

## License

NikaMusic is licensed under the **GNU General Public License v3.0**, see [LICENSE](LICENSE).
It is a derivative work, so it stays GPL-3.0 and its source must remain available. Original copyright notices are kept.

## Disclaimer

NikaMusic is not affiliated with, endorsed by or connected to YouTube, Google, Spotify, Discord or any other service it can talk to. All trademarks belong to their owners. The app uses unofficial interfaces, which may go against those services' terms. You are responsible for how you use it.

---

<div align="center">Made by <a href="https://github.com/Sandipeyy">Sandipeyy</a></div>
