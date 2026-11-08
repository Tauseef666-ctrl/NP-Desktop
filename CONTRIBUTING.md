# Contributing to NP Desktop

First of all, thank you — this project grows because people help it grow. This guide explains
what the app is, how its code is organized, how to get it running, and how to send work back.

> NP Desktop is an independent rebuild of NewPipe for Windows desktop and Android TV in
> Kotlin Multiplatform + Compose. It is not affiliated with the official NewPipe team.

---

## 1. What this project is

| Platform | State | Notes |
| --- | --- | --- |
| **Windows / Linux desktop** | Primary target | Compose Desktop app, shipped as MSI / EXE / deb |
| **Android TV** | Target | Same shared UI, TV layout (rail focus, D-pad), packaged as APK |
| **Android phone/tablet** | Legacy compatibility | `:app` module, based on the upstream NewPipe Android app |
| **iOS** | Not started | `shared/src/iosMain` holds stubs only |

The desktop app must stay fast and self-contained: no forced sign-in, everything local-first,
VLC for playback, ffmpeg for merging downloads.

---

## 2. Repository layout

```
├── app/                  Legacy Android app (upstream NewPipe Android, keeps Android working)
├── desktopApp/           Compose Desktop entry point (Main.kt, window/popup windows, packaging)
├── shared/               Everything that matters lives here (Kotlin Multiplatform):
│   │
│   ├── commonMain/       Shared UI & logic
│   │   ├── screen/       Per-section screens: stream/, feed/, search/, downloads/, settings/, ...
│   │   ├── shell/        ModernShell.kt — rail + content + mini player + popup host + dialogs
│   │   ├── composable/   Reusable widgets (video cards, context menus, players)
│   │   ├── service/      PlaybackController, PopupPlayer, DownloadManager, MediaDownloader,
│   │   │                 DownloadLocation/DownloadRepository, ExtractorContentRepository (JVM)
│   │   ├── viewmodel/    Screen state (Koin view models)
│   │   ├── preferences/  Settings keys/values
│   │   ├── di/           Koin modules (KoinApp, ContentModule, settings)
│   │   ├── theme/ & ui/  Themes and design tokens: Spacing, Radius, Glass, Breakpoints
│   │   └── model/        ContentItem, StreamDetails, MediaStream, ...
│   │
│   ├── jvmMain/          Desktop actuals: NewPipe Extractor wiring, VLC playback controller,
│   │                     MediaDownloader (+ ffmpeg merge), DownloadLocation (Swing picker)
│   ├── androidMain/      Android actuals (some stubs)
│   └── iosMain/          iOS actuals (mostly stubs)
└── doc/, assets/         Screenshots and branding
```

**The rule of thumb:** if it is UI or orchestration, it belongs to `shared/commonMain` and must
compile for all targets; anything platform-specific goes behind an `expect` declaration with an
`actual` per source set.

---

## 3. Development setup

### Requirements

- **JDK 21** (Temurin/Adoptium recommended) — the whole multiplatform build is driven from it
- **Android SDK** — required for `:app` and `:shared`'s Android target; easiest via
  Android Studio (open the repo root as a Gradle project)
- **VLC 4.x runtime** for desktop playback — MediaPlayer/Whisker-based VLCj is bundled via
  gradle, the runtimes are packed in `desktopApp/src/main/resources/vlc-runtime.zip`
- A standard desktop toolchain; on Windows run commands from the repo root

### Getting the project running (desktop)

```bash
git clone https://github.com/Tauseef666-ctrl/NP-Desktop.git
cd NP-Desktop

# run the desktop app on your machine (first build takes a few minutes)
./gradlew :desktopApp:run

# build the debug distributable (folder app in desktopApp/build/compose/binaries/main/app)
./gradlew :desktopApp:createDistributable
```

On Windows, the generated launcher lives at
`desktopApp/build/compose/binaries/main/app/NP-Desktop/NP-Desktop.exe`.

### Building for other targets

```bash
# Android debug APK (legacy app module)
./gradlew :app:assembleDebug

# Windows installer (MSI / EXE), Linux (deb)
./gradlew :desktopApp:packageReleaseMsi           # Windows only
./gradlew :desktopApp:packageReleaseExe           # Windows only
./gradlew :desktopApp:packageReleaseDeb           # Linux only
# `packageDistributionForCurrentOS` picks the right one for the running OS

# JVM-only quick compile check (fastest feedback loop)
./gradlew :shared:compileKotlinJvm
```

### Tests

```bash
./gradlew :shared:jvmTest            # JVM unit tests
./gradlew :shared:allTests           # every test source set
```

**Tips for a fast loop:** keep the Gradle daemon warm (drop `--no-daemon`; incremental JVM
compiles take ~1 minute instead of ~20). Use `:shared:compileKotlinJvm` before building the
distributable — the neighbor tasks are usually up-to-date.

---

## 4. Architecture notes (what you must know before coding)

- **Koin everywhere.** Repositories, controllers and services are resolved with
  `koinInject()` defaults at the composition root; register new singletons in
  `shared/commonMain/kotlin/net/newpipe/app/di/`.
- **Extraction works through NewPipe Extractor** — the fork wires `NewPipeExtractor` into
  `ExtractorContentRepository` (jvmMain). First extraction of a video takes ~10–20 s; results
  are cached for 5 minutes, so the next visit is instant. Do not add UI spinners that assume
  sub-second loads.
- **Playback** goes through `PlaybackController` (jvmMain VLC binding). Videos are frequently
  video-only DASH tracks + separate audio; the controller plays both.
- **Downloads** are orchestrated by `DownloadManager` (commonMain). A "video" download may
  fetch the video-only MP4 + best audio and then **merge them with the bundled static ffmpeg**
  (JAVE2) into a single file; `MediaDownloader` (jvmMain) does the merge with `-c copy`.
  Keep that single-file behavior when touching the dialog or the manager.
- **Popup vs mini player:** the popup window (piP) is the mini player. Pressing back or
  switching sections must never kill popup playback; the bottom bar mini player only shows
  when no popup is open. Preserve this contract in `ModernShell.kt`/`StreamScreen.kt`.
- **TV split:** `isTvLayout`/`Breakpoints` decide desktop vs television layout. TV always shows
  the rail and uses D-pad navigation; do not rely on hover for anything essential.
- **Design tokens:** use `Spacing`, `Radius`, `Glass`, `Motion` and `MaterialTheme.colorScheme`
  for every value. Never hardcode colors in screens — the theme must stay in sync in light and
  dark modes.
- **Glass panels:** frosted surfaces are translucent; when you paint text on a custom
  translucent color, set `contentColor` explicitly or texts fall back to raw black.

---

## 5. Code style and project conventions

- Kotlin official style; KDoc comments for every significant class/function. The codebase
  comments are written in English prose — keep that voice.
- Keep SPDX headers on new files:
  ```
  /*
   * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
   * SPDX-License-Identifier: GPL-3.0-or-later
   */
  ```
- One logical change per pull request. Small, reviewable PRs beat big ones.
- UI changes: include a screenshot of before/after in the PR description.
- Behavior changes: describe what you verified manually (e.g. "download of a 1080p video merges
  into one mp4 that plays in VLC and Movies & TV").
- Do not commit generated files, build outputs, or local tooling scripts.
- `TODO-SECOND-AI.md`, `TV-DPAD-AUDIT.md` are local working notes and are git-ignored — never
  commit them.

---

## 6. Sending your work

1. Fork the repo (or work on a branch in the main repo) and keep `main` as the base:
   `git checkout -b feat/my-change main`
2. Compile the JVM target and test what you touched: `./gradlew :shared:compileKotlinJvm`.
3. Commit with clear messages (`feat: …`, `fix: …`, `tv: …`, `chore: …`).
4. Open the PR to `main`. The CI compiles the JVM target; fill the PR template, link the issue
   it fixes (`Fixes #123`).
5. Reviewers check three things: correctness across platforms, fit with the glass design
   language, and speed (nothing may slow down startup or playback).

### Good first issues

Look for issues labeled `good first issue` — typically small UI fixes, TV D-pad gaps, or copy
improvements.

---

## 7. Issues and support

- **Bug reports** and **feature requests**: use the issue templates. Desktop, TV and Android
  each have their own environment selector, so pick the right one.
- Check existing issues before opening a new one; include the app version and a screenshot or
  short screen recording when relevant.

---

## 8. Licensing

The project is licensed under **GPL-3.0-or-later**. Any contribution effectively submitted is
licensed under the same terms, in line with the original NewPipe project.
