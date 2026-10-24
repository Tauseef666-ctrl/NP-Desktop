# YT Desktop

<p align="center">
  <img src="assets/yt_desktop_chosen.svg" width="120" alt="YT Desktop Logo">
</p>

<h3 align="center">A modern, rebuilt fork of <a href="https://github.com/TeamNewPipe/NewPipe">NewPipe</a> for Windows Desktop and Android TV</h3>

<p align="center">
  <a href="https://github.com/Tauseef666-ctrl/YT-DESKTOP-/releases"><img src="https://img.shields.io/github/v/release/Tauseef666-ctrl/YT-DESKTOP-?label=latest%20release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg"></a>
</p>

## About

**YT Desktop** is a personal rebuild of NewPipe with a heavy focus on a proper Windows desktop experience, alongside continued support for Android TV and Android. The goal is to bring a cleaner, more app-like feel to the desktop with proper windowing, keyboard shortcuts, navigation, and media controls while keeping the spirit of NewPipe intact.

> This project is not affiliated with, endorsed by, or connected to the official NewPipe team. All credit for the original idea, extractor, and core goes to the [NewPipe](https://github.com/TeamNewPipe/NewPipe) project and its contributors.

## Platforms

- **Windows Desktop (x64)** – Primary focus. Built with Kotlin Multiplatform + Compose Desktop
- **Android TV** – Optimized for D-pad/remote navigation
- **Android Phone/Tablet** – Kept functional for compatibility

## Key Features

- **Windows Desktop UI** – Custom modern shell, side rail navigation, resizable window, proper fullscreen/immersive modes
- **Keyboard Shortcuts** – YouTube-like shortcuts for playback, volume, captions, speed, seeking and more
- **Miniplayer / Picture-in-Picture style popup** – Keep watching while browsing
- **Downloads** – Built-in download UI for supported streams
- **History & Subscriptions** – Local history, subscription management, and personalized home feed
- **Kiosk Sections** – Trending, Live, Gaming, Music, Movies & Shows, Podcasts
- **Multi-service support** – YouTube, SoundCloud, PeerTube, Bandcamp, media.ccc.de
- **Local-first** – No forced account login. Subscriptions, history and preferences stay local by default

## Supported Services

- [YouTube](https://www.youtube.com/) & [YouTube Music](https://music.youtube.com/)
- [PeerTube](https://joinpeertube.org/) (and all instances)
- [Bandcamp](https://bandcamp.com/)
- [SoundCloud](https://soundcloud.com/)
- [media.ccc.de](https://media.ccc.de/)

## Download

Head over to the [Releases](https://github.com/Tauseef666-ctrl/YT-DESKTOP-/releases) page to download the latest Windows installer (`.msi`) or Android TV/Phone APK.

## Building From Source

### Prerequisites
- JDK 21
- Android SDK (for Android builds)
- Git

### Windows Desktop
```bash
./gradlew.bat :desktopApp:packageMsi
```
The built installer will be available under `desktopApp/build/compose/binaries/main/msi/`

### Android / Android TV
```bash
./gradlew.bat assembleRelease
```

## License

This project is licensed under the **GNU General Public License v3.0 or later**. See [LICENSE](LICENSE) for more details.

## Credits

- [NewPipe](https://github.com/TeamNewPipe/NewPipe) - Original project and NewPipeExtractor
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) - The backbone for fetching content from supported services
- All upstream contributors who made this possible

## Disclaimer

Using third-party clients may be against the Terms of Service of some platforms. Use at your own discretion and in compliance with your local laws.
