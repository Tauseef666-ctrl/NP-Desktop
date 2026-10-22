# Architecture — NewPipe Windows Desktop

**Status**: Accepted · **Date**: 2026-10-22 · **Applies to branch** `feature/windows-desktop`

---

## 1. Decision summary

The Windows port is built **on top of the upstream Compose Multiplatform migration** that already
exists on NewPipe's `dev` branch. No new language is introduced, no second copy of the extraction
engine is created, and the legacy Android application module is left intact.

```
NewPipe (upstream dev)
├── app/            legacy Android app — unchanged
├── shared/         Kotlin Multiplatform module
│   ├── commonMain  domain models · repository interfaces · Compose UI · theme · settings · navigation
│   ├── androidMain extractor-backed implementations (Android)
│   ├── jvmMain     extractor-backed implementations (Windows desktop) · DB · downloads
│   └── iosMain     explicit stubs for extractor-backed features
└── desktopApp/     Kotlin JVM — Windows entry point, window mgmt, VLCJ host, native dialogs
```

**Media engine**: libVLC 3.0.21 driven by **VLCJ 4.11.0**.

---

## 2. Why not start from the legacy Android app

The legacy `app/` module is 434 source files of View/XML Android code. Porting it would mean
re-implementing the UI, the database access layer, the service layer, the player and the settings
system in a second UI framework. The `dev` branch already contains the destination: a Compose
Multiplatform `shared` module with a `jvm()` target, an existing `desktopApp`, an `expect`/`actual`
platform layer whose JVM implementations are already written, a full Material 3 Expressive theme
with per-service brand colours, and a navigation3-based navigation model.

The legacy module is therefore used as a **reference and donor**, not as the porting base:

| Need | Donor |
|---|---|
| Domain semantics, edge-case rules | `app/src/main/java/org/schabi/newpipe/**` |
| Room schema | `app/schemas/org.schabi.newpipe.database.AppDatabase` |
| Muxing helpers (DASH/WebM/TTML) | `app/.../streams/*Reader.java`, `*Writer.java` |
| Import/export format | `app/.../local/subscription/**`, `app/.../settings/ImportExportManager` |
| Preference keys | `app/src/main/res/values/settings_keys.xml` |

---

## 3. Layer boundaries

### 3.1 Shared application core (`shared/commonMain`)

Pure, platform-neutral Kotlin. Contains **no** extractor types and **no** JVM-only types, because it
is also compiled for iOS.

- **Domain models** — `ServiceInfo`, `SearchResultItem`, `StreamDetails`, `ChannelInfo`,
  `PlaylistInfo`, `MediaStream`, `VideoQuality`.
- **Repository interfaces** — `ContentRepository`, `ServiceRegistry`, `SubscriptionRepository`,
  `PlaylistRepository`, `HistoryRepository`, `DownloadManager`.
- **UI** — Compose Multiplatform screens and components, built on the existing upstream theme
  (`net.newpipe.app.theme`), which already encodes NewPipe's visual identity and per-service brand
  colours.
- **Navigation** — the existing `navigation3` `Destination`/`Navigator` model, extended with the
  new content destinations.
- **Settings** — the existing `multiplatform-settings` based preference layer and preference key
  constants. Keys match the legacy Android keys one-for-one (`service`, theme keys, …), so a user's
  existing preferences map across unchanged.

### 3.2 Windows platform layer (`shared/jvmMain` + `desktopApp`)

`shared/jvmMain` holds the **platform-neutral API implementations** that need JVM libraries:

- `ExtractorServiceRegistry` — resolves services from `org.schabi.newpipe.extractor.ServiceList`.
- `ExtractorContentRepository` — the real search / channel / playlist / stream-details
  implementation, translating extractor types into the neutral domain models.
- `ExtractorDownloader` — an Android-free `Downloader` (OkHttp + Brotli, JVM cookie jar) for the
  extractor, replacing the Android-bound `app/.../DownloaderImpl`.
- Room 3 KMP database (bundled SQLite), download manager, file-system helpers.

`desktopApp` holds the **Windows-only** concerns that must not leak into the shared module:

- `main()` — Compose Desktop `application { }` with the main `Window` and the floating player window.
- `PlayerController` / `VlcPlayer` — VLCJ instance lifecycle, native library provisioning.
- Native file dialogs, `%APPDATA%` path resolution, window-state persistence, OS integration.

### 3.3 Test layer

| Kind | Location | Scope |
|---|---|---|
| Common unit tests | `shared/src/commonTest` | Domain models, repository contracts, queue, settings — run on **every** target |
| JVM unit tests | `shared/src/jvmTest` | Extractor-backed implementations against fake/live services |
| Desktop tests | `desktopApp/src/test` | VLCJ stream selection, queue logic, path handling |
| Live smoke tests | `shared/src/jvmTest` | Opt-in (`-Pnp.liveTests=true`) real network calls |
| UI tests | `shared/src/commonTest` | Compose UI tests using fakes — no network |

Deterministic tests never touch the network. Live-service tests are opt-in so the default build
stays reproducible.

---

## 4. Key decisions and their rationale

### 4.1 The extractor lives in `jvmMain` + `androidMain`, not `commonMain`

`shared` declares `iosArm64()` and `iosSimulatorArm64()`. NewPipeExtractor is a plain Java
library with no iOS variant, so it cannot be a `commonMain` dependency.

Options considered:

| Option | Outcome |
|---|---|
| (a) Extract the extractor to `commonMain` | Breaks the iOS targets — unacceptable |
| (b) Custom source-set hierarchy (`jvmSharedMain` shared by `jvmMain` + `androidMain`) | Works, but adds a non-standard hierarchy to an upstream module and complicates future upstream merges |
| (c) **`expect`/`actual` repository interfaces in `commonMain`, extractor-backed implementations in `jvmMain` + `androidMain`, explicit stub in `iosMain`** | **Chosen.** Keeps upstream's structure, keeps iOS compiling, keeps the extractor out of `commonMain` |

The cost of (c) is that the extractor types are translated into neutral domain models at the
boundary. That translation is done once, in one place per platform, and it is what makes the UI
layer testable with fakes.

### 4.2 libVLC / VLCJ instead of ExoPlayer

ExoPlayer 2.19.1 is Android-only (`com.google.android.exoplayer`). The only realistic desktop
options are:

| Engine | Pros | Cons |
|---|---|---|
| **libVLC via VLCJ** | Broad codec support, HLS/DASH, audio-only streams, subtitle tracks, hot-seek, real media-session semantics, actively maintained JVM binding | Native binaries must be shipped (~40 MB) |
| mpv via JNI | Excellent | No JVM binding, awkward embedding |
| Windows Media Foundation via JNA | Native | Far weaker codec/stream coverage, poor DASH support |
| FFmpeg via JNA | Flexible | FFmpeg licensing (LGPL/GPL) + large native surface, no ready-made JVM binding |

libVLC wins on the criteria that matter for NewPipe: it plays the exact formats the extractor
produces (including separate DASH video and audio tracks), it supports subtitle tracks, and its
licence permits redistribution with attribution.

A pre-extracted **libVLC 3.0.21** tree already exists on this machine at
`%USERPROFILE%\.gradle\vlcSetup\vlc`, and **VLCJ 4.11.0** is in the Gradle cache — this matches
the `ir.mahozad.vlc-setup` Gradle plugin, which provisions libVLC reproducibly for packaging.

### 4.3 Separate audio and video streams

NewPipe's YouTube extraction frequently yields a **video-only** DASH stream and a **separate
audio-only** stream. The desktop player therefore never assumes the selected video stream carries
audio:

- The stream list exposes video streams and audio streams separately.
- If the selected video stream has no audio track, the player attaches the selected audio stream
  as a secondary input (libVLC supports an audio slave input) or plays the audio-only stream on
  its own when the user explicitly asks for audio-only mode.
- The UI states which audio track is in use.

### 4.4 Room 3 KMP for the desktop database

`androidx.room3` 3.0.3 supports Kotlin Multiplatform with `androidx.sqlite:sqlite-bundled`, which
means the **same** database code and schema can serve Windows and Android. The legacy Android app
has already migrated to Room 3 on this branch, so the schema reference is current.

Room is used rather than a hand-rolled JDBC layer because it gives transactions, migrations and
type converters for free, and because it keeps the Windows port's data model compatible with the
upstream project rather than inventing a parallel one.

### 4.5 Settings reuse

The shared module already uses `com.russhwolf:multiplatform-settings`. The desktop implementation
uses `PreferencesSettings` over a properties file under `%APPDATA%\NewPipe`, and the Android
implementation uses `SharedPreferencesSettings`. Because both use the **same keys** as the legacy
Android app, preference values are portable between platforms with no translation table.

### 4.6 Navigation adaptation

Upstream's new UI uses navigation3 with a bottom navigation bar (a phone pattern). On Windows the
bottom bar is replaced by a **navigation rail** on the left of the window — the standard desktop
equivalent — while the navigation *model*, the destination graph and the back-stack behaviour are
unchanged. The visual identity (colours, typography, spacing, service brand colours, icons) is
preserved exactly; only the navigation *affordance* changes, and only because a phone bottom bar
behaves poorly in a resizable desktop window.

---

## 5. Data flow

```
   UI (commonMain)                Domain (commonMain)          Implementation (jvmMain)
┌──────────────────┐          ┌────────────────────┐          ┌──────────────────────────┐
│ SearchScreen     │          │ SearchResultItem   │          │ ExtractorContentRepo     │
│ ContentListScreen│──call──▶ │ ContentRepository  │──impl──▶ │  → NewPipeExtractor      │
│ ChannelScreen    │          │ ServiceRegistry    │          │ ExtractorServiceRegistry │
│ PlayerScreen     │          │ MediaStream        │          │  → ServiceList           │
└──────────────────┘          └────────────────────┘          └──────────────────────────┘
         │                                                              │
         │ ViewModel + StateFlow                                       │ suspend
         ▼                                                              ▼
   Koin (commonMain)                                          libVLC / OkHttp / SQLite
```

All network and disk work happens in `viewModelScope` on `Dispatchers.IO`; the UI layer only ever
sees immutable domain models and `StateFlow` state.

---

## 6. Error handling policy

- Extraction failures are caught at the repository boundary and converted into a typed
  `NewPipeError` (network, rate-limit, re-captcha, unsupported, extractor-specific) carrying the
  original exception as the cause. The UI renders NewPipe's own error surface, never a raw stack
  trace.
- A failed request produces an error state. It never produces placeholder or fabricated results.
- Obsolete search responses are dropped by request id, so a slow response can never overwrite a
  newer result set.
- Playback errors are surfaced through the player state machine and shown in the player UI.

---

## 7. Licensing

| Component | Licence | Obligation |
|---|---|---|
| NewPipe source | GPL-3.0-or-later | Derivative work must be GPL-3.0-or-later; source must be offered |
| NewPipeExtractor | GPL-3.0-or-later | Same |
| libVLC | LGPL-2.1+ (or GPL-2.1+ by VideoLAN) | Ship the licence texts; keep the library dynamically linked |
| VLCJ | LGPL-2.1+ / Apache-2.0 | Retain notices |
| Compose Multiplatform | Apache-2.0 | Notice |
| OkHttp | Apache-2.0 | Notice |
| Room / AndroidX | Apache-2.0 | Notice |

The Windows application is an **independent port**. It must not imply endorsement by TeamNewPipe.
A `THIRD_PARTY_NOTICES.md` and a `LICENSES/` folder accompany every distribution artefact.

---

## 8. Build and packaging

| Artefact | Gradle task | Notes |
|---|---|---|
| Application image | `:desktopApp:createDistributable` | Self-contained, includes the JRE and libVLC |
| MSI installer | `:desktopApp:packageMsi` | `TargetFormat.Msi` is already declared upstream |
| Portable ZIP | `:desktopApp:packageZipDistribution` | No installer required |
| Tests | `:shared:jvmTest`, `:desktopApp:test`, `:shared:testDebugUnitTest` | |
| Live smoke tests | `:shared:jvmTest --tests '*LiveServiceSmokeTest'` with `-Pnp.liveTests=true` | Network required |

CI: `.github/workflows/windows-build.yml` on `windows-latest` — restore, test, package, validate,
upload artefacts. Release publication is gated on the required checks passing and is only wired to
secrets when explicitly configured.

---

## 9. Platform path conventions

| Path | Purpose |
|---|---|
| `%APPDATA%\NewPipe\` | Settings, window state |
| `%APPDATA%\NewPipe\newpipe.db` | Room database |
| `%APPDATA%\NewPipe\downloads\` | Default download directory |
| `%LOCALAPPDATA%\NewPipe\logs\` | Application logs (Kermit) |
| `libVLC` | Provisioned into the distribution image by the `vlc-setup` plugin |
