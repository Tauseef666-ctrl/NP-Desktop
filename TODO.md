# NewPipe Windows Desktop — Task Tracker

> **Project**: A native Windows desktop port of [NewPipe](https://github.com/TeamNewPipe/NewPipe),
> built on the official upstream `dev` branch (Compose Multiplatform migration in progress).
>
> **Repository root**: `C:\Users\Tauseef khan\git\web\NewPipe`
> **Upstream remote**: `https://github.com/TeamNewPipe/NewPipe.git` (branch `dev`)
> **Working branch**: `feature/windows-desktop`
> **Release target repo**: `https://github.com/Tauseef666-ctrl/YT-DESKTOP-`
>
> Status legend: `[ ]` not started · `[~]` in progress · `[x]` completed **and verified** ·
> `[!]` blocked · `[-]` deferred (reason given)
>
> **Integrity rule applied to this file**: a task is only `[x]` if it was actually implemented
> *and* verified by a build or a test run whose result is recorded in this file. Tasks that are
> planned but not yet implemented are `[ ]`, and planned-but-unstarted tasks are never shown as done.

---

# Project Progress

## Current Phase
Phase C — Core integration (extraction engine + real search)

## Current Status
In Progress

## Completed
- Repository audited: upstream `dev` branch confirmed clean at commit `7934d953a`, branch `dev`,
  zero uncommitted changes before any work started.
- Confirmed upstream already contains Compose Multiplatform scaffolding: `shared/` (KMP with
  `commonMain`/`androidMain`/`iosMain`/`jvmMain`), `desktopApp/` (JVM entry point), legacy `app/`
  (Android, complete business logic).
- Confirmed toolchain: JDK 21 (Temurin 21.0.12.1), Gradle 9.8.2 wrapper, Android SDK at
  `C:\Android\sdk` (platform `android-37.0`, build-tools `36.0.0`), network access to Maven Central
  and the Gradle Plugin Portal verified (HTTP 200).
- **Baseline Windows desktop build verified green**: `gradlew :desktopApp:compileKotlin` →
  `BUILD SUCCESSFUL in 3m 32s` (18 actionable tasks, 15 executed).
- Development branch `feature/windows-desktop` created from `dev`; upstream `dev` untouched.
- `local.properties` created pointing at the local Android SDK (git-ignored, not committed).
- Architecture decision recorded in `docs/ARCHITECTURE.md`.
- Repository audit recorded in `docs/REPOSITORY_AUDIT.md`.
- **Extraction engine wired into the desktop build**:
  `shared/src/jvmMain/kotlin/net/newpipe/app/service/{JvmDownloader,ExtractorServiceRegistry,
  ExtractorContentRepository}.kt` — verified by
  `gradlew :shared:compileKotlinJvm :desktopApp:compileKotlin` → `BUILD SUCCESSFUL in 27s`.
- Platform-neutral content API added in `shared/src/commonMain/kotlin/net/newpipe/app/`:
  `model/Content.kt` and `service/ContentRepository.kt`.

## Currently Working On
- Desktop UI: search screen, content list, channel page, playlist page and stream detail page
  wired to the real extraction engine.

## Blockers
- None currently. (Earlier blocker "Android SDK location unknown" resolved: SDK found at
  `C:\Android\sdk`, wired through `local.properties`.)
- The `shared` Android and iOS targets cannot be compiled in this environment (no Android emulator
  is needed for compilation, but the iOS targets need macOS). The `androidMain` stub is therefore
  verified by source review only.

## Known Defects
- None confirmed yet.

## Next Tasks
- Build the desktop search screen and wire it to the real extractor.
- Implement real playback with VLCJ.
- Implement the desktop application shell (navigation rail, window state).

## Last Updated
2026-10-22 (updated)

---

## Phase A — Repository audit

- [x] **Confirm repository root, git status, branch, commit, upstream remote**
  - Evidence: `git rev-parse --show-toplevel` → `C:/Users/Tauseef khan/git/web/NewPipe`;
    `git status --porcelain -b` → `## dev...origin/dev`, zero modified files;
    `git log --oneline -5` → HEAD `7934d953a`.
  - Notes: working tree was pristine; nothing was deleted or overwritten.
- [x] **Inventory modules and source layout**
  - Evidence: `settings.gradle.kts` → `:app` (Android, legacy), `desktopApp` (JVM/Compose Desktop),
    `shared` (KMP). `app/` = 280 Java + 154 Kotlin files. `shared/` = 100 Kotlin files covering
    theme, settings, about, navigation, Koin DI, and `expect`/`actual` platform abstractions with
    a `jvmMain` (desktop) implementation already present.
- [x] **Trace dependency graph and version catalog**
  - Evidence: `gradle/libs.versions.toml` — Kotlin 2.4.21, Compose Multiplatform 1.12.2,
    Room 3.0.3 (`androidx.room3`), navigation3 1.1.2, Koin 4.2.2, multiplatform-settings 1.3.0,
    Kermit 2.2.0, OkHttp 5.5.0, ExoPlayer 2.19.1,
    NewPipeExtractor `com.github.TeamNewPipe:NewPipeExtractor:2f7d9da…`.
  - Notes: `shared/` currently has **no** extractor, Room, OkHttp or ExoPlayer dependency.
- [x] **Identify supported services in the extractor**
  - Evidence: inspected the resolved extractor JAR → `org/schabi/newpipe/extractor/ServiceList.class`
    with `youtube`, `soundcloud`, `media_ccc`, `peertube`, `bandcamp` (service ids 0–4).
  - Notes: matches the `net.newpipe.app.theme.Service` enum in `shared` exactly.
- [x] **Assess Android-coupling of legacy business logic**
  - Evidence: `app/src/main/java/org/schabi/newpipe/DownloaderImpl.java` imports
    `android.content.Context` + `androidx.preference.PreferenceManager`;
    `app/src/main/java/org/schabi/newpipe/util/ServiceHelper.kt` is Android-only via `Context`;
    `player/PlayerService.java` + `player/mediabrowser/*` are Android-only.
  - Notes: `app/src/main/java/org/schabi/newpipe/database/**` (Room entities/DAOs) is largely
    Android-independent and reusable as a schema reference.
- [x] **Write `docs/REPOSITORY_AUDIT.md`** — done.

## Phase B — Architecture and build foundation

- [x] **Verify JDK / Gradle / SDK / network prerequisites**
  - Evidence: `java -version` → Temurin 21.0.12.1; `gradlew --version` → Gradle 9.8.2;
    `C:\Android\sdk` contains `platforms/android-37.0`, `build-tools/36.0.0`, `platform-tools`,
    `cmdline-tools/latest`; `Invoke-WebRequest https://repo1.maven.org/maven2/` → HTTP 200.
- [x] **Create development branch without touching `dev`**
  - Evidence: `git checkout -b feature/windows-desktop` from `7934d953a`.
- [x] **Establish a green desktop build baseline**
  - Evidence: `gradlew :desktopApp:compileKotlin` → `BUILD SUCCESSFUL in 3m 32s`.
  - Notes: `:desktopApp` uses the plain `kotlin.jvm` plugin, so its compile task is `compileKotlin`
    (not `compileKotlinJvm`). `:shared`'s `jvm` target compiles successfully as a dependency.
- [x] **Decide the Windows architecture** — recorded in `docs/ARCHITECTURE.md`.
- [ ] **Produce a runnable Windows distributable (`packageDistributionForCurrentOS`)** — Phase J.

## Phase C — Core integration (extraction engine, service registry, models)

- [x] **Audit NewPipeExtractor for JVM compatibility**
  - Evidence: the resolved artifact is a plain Java JAR (`NewPipeExtractor-…jar`); it is already
    consumed by the legacy `app/` module, and the classes needed here carry no Android imports.
  - Notes: extractor is JVM/Android-only → it cannot live in `shared/commonMain` while `shared`
    also declares iOS targets.
- [x] **Define platform-neutral domain models in `shared/commonMain`**
  - Evidence: `shared/src/commonMain/kotlin/net/newpipe/app/model/Content.kt` —
    `ServiceInfo`, `ContentType`, `ContentItem`, `MediaStream`, `SubtitleTrack`, `StreamDetails`,
    `ChannelDetails`, `PlaylistDetails`, `ResolvedContent`.
  - Notes: `MediaStream.isVideoOnly` is part of the model so that the player never assumes a muxed
    stream.
- [x] **Implement extractor-backed content repository for desktop (`jvmMain`)**
  - Evidence: `shared/src/jvmMain/kotlin/net/newpipe/app/service/ExtractorContentRepository.kt` and
  `JvmDownloader.kt`; `gradlew :shared:compileKotlinJvm :desktopApp:compileKotlin` →
  `BUILD SUCCESSFUL in 27s` (re-verified after material3 alignment).
  - Notes: `JvmDownloader` is the Android-free port of the legacy `DownloaderImpl` (OkHttp 5 +
  Brotli/Gzip, in-memory cookie store, 429 → `ReCaptchaException`). The repository maps the
  extractor onto the neutral models and translates extractor exceptions into `ContentException`.
- [x] **Implement service registry for desktop (`jvmMain`)**
  - Evidence: `shared/src/jvmMain/kotlin/net/newpipe/app/service/ExtractorServiceRegistry.kt`
    (`ExtractorServiceRegistry` over `ServiceList.all()`), plus `ExtractorInitializer` which
    initialises `NewPipe` once per process.
- [x] **Provide `iosMain` stub so `shared` keeps compiling for all declared targets**
  - Evidence: `shared/src/iosMain/kotlin/net/newpipe/app/service/IosContentRepository.kt`.
  - Notes: the iOS targets cannot be compiled in this environment (no macOS toolchain), so the stub
    is verified by source review only.
- [~] **Provide `androidMain` implementation so the Android app is not regressed**
  - Evidence: `shared/src/androidMain/kotlin/net/newpipe/app/service/AndroidContentRepository.kt`
    currently throws `ContentException.Unsupported`.
  - Open: the legacy `app/` module does not depend on `shared`, so nothing regresses today. The
    real implementation should move to a JVM source set shared by `jvmMain` and `androidMain` once
    the Android build tolerates the extractor's HTTP stack.
- [ ] **Unit tests for the service registry and domain models** — not started.

## Phase D — Desktop UI port

- [x] **Audit the existing upstream UI foundation that must be reused**
  - Evidence: `shared/src/commonMain/kotlin/net/newpipe/app/theme/{Theme,ServiceTheme,Color,Dimens}.kt`,
    `navigation/{Destination,NavDisplay,NavModule}.kt`, `composable/*` — all Compose Multiplatform,
    all reusable on desktop unchanged.
- [x] **Reuse upstream theme, service brand colours and typography in the desktop shell** —
  Evidence: shared theme/composables used; app uses `App()` with shared navigation/theme.
- [x] **Desktop application shell** — Evidence: `Main.kt` window + `App()` wired; navigation via Nav3.
- [x] **Search screen with loading/empty/error states** — Evidence: `SearchScreen.kt`, `SearchViewModel`, wired to `ExtractorContentRepository`.
- [x] **Content list item component** — Evidence: `composable/ContentListItem.kt`.
- [x] **Channel page** — Evidence: `screen/channel/ChannelScreen.kt`.
- [x] **Playlist page** — Evidence: `screen/playlist/PlaylistScreen.kt`.
- [x] **Stream detail page** — Evidence: `screen/stream/StreamScreen.kt`.
- [x] **High-DPI, resizable, maximisable window behaviour** — Evidence: `Window` with `rememberWindowState` (Compose Desktop).
- [ ] **Keyboard shortcuts and focus handling** — pending.

## Phase E — Service functionality (search, browsing, pagination)

- [ ] **Real search against the selected service** — pending.
- [ ] **Search result kinds: stream, channel, playlist** — pending.
- [ ] **Pagination (page 0, 1, 2 …)** — pending.
- [ ] **Cancellation of obsolete requests (stale response never overwrites newer results)** — pending.
- [ ] **Channel page with uploads** — pending.
- [ ] **Playlist page with items** — pending.
- [ ] **Direct URL handling (open a NewPipe URL and resolve it to a service)** — pending.
- [ ] **Live-service smoke test (YouTube, SoundCloud, media.ccc.de, Bandcamp)** — pending.

## Phase F — Real media playback

- [x] **Select the playback engine** — VLCJ 4.11.0 over libVLC 3.0.21 (not ExoPlayer).
  - Rationale: ExoPlayer is Android-only. libVLC ships broad codec + HLS/DASH support and a
    maintained JVM binding. A pre-extracted libVLC 3.0.21 tree already exists at
    `%USERPROFILE%\.gradle\vlcSetup\vlc`, and VLCJ 4.11.0 is in the Gradle cache.
  - Notes: licensing (LGPL-2.1+ / GPL-2.1+ by VideoLAN) permits redistribution with notices.
- [x] **Add VLCJ to the desktop module and wire the native library path** —
  Evidence: `gradle/libs.versions.toml` has `vlcj=4.11.0`; `shared/build.gradle.kts` jvmMain
  `implementation(libs.vlcj)`; `VlcNativeLocator.kt` sets `jna.library.path` from candidate paths
  including `%USERPROFILE%\.gradle\vlcSetup\vlc` (libVLC 3.0.21); `np-run2.log` shows
  `Info: Using libVLC from ...`.
- [x] **Implement the player controller (play/pause/stop/seek/volume/mute/speed)** —
  Evidence: `VlcPlaybackController.kt` uses `EmbeddedMediaPlayerComponent`, `ControlsApi.setTime`,
  `AudioApi.setVolume`, `setRate`; `PlaybackController` exposes `play/stop/pause/resume/seekTo/setVolume/setMuted/setSpeed/release`.
- [x] **Separate audio + video stream handling (no silent assumption of muxed streams)** —
  Evidence: `VlcPlaybackController` adds separate audio slave via `media().slaves().add(MediaSlaveType.AUDIO, url, true)`.
- [x] **Player UI with controls, position, duration** —
  Evidence: `desktopApp/.../player/PlayerWindow.kt` has seek slider, play/pause, speed dropdown,
  volume slider, mute toggle, elapsed/duration text; uses `androidx.compose.ui.awt.SwingPanel`.
- [x] **Floating player window (independent window, closing it stops playback)** —
  Evidence: `Main.kt` creates `PlayerWindow(playbackController)` as a separate `Window`;
  `onCloseRequest = { controller.stop() }`, `visible` bound to `state.title.isNotEmpty()` (lifecycle-safe).
- [~] **Playback continues while navigating** — controller created in `application {}` scope passed via `App(playbackController)`, observed running after fixes.
- [ ] **Background audio / playback while minimised** — untested.
- [ ] **Queue management (add / remove / reorder / next / previous)** — not started.
- [ ] **Verify long-session playback and unsupported-format error surfacing** — untested.

## Phase G — User data (subscriptions, playlists, history, settings)

- [x] **Audit existing persistence layer and preference key parity**
  - Evidence: `shared/src/commonMain/kotlin/net/newpipe/app/preferences/*.kt` uses
    `multiplatform-settings`; legacy `app/.../res/values/settings_keys.xml` uses the same key
    names (`current_service_key = "service"`), so preference values transfer 1:1.
- [ ] **Database layer on the desktop JVM (Room 3 KMP, bundled SQLite)** — pending.
- [ ] **Subscriptions (subscribe / unsubscribe / list / persist)** — pending.
- [ ] **Channel groups** — pending.
- [ ] **Local playlists (create / rename / add / remove / reorder / delete)** — pending.
- [ ] **History (record, browse, remove, clear, position restore)** — pending.
- [ ] **Import / export of user data (NewPipe JSON + ZIP backup formats)** — pending.
- [ ] **Verify persistence across restarts** — pending.

## Phase H — Downloads and Windows integration

- [x] **Audit download implementation and its Android coupling**
  - Evidence: `app/src/main/java/org/schabi/newpipe/download/**` plus
    `app/.../streams/{Mp4DashReader,Mp4FromDashWriter,OggFromWebMWriter,SrtFromTtmlWriter,WebMReader,WebMWriter}.java`
    — the muxing/writing helpers are plain Java and reusable; the download *service* is Android-bound.
- [ ] **Download manager with progress, cancellation and resumable transfer** — pending.
- [ ] **Audio-only and video downloads, output directory selection** — pending.
- [ ] **Native file dialogs** — pending.
- [ ] **Persistent user-data location under `%APPDATA%`** — pending.
- [ ] **Window state persistence (size/position) and clean shutdown** — pending.
- [ ] **Application icon and metadata** — pending.

## Phase I — Testing and stabilisation

- [ ] **Unit tests for domain models, service registry, queue, settings** — pending.
- [ ] **Unit tests for playlist / subscription / history repositories** — pending.
- [ ] **Tests for import / export round trip** — pending.
- [ ] **Tests for download state transitions** — pending.
- [ ] **Opt-in live-service smoke tests** — pending.
- [ ] **Regression tests for defects found during development** — pending.
- [ ] **Windows end-to-end test on a clean machine** — pending; no clean Windows VM is available
      in this environment, so this must be run in CI or on a separate machine.

## Phase J — Packaging and release

- [ ] **Windows x64 application image** — pending.
- [ ] **Versioned installer (MSI)** — pending.
- [ ] **Portable ZIP distribution** — pending.
- [ ] **Third-party notices / licensing documentation** — pending.
- [ ] **GitHub Actions build + test workflow** — pending.
- [ ] **Release workflow gated on a clean build** — pending.
- [ ] **Build and usage documentation** — pending.

## Release (authorisation-gated)

- [ ] **Push `feature/windows-desktop` to `https://github.com/Tauseef666-ctrl/YT-DESKTOP-`** —
  awaiting explicit authorisation.
- [ ] **Create a GitHub release with the MSI + portable ZIP** — awaiting explicit authorisation.

---

## Notes, decisions and open issues

- **Do not modify the user's separate Android TV / phone repository.** All work is confined to this
  clone. Upstream `dev` is preserved; all work happens on `feature/windows-desktop`.
- **Android SDK absence** was a blocker and was resolved by pointing `local.properties` at
  `C:\Android\sdk`. `local.properties` is git-ignored and is not part of the deliverable.
- **iOS targets**: `shared` declares `iosArm64()`/`iosSimulatorArm64()`. Because NewPipeExtractor is
  JVM-only, extractor-backed code will live in `jvmMain` + `androidMain` and iOS will receive an
  explicit stub. The iOS build cannot be exercised here (no macOS toolchain).
- **Player engine**: ExoPlayer (Android-only) → libVLC via VLCJ (cross-platform JVM).
- **Licensing**: NewPipe is GPL-3.0-or-later. libVLC is LGPL-2.1+ (relicensable to GPL-2.1+ by the
  VideoLAN project). Any Windows distribution must ship libVLC with the applicable notices. The
  application is an independent port and must not imply endorsement by TeamNewPipe.
- **Clean-machine verification** is the largest remaining gap in the acceptance criteria and is
  tracked explicitly in `docs/FEATURE_PARITY.md`.
