# Repository Audit — NewPipe upstream `dev` branch

**Audit date**: 2026-10-22
**Repository root**: `C:\Users\Tauseef khan\git\web\NewPipe`
**Upstream remote**: `https://github.com/TeamNewPipe/NewPipe.git`
**Branch audited**: `dev`
**HEAD at audit time**: `7934d953a` — *"Use rx3 API of coroutines to simplify logic with room v3 migration"*
**Working tree at audit time**: clean (`git status --porcelain -b` → `## dev...origin/dev`, no modified files)

This document records **verified** findings only. Every claim below was checked against the source
tree, the Gradle files, or the resolved dependency artifacts on this machine.

---

## 1. The central finding

The upstream repository is **not** a legacy Java/XML Android app any more. On the `dev` branch it is
already mid-way through a Compose Multiplatform migration:

| Module | Type | Purpose |
|---|---|---|
| `app/` | Android application (AGP 9.4.1) | The **legacy** View/XML Android app. Still contains the complete business logic: 280 Java + 154 Kotlin files. Consumes `shared`. |
| `shared/` | Kotlin Multiplatform library | The **new** shared code: Compose Multiplatform UI, theme, settings, navigation3, Koin DI, and `expect`/`actual` platform abstractions. Targets: `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`. |
| `desktopApp/` | Kotlin JVM application | Compose Desktop entry point. Contains a single file, `Main.kt`. |

This changes the porting strategy fundamentally. A Windows port does **not** need to be built from
scratch against a foreign codebase — the upstream project already contains the exact seams a
desktop port needs (`shared/src/jvmMain`, `desktopApp/`, and an `expect`/`actual` platform layer with
a JVM implementation already written).

Consequences:

- The extraction engine, service registry, theme, settings model and navigation model can be
  **reused** rather than reimplemented.
- The Android-only pieces (ExoPlayer, `PlayerService`, `MediaSession`, `WorkManager`, `Context`
  bound helpers) are the only things that genuinely need Windows replacements.

### 1.1 Verified module topology

`settings.gradle.kts`:

```kotlin
include(":app")        // androidApp
include(":desktopApp")
include("shared")
```

`app/build.gradle.kts:223` → `implementation(projects.shared)`
`desktopApp/build.gradle.kts:16` → `implementation(projects.shared)`

So the legacy Android app and the desktop app already share a common module. That is the anchor for
this port.

---

## 2. Module inventory

### 2.1 `shared/` — the shared KMP module

`shared/build.gradle.kts` (159 lines). Plugins: `android.library` (AGP's
`com.android.kotlin.multiplatform.library`), `kotlin.multiplatform`, `kotlin.compose`,
`compose.multiplatform`, `koin`, `kotlinx.serialization`, `about.libraries`.

Dependencies in `commonMain` (verbatim):

```
compose.runtime, compose.foundation, compose.material3, compose.ui, compose.resources,
compose.preview, lifecycle.viewmodel, navigation3.ui (api), lifecycle.navigation3,
kotlinx.serialization.json, koin.compose.navigation3, koin.compose.viewmodel,
koin.annotations, multiplatform-settings, kermit
```

**Critically: there is no NewPipeExtractor, no Room, no OkHttp and no player dependency in
`shared/`.** The extraction engine has not yet been moved into the shared module. This is the single
biggest gap between the upstream state and a functional desktop app.

Source-set layout (100 Kotlin files):

| Source set | Files | Content |
|---|---|---|
| `commonMain` | 68 | `App.kt`, `theme/` (Theme, ServiceTheme, Color, Dimens), `navigation/` (Destination, NavDisplay, NavModule), `di/` (KoinApp, PlatformModule, SerializationModule, settings/SettingsModule), `model/`, `preferences/`, `platform/` (7 `expect` interfaces), `screen/` (about + settings), `viewmodel/`, `composable/`, `preview/` |
| `androidMain` | 20 | Real implementations of the 7 platform interfaces + Android-only `ExoPlayerPreferences` and `PlayerSettingsViewModel` |
| `iosMain` | 11 | Real implementations of the platform interfaces for iOS |
| `jvmMain` | 10 | **Real implementations of the platform interfaces for the desktop JVM** — already present and compiling |
| `commonTest` | 13 | Compose UI tests for the settings/about screens |
| `androidDeviceTest` | 1 | `ExoPlayerSettingsSectionsTest` |

The `jvmMain` implementations already cover `AppearanceActions`, `BuildInfo`, `DebugActions`,
`DownloadStorageActions`, `PlayerSettingsSections`, `ResourceHandler` and `ShareHandler` — i.e. the
upstream authors already intended a desktop target and built the platform seam for it.

### 2.2 `desktopApp/` — the desktop entry point

Currently **one file**, `desktopApp/src/main/kotlin/net/newpipe/app/Main.kt`:

```kotlin
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "NewPipe") {
        App(onCloseRequest = ::exitApplication)
    }
}
```

`desktopApp/build.gradle.kts` already declares `mainClass = "net.newpipe.app.MainKt"` and
`targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)` — **MSI is already in the
build**, it was simply never exercised.

### 2.3 `app/` — the legacy Android app

Top-level packages under `app/src/main/java/org/schabi/newpipe/`:

```
database/  download/  error/  fragments/  info_list/  ktx/  local/
player/     settings/  streams/ util/      views/
```

Key sub-systems (all verified by file listing):

- **`database/`** — Room. `AppDatabase.kt`, `Migrations.kt`, `Converters.kt`, `BasicDAO.kt`, plus
  entities/DAOs for `Feed`, `FeedGroup`, `FeedGroupSubscription`, `FeedLastUpdated`, `SearchHistory`,
  `StreamHistory`, `Playlist`, `PlaylistRemote`, `PlaylistStream`, `Stream`, `StreamState`,
  `Subscription`.
- **`player/`** — ExoPlayer-based. `Player.java`, `PlayerService.java`, `player/playback/`,
  `player/resolver/{PlaybackResolver,VideoPlaybackResolver,AudioPlaybackResolver}`,
  `player/playqueue/{PlayQueue,PlayQueueItem,PlayQueueAdapter,AbstractInfoPlayQueue,…}`,
  `player/mediabrowser/`, `player/mediasession/`, `player/notification/`, `player/ui/`.
- **`streams/`** — `DataReader`, `Mp4DashReader`, `Mp4FromDashWriter`, `OggFromWebMWriter`,
  `SrtFromTtmlWriter`, `WebMReader`, `WebMWriter`. **These are plain Java muxing/writing helpers
  and are directly reusable on the JVM.**
- **`util/`** — `ServiceHelper.kt`, `ExtractorHelper.java`, `PeertubeHelper.kt`, `FilenameUtils.kt`,
  `KioskTranslator.kt`, `ListHelper.java`, `Localization.java`, `ImageStrategy`, `UrlFinder`,
  `ZipHelper.java`, …
- **`download/`** — `DownloadActivity`, `DownloadDialog`, `LoadingDialog` (the heavy lifting lives in
  the `streams/` writers plus a background downloader).
- **`local/`** — subscription import/export workers, feed groups.
- **`settings/`** — `ImportExportManager`, tabs JSON.

### 2.4 Tests present in the legacy module

`app/src/test/` contains 20 unit-test classes including `PlayQueueTest`, `PlayQueueItemTest`,
`ImportExportManagerTest`, `ImportAllCombinationsTest`, `TabsJsonHelperTest`, `UrlFinderTest`,
`TimestampExtractorTest`, `ImageStrategyTest`, `LocalizationTest`, `QuadraticSliderStrategyTest`,
`ListHelperTest`, `SrtFromTtmlWriterTest`, `ExportPlaylistTest`, `SubscriptionImportWorkerTest`,
`FeedGroupIconTest`, `PlaylistLocalItemTest`.

`app/src/androidTest/` holds the Room migration tests (the recent commits
*"Migrate database migration tests to use SQLiteConnection APIs"* and
*"Migrate tests to use new room v3.x APIs"* target these).

`app/src/test/resources/settings/` contains **binary fixture databases and ZIP backups**
(`newpipe.db`, `db_ser_json.zip`, `db_noser_json.zip`, `db_vulnser_json.zip`, …) — valuable
regression fixtures for the desktop import/export work.

---

## 3. Dependencies and their Windows compatibility

From `gradle/libs.versions.toml` (verified):

| Dependency | Version | Windows (JVM) compatibility | Verdict |
|---|---|---|---|
| `com.github.TeamNewPipe:NewPipeExtractor` | `2f7d9da4ce82…` (JitPack) | Plain Java JAR, no Android APIs in the classes we need | **Reuse as-is** |
| Kotlin | 2.4.21 | — | Fine |
| Compose Multiplatform | 1.12.2 | Desktop artifacts present (`org.jetbrains.compose.desktop`) | **Reuse** |
| Navigation3 | 1.1.2 | Multiplatform | **Reuse** |
| Koin | 4.2.2 | Multiplatform | **Reuse** |
| `com.russhwolf:multiplatform-settings` | 1.3.0 | Multiplatform | **Reuse** (desktop: `PreferencesSettings`) |
| Kermit | 2.2.0 | Multiplatform | **Reuse** |
| Room 3 (`androidx.room3`) | 3.0.3 | Room 3 supports KMP (JVM + native + Android) with `androidx.sqlite:sqlite-bundled` | **Reuse** — preferred over a custom DB |
| OkHttp | 5.5.0 | JVM | Reuse for downloads |
| ExoPlayer | 2.19.1 | **Android only** | **Must be replaced** |
| `androidx.room3:room3-compiler` (KSP) | 3.0.3 | — | Use with KSP in `shared` |
| `uk.co.caprica:vlcj` | 4.11.0 (present in Gradle cache) | JVM + libVLC native | **Use as the desktop media engine** |
| `ir.mahozad.vlc-setup` Gradle plugin | 0.1.0 (present in cache) | Downloads/extracts libVLC | **Use to provision libVLC** |

### 3.1 Extractor service inventory (verified from the resolved JAR)

`org/schabi/newpipe/extractor/ServiceList.class` present, with service implementations:

| id | Service | Class |
|---|---|---|
| 0 | YouTube | `org.schabi.newpipe.extractor.services.youtube.YoutubeService` |
| 1 | SoundCloud | `…services.soundcloud.SoundcloudService` |
| 2 | media.ccc.de | `…services.media_ccc.MediaCCCService` |
| 3 | PeerTube | `…services.peertube.PeertubeService` |
| 4 | Bandcamp | `…services.bandcamp.BandcampService` |

This exactly matches the `net.newpipe.app.theme.Service` enum already in `shared/commonMain`
(`serviceId` 0–4, same names, same brand colour schemes). The upstream theme is already
service-aware, so no visual-identity work is needed for the branding.

### 3.2 Android-only code, by subsystem

| Subsystem | Android dependency | Windows replacement |
|---|---|---|
| Playback | `com.google.android.exoplayer` | libVLC via VLCJ (`uk.co.caprica:vlcj`) |
| Playback lifecycle | `PlayerService` (bound service) | Application-scoped controller in `desktopApp`; a floating window for the mini player |
| Media session / OS keys | `androidx.media` `MediaSession` | Windows SMTC via JNA, or omit and document (Phase F/F6) |
| Notifications | `androidx.core` `NotificationCompat` | Windows toast via JNA, or omit and document |
| Background refresh | `androidx.work` `WorkManager` | Scheduled JVM task on app start + manual refresh |
| HTTP | `DownloaderImpl` binds `Context` + `PreferenceManager` | Android-free OkHttp `Downloader` with a JVM cookie jar |
| File picking | `NoNonsense-FilePicker` (Android) | `java.awt.FileDialog` / JFileChooser |
| Preferences | `androidx.preference` | `multiplatform-settings` (already used by `shared`) |
| Database | Room on Android | Room 3 KMP + bundled SQLite (same schema) |
| Share | `Intent.ACTION_SEND` | Desktop `Desktop.getDesktop().browse` / clipboard |
| Markdown | `noties:markwon` (Android) | Reuse `shared`'s Compose-based description rendering if present, else plain text |

### 3.3 Verified toolchain on this machine

```
JDK          Temurin 21.0.12.1 (Eclipse Adoptium) at C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot
Gradle       9.8.2 via wrapper (gradle/wrapper/gradle-wrapper.properties)
Android SDK  C:\Android\sdk  (platforms/android-37.0, build-tools/36.0.0, platform-tools, cmdline-tools/latest)
Network      repo1.maven.org → HTTP 200, plugins.gradle.org → HTTP 200
libVLC      %USERPROFILE%\.gradle\vlcSetup\vlc  (VLC 3.0.21 extracted: axvlc.dll, libvlc.dll, plugins/, msi/)
VLCJ         uk.co.caprica:vlcj:4.11.0 and vlcj-natives:4.8.3 already in the Gradle cache
```

The presence of a fully extracted libVLC 3.0.21 tree and cached VLCJ artifacts is consistent with a
previous attempt that chose VLCJ; that choice is validated and reused.

---

## 4. Reusable vs. Android-specific — summary

**Directly reusable on Windows (no rewrite):**

- The entire NewPipeExtractor (services, search, stream extraction, pagination, metadata).
- `shared/commonMain`: theme, colour schemes, typography, dimens, navigation model, settings
  screens, about screens, preference key constants, preference accessors.
- `shared/jvmMain`: the seven platform-interface implementations that already exist.
- `app/.../streams/*Reader|*Writer` (DASH/WebM/TTML muxing helpers, plain Java).
- `app/.../util/{FilenameUtils,KioskTranslator,ListHelper,UrlFinder,ZipHelper,…}` where not
  Android-bound.
- The Room schema in `app/schemas/org.schabi.newpipe.database.AppDatabase` as the reference schema
  for the desktop database.

**Must be replaced or adapted:**

- ExoPlayer → libVLC/VLCJ.
- `PlayerService` → application-scoped controller + floating window.
- `DownloaderImpl` → Android-free `Downloader`.
- Android `Context`-bound helpers (`ServiceHelper`, `PermissionHelper`, `Localization`).
- Android intents / share / file picker / notifications.

---

## 5. Technical risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| R1 | NewPipeExtractor is JVM-only while `shared` also targets iOS | Extractor cannot be placed in `commonMain` | Keep extractor-backed code in `jvmMain` + `androidMain`; give `iosMain` an explicit stub. Platform-neutral domain models + repository interfaces stay in `commonMain`. |
| R2 | `shared` uses AGP's `com.android.kotlin.multiplatform.library` | Needs a local Android SDK even for desktop-only builds | SDK is present (`C:\Android\sdk`); `local.properties` wires it. CI installs it. |
| R3 | Room 3 KMP + KSP inside an AGP-KMP module | Configuration complexity, codegen per target | Add Room to `shared` with `room3 { schemaDirectory(...) }`; validate early with a minimal entity before writing DAOs. |
| R4 | libVLC native binaries must ship with the app | App works on dev machine, fails on a clean machine | Use the `vlc-setup` Gradle plugin to provision libVLC into the distribution; assert the native path at startup and fail with a clear message. |
| R5 | Separate video and audio streams (DASH) | Playback silently has no audio | Detect audio-less video streams and either mux with the audio track or fall back to audio-only; surface the state in the UI. |
| R6 | Upstream `dev` is actively rewritten | Desktop work can conflict with upstream changes | Keep all Windows-specific code in `desktopApp/` and `shared/src/jvmMain`; minimise edits to `shared/commonMain`; never modify `app/` unless strictly required. |
| R7 | Koin 4.2.2 compiler plugin warns "Kotlin 2.4.21 is newer than the newest verified version (2.4.20)" | Possible future compile break | Warning only today; watch it, do not suppress. |
| R8 | Extractor may break because a *service* changed upstream | "Nothing works" reports | Distinguish upstream breakage from port breakage in `docs/FEATURE_PARITY.md`; keep live-service smoke tests opt-in. |
| R9 | Gradle 10 incompatibility warnings | Future upgrade breakage | Track `Deprecated Gradle features` output; do not chase it now. |
| R10 | No clean Windows machine available in this environment | Cannot fully verify the acceptance criteria | Package the app with libVLC bundled, add CI on `windows-latest`, and state the gap explicitly. |

---

## 6. Proposed architecture (summary; full rationale in `docs/ARCHITECTURE.md`)

Extend the existing upstream structure rather than introducing a parallel project:

```
shared/commonMain   platform-neutral domain models, repository interfaces, Compose UI
shared/jvmMain      extractor-backed implementations, JVM settings, JVM database, downloads
shared/androidMain  extractor-backed implementations (keeps the Android app working)
shared/iosMain      explicit stubs for extractor-backed features
desktopApp          Windows entry point, window management, VLCJ playback host, file dialogs
app/                legacy Android app — left intact
```

Adapters (not invasive upstream modifications) are used to translate extractor types into
platform-neutral domain models, so `commonMain` never sees an extractor type.

---

## 7. Recommended implementation sequence

1. **Phase B** — green desktop baseline (done: `:desktopApp:compileKotlin` succeeded).
2. **Phase C** — add the extractor to `shared`; define domain models and a content repository
   interface in `commonMain`; implement it for `jvmMain`, `androidMain`, `iosMain`.
3. **Phase D/E** — desktop shell, search, content lists, channel and playlist pages on the real API.
4. **Phase F** — VLCJ playback, stream selection, controls, queue, floating window.
5. **Phase G** — Room 3 KMP database, subscriptions, playlists, history, import/export.
6. **Phase H** — downloads, native dialogs, `%APPDATA%` paths, window-state persistence.
7. **Phase I** — unit tests, opt-in live smoke tests, regression tests.
8. **Phase J** — MSI + portable ZIP, `windows-build.yml` CI, documentation, release.

---

## 8. Audit commands used (reproducible)

```powershell
git rev-parse --show-toplevel
git status --porcelain=v1 -b
git log --oneline -5
git remote -v
java -version
.\gradlew.bat --version
Get-ChildItem shared/src -Recurse -File -Filter *.kt | ...
Get-ChildItem app/src/main/java/org/schabi/newpipe -Directory
Get-Content gradle/libs.versions.toml
Get-Content settings.gradle.kts
```

Extractor service list was read directly out of the resolved JAR
(`org/schabi/newpipe/extractor/ServiceList.class` and `…/services/*/…Service.class`).
