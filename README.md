# Nika Music

A Material You music player for Android.

> **Nika Music is a fork of [Echo Music](https://github.com/EchoMusicApp/Echo-Music)**, an open-source (GPL-3.0)
> YouTube Music client. All credit for the original app goes to the Echo Music authors and contributors.

## What is different from Echo Music

- New name, new logo and new package ID: `nika.music.sandip`
- App-icon choices trimmed to the Nika icon plus Billie Eilish, Sabrina Carpenter and Dog
- The original developer's promo, social and donation links were removed (Nika Music has none yet)
- The About page shows this project's own developer and GitHub info

Everything else (playback, library, synced lyrics, song recognition, downloads, widgets, Wrapped, equalizer and
more) comes from Echo Music. See the upstream project for the full feature list and for features I haven't verified.

## Get the app

Open the **Releases** page of this repository and download the file ending in `.apk` under **Assets**
(not "Source code"). Allow installs from your browser if Android asks.

## Build it yourself (no computer needed)

1. Open **Actions** -> **Build Nika Music** -> **Run workflow**.
2. When it finishes, the APK is published under **Releases**.

On a computer: `./gradlew assembleUniversalFossDebug` (JDK 21, Android SDK + NDK 28.2.13676358).

## Credits and inspiration

Echo Music (upstream project), and the open-source projects this app draws on:
Metrolist and Vivi Music (architecture), ArchiveTune (Material You UI), Better Lyrics and SimpMusic (lyrics),
Music Recognizer (audio recognition), BravePipe (playback and decryption handling).

## Licence

GPL-3.0. See [LICENSE](LICENSE). Because this app is a derivative of GPL-3.0 software, it and any changes to it
must stay open source under the same licence.
