<div align="center">

# 🎵 Mekronize

**Your own music & video library — managed on the web, played offline on Android.**

[English](README.md) · [Türkçe](README.tr.md)

![Laravel](https://img.shields.io/badge/Laravel-13-FF2D20?logo=laravel&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white)
![minSdk](https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android&logoColor=white)
![APK](https://img.shields.io/badge/APK-~4.7%20MB-5b5bf0)

</div>

## Why Mekronize?

Streaming apps need a connection, ads and a subscription. Your phone or car, on the other hand, often has none of those.
Mekronize lets you curate a library once on a comfortable web panel, then **sync it to your phone and play it with no internet at all** — in a car-friendly, big-tile interface that also works with Android Auto.

There are no accounts, emails or passwords. Your identity is a single **6-digit key**.

## How it works

```
 ┌──────────────┐   links / uploads   ┌──────────────┐    sync key    ┌──────────────────┐
 │  Web panel   │ ──────────────────▶ │ Laravel API  │ ◀───────────── │  Android app     │
 │ (manage all) │                     │ (cPanel OK)  │ ──────────────▶│ offline player   │
 └──────────────┘                     └──────────────┘   manifest     └──────────────────┘
                                                                    downloads files / YouTube
```

1. Open the web panel and sign in with a 6-digit key (or create a new one).
2. Add content: paste YouTube links (single videos, playlists, even auto-generated Mixes) or drag & drop audio/video files.
3. Organize playlists, rename or remove items.
4. In the Android app enter the same key and press **Sync**. New items download, deleted ones disappear from the device.

The phone and Android Auto are **read-only**: everything is managed from the web.

## Features

### Web panel (`backend/`)
- Light, modern dashboard with library stats, filters and inline renaming.
- **YouTube links** — the server stores only the link; the device downloads at sync time (no server storage or bandwidth).
- **Playlist detection** — paste a `watch?v=…&list=…` link and it asks whether to add the whole list (up to 300 videos; Mixes `RD…` give ~25) and optionally creates a Mekronize playlist.
- **File uploads** — drag & drop, multiple files, chunked (4 MB) with per-file progress; works on small shared hosting limits.
- Playlists with ordering, 6-digit key auth, key rotation limited to 4 changes per hour, signed download URLs and rate limiting.
- Deployable to plain **cPanel** hosting (MySQL or SQLite), see `backend/deploy-cpanel.sh`.

### Android app (`android/`)
- **Offline-first**; opens straight into your local library.
- CarPlay-style home: big tiles for Playlists · All · Videos · Music · Favorites · Shuffle · Sync · Settings. Empty categories are dimmed.
- A real player: mini bar that expands to a full player; **swipe left/right** to change tracks; shuffle, repeat, favorites.
- Video mode: controls auto-hide and the video fills the screen; one tap brings them back without pausing.
- **Android Auto** browse tree (audio).
- Runs on **Android 5.0+** and low-RAM devices; ~4.7 MB release APK, no Compose.
- YouTube extraction on-device with NewPipe Extractor.

## Tech stack

| Part | Stack |
|------|-------|
| Backend | PHP 8.3, Laravel 13, MySQL/SQLite, Blade, vanilla JS |
| Android | Kotlin, XML views, Media3 ExoPlayer, SQLite, OkHttp, NewPipe Extractor, R8 |

## Getting started

**Backend**
```bash
cd backend
composer install
cp .env.example .env && php artisan key:generate
php artisan migrate
php artisan serve          # http://127.0.0.1:8000
php artisan test
```

**Android**
```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease -Pmekronize.baseUrl=https://your-server.example   # signed release
```
The release build is also copied to `dist/mekronize-<version>.apk`. More details in [`backend/README.md`](backend/README.md) and [`android/README.md`](android/README.md).

## Legal notice

The Android app contains a module that can download YouTube links on the device. YouTube's Terms of Service prohibit downloading outside the means it provides, and content may be copyrighted.
This software is offered for personal use with content you own or are permitted to download. Responsibility for its use rests with the user.

## License

See [LICENSE](LICENSE).
