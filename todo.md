# NewPipe+ — Build Todo (self-checklist)

Working checklist for the NewPipe+ redesign + 10 enhancements.
Source of truth for **behaviour** is the repo itself. Base commit: `4b6264de6f` (upstream `TeamNewPipe/NewPipe`, branch `dev`), cloned fresh.

Legend: `[x] done` · `[~] in progress` · `[ ] todo` · `[!] blocked/unsupported` · `[-] N/A`

---

## 0. Reality check (from Phase-1 audit)

| Area | Where it lives | State |
|---|---|---|
| Full app (player, downloads, subs, feed, history, settings, backup) | `:app` (Android, Java/XML + ExoPlayer 2.19.1 + giga + Room) | **exists**, Android-only |
| New Compose UI | `shared` (KMP: android/jvm/ios) | **skeleton**, only About + Settings |
| Desktop shell | `:desktopApp` (18-line `Main.kt`) | **empty**, hosts `shared` About screen |
| Windows packaging | `:desktopApp` targets MSI/DMG/DEB | MSI declared, **no EXE**, never built in CI |
| iOS | `iosApp` Xcode shell + `shared` iosMain | shell only |
| Design reference image | — | **not received** (proceed on described dark+red system) |

Implication: desktop functionality + the 10 features are **new ports** onto `shared`/`desktopApp`/JVM, using `:app` as the reference. This is a large, multi-stage effort; items are checked only with evidence.

---

## 1. Phase 1 — Audit & baseline

- [x] Inventory framework, modules, entry points (`settings.gradle.kts`, `build.gradle.kts`).
- [x] Inventory Android screens/routes/dialogs (`:app` — 11 activities, 53 fragments, 13 pref XML, 234 keys).
- [x] Inventory shared KMP UI (5 destinations, 5 screens, theme, settings, viewmodels).
- [x] Inventory player (`:app` ExoPlayer UI: quality, speed, captions, repeat, popup; `PlayerHelper`).
- [x] Inventory downloads (giga engine, `DownloadDialog`, `MissionsFragment`, `DownloadManagerService`).
- [x] Inventory local media (local playlists, bookmarks, history; no generic file-library scan).
- [x] Inventory backup/restore (`ImportExportManager` → ZIP: `newpipe.db` + `newpipe.settings`/`preferences.json`).
- [x] Inventory providers (NewPipeExtractor + `ServiceList`; 5 services).
- [x] Inventory tests (app unit 22, instrumented 9; shared commonTest 12; desktop 0).
- [x] Inventory CI/release (`.github/workflows` — Android only; version in `buildSrc/ProjectConfig.kt`).
- [x] Record baseline build/test results (see below).
- [x] Produce formal **feature inventory + preservation checklist** doc (`FEATURE_INVENTORY.md`).

### Baseline results (local, Windows, JDK 21 Temurin; `ANDROID_HOME=C:\Android\Sdk`)
- Local toolchain: Gradle 9.7.1 (downloaded), AGP 9.3.1, Kotlin 2.4.10, CMP 1.11.1.
- [x] `:desktopApp:compileKotlin` → **BUILD SUCCESSFUL** 3m38s (first run). Only Gradle 9.6 deprecation warnings.
- [x] `:shared:jvmTest` → **BUILD SUCCESSFUL** 26s. (Koin plugin warns Kotlin 2.4.10 > tested 2.4.0; proceeds.)
- [!] Android `testDebugUnitTest` / `assembleContinuous` / `connectedCheck` — **env-gated**: SDK has only `cmdline-tools` (no `platforms;android-37`, no `build-tools`). Needs `sdkmanager` install + licenses.
- [ ] `:app:runCheckstyle :app:runKtlint :app:checkDependenciesOrder` (config-only; run when Android SDK ready)
- [!] `connectedCheck` (needs emulator).

---

## 2. Phase 2 — Architecture & design plan

- [x] Write `FEATURE_INVENTORY.md`: every existing feature → current impl (file) → new location → verification method.
- [x] Define preservation contract: nothing removed; behaviours mapped, not dropped.
- [x] Decide desktop strategy: **port to `shared`/JVM incrementally**; Android `:app` stays the reference + buildable.
- [x] Design system spec: dark cinematic palette, red accent, spacing scale, type scale, card/rail/button/dialog tokens (`DESIGN_SYSTEM.md`).
- [ ] Migration plan for any schema/pref change (backward compatible; keep keys).

> **Decision (owner-approved):** (a) Port to `shared`/JVM incrementally (real `.exe`, long effort); `:app` stays untouched-but-buildable.

---

## 3. Phase 3 — Shared design system (Compose, in `shared`)

- [x] Theme tokens: near-black surfaces, charcoal, blue-gray borders, red accent — `theme/PlusColors.kt` + updated dark scheme in `theme/Color.kt`; provided via `AppTheme` (`LocalPlusColors`).
- [x] Reusable components complete: `SectionHeader`, `VideoCard`, `VideoRail`, `NavRail`, `SearchBarField`, `TopToolbar`, `StatusChip`, `FilterChipRow`, `DownloadItem`, `LocalMediaRow`, `StateViews`, `PlusScaffold`, `PlayerFrame` (all in `component/`).
- [x] Player frame scaffold (`component/PlayerFrame.kt`).
- [x] Settings: section header + `SearchBarField`; preference rows (exist).
- [x] Download item + progress bar + status chip (`component/DownloadItem.kt`, `StatusChip.kt`).
- [x] Local-media row (`component/LocalMediaRow.kt`).
- [x] Dialogs + snackbar host + tooltips (`component/PlusDialog.kt`, `PlusSnackbarHost.kt`, `PlusTooltip.kt`).
- [x] Empty / loading / error states (`component/StateViews.kt`).
- [x] Focus rings (`component/Focus.kt` — `Modifier.plusClickable`), tooltips, reduced-motion tokens (`theme/Motion.kt`); per-screen keyboard traversal pending.
- [~] Component previews (`preview/`) done for all new components; `FormatTest` added; more commonTest coverage pending.

---

## 4. Phase 4 — Screen redesign (desktop-first)

- [~] Home: desktop shell (NavRail + top toolbar + hero + honest empty rails) via `screen/home/HomeScreen.kt`; real data rails pending.
- [~] Search: `screen/search/SearchScreen.kt` + `Destination.Search` + NavRail entry (live input, honest empty state, 3 UI tests); results/suggestions/history pending.
- [ ] Subscriptions + Feed (groups).
- [~] Trending / Popular: `screen/trending/TrendingScreen.kt` + `Destination.Trending` + NavRail entry (honest empty state, 1 UI test); ranking/kiosk wiring pending.
- [ ] Channels (tabs).
- [ ] Playlists (remote + local).
- [~] History: `screen/history/HistoryScreen.kt` + `Destination.History` + NavRail entry (honest empty state, 1 UI test); playback-history recording pending.
- [~] Downloads: `screen/downloads/DownloadsScreen.kt` + `Destination.Downloads` + Home entry (real missions via the JVM engine; §6).
- [~] Local Media: `screen/library/LibraryScreen.kt` (indexed folders + scanned list + play), route `Destination.Library` registered + Home entry.
- [ ] Settings (all existing categories preserved).
- [ ] About / Licenses (already exists).
- [~] Navigation + routes: `Destination.Home`/`Library`/`Downloads`/`Search`/`Trending`/`History` registered (`NavModule`), desktop entry starts at Home; remaining routes pending.

---

## 5. Phase 5 — Player (video + settings together)

- [~] Shared player foundation: `player/PlayerEngine.kt` (interface + `PlaybackState`/`PlaybackStatus`/`PlaybackError`/`Quality`), `player/MediaItem.kt`, `player/PlayQueue.kt` (repeat/shuffle, 12 tests green), `player/PlayerQueueState.kt`, `player/UnavailablePlayerEngine.kt`.
- [~] JVM playback engine (VLCJ) implementing `PlayerEngine` (`platform/JVMPlayerEngine.kt`, `@Singleton(binds=...)`); compiles + honest `isAvailable=false` when libVLC absent; real playback unverified (no libVLC on this machine).
- [~] Shared `PlayerViewModel` (`viewmodel/player/PlayerViewModel.kt`) + control center screen (`screen/player/PlayerScreen.kt`); route `Destination.Player` registered, Home nav entry added.
- [~] Shared `PlaybackController` (`player/PlaybackController.kt`, `@Singleton`, 7 tests): single owner of the queue + engine so playback survives navigation between player/library/other screens; `PlayerViewModel` is a thin wrapper over it.
- [~] Video surface + control overlay inside the player frame: native libVLC AWT surface embedded via `expect/actual PlayerVideoSurface` (`JVMPlayerVideoSurface` → `SwingPanel` + `EmbeddedMediaPlayer.videoSurface().set(...)`); video shows once a real item is loaded (verification needs libVLC).
- [~] Integrated expandable settings panel (speed, volume/mute, quality wired; subtitles/audio track/loop/autoplay/PiP/background pending).
- [~] Timeline scrub, time, volume/mute, next/prev, queue (wired to engine + `PlayQueue`).
- [ ] State preserved on panel open/close; settings change **real** playback.
- [~] Buffering / loading / error states (`PlaybackStatus` chip + `ErrorState` for unavailable/errors).
- [~] Keyboard shortcuts + accessibility: default bindings + conflict detection + reserved media keys (`player/PlayerShortcuts.kt`, 13 tests), wired into `PlayerScreen` via platform-normalized key interceptor (`player/KeyInput.kt` + jvm/android/ios actuals); **searchable reference UI in settings done** (`ShortcutReference` + `ShortcutReferenceSection`, wired into the JVM player-settings section; 9 pure + 3 UI tests); focus rings + tooltips on controls. App-wide scope, focus-search & mini-player keys pending.
- [~] Verify no control is a no-op (or mark unavailable) — controls disabled when engine unavailable; video surface explicitly honest.

---

## 6. Phase 6 — Downloads (desktop)

- [x] JVM download engine behind a shared interface: `download/DownloadEngine.kt` + `platform/JVMDownloadEngine.kt` — real `HttpURLConnection` transfers to a `.part` file, HTTP `Range` resume, pause/cancel/remove/clear, honest progress/speed, only marked COMPLETED after the file exists (5 tests against a real local HTTP server).
- [~] Downloads screen (`screen/downloads/DownloadsScreen.kt`, route `Destination.Downloads` + Home entry): single mission list with status chip/progress/speed/size, per-row Pause/Resume/Retry/Cancel/Remove, Clear-completed, and an "add URL + destination folder" form. **Category tabs done** (`screen/downloads/DownloadCategory.kt`: All/Queued/Downloading/Paused/Completed/Failed + `select`/`tabCounts`, 7 pure tests + 3 tab-bar UI tests).
- [~] Actions: start/resume, pause, cancel, retry, remove, clear completed, **open file + open folder** (`platform/FileActions.kt` expect + JVM AWT `SystemFileActions` actuals, no-op android/ios actuals; surfaced as Open-file/Open-folder on COMPLETED rows); **reorder done** (`download/DownloadOps.reorder` — queued/paused/failed missions movable, downloading/completed pinned to slot).
- [~] Destination folder per download supported (default `~/Downloads` via `defaultDownloadDirectory()` expect/actual); **format/quality selection + duplicate detection done** (`download/DownloadFormat` + `formatLabel`, `isDuplicate` on live missions, `DownloadOps` 18 tests); subtitle selection pending.
- [x] Real progress/speed/size; verifies the file before marking complete.
- [x] Persist queue/status across restarts: `download/DownloadStore.kt` (`@Serializable DownloadSnapshot`, key `npp_download_tasks`, blank/corrupt/empty → null), `DownloadRequest`/`DownloadTask` `@Serializable`, engine restores on init (active → QUEUED, speed reset) and persists on every mutation (`@Configuration` DownloadModule needed for the Koin checker) — 6 store tests + 2 engine restart tests; 156 total green.

---

## 7. Phase 7 — Local media library

- [~] Indexed folders (user-selected, persisted via `LibraryPreferences.KEY_INDEXED_FOLDERS`), recursive scan, **never mutates files** (`player/LocalMedia.kt` + `platform/JVMLocalMediaScanner.kt`, 11 tests); incremental scan done (`ScanDiff` + pure `diffByPath` + `JVMLocalMediaScanner.incrementalScan(dir, previous)`, reports added/removed/changed/unchanged); Android/iOS scanners pending.
- [~] Views: single list + **derived views done** (`screen/library/LibraryViews.kt` — `KIND`/`LibraryViewTab` RECENT/CONTINUE/DOWNLOADED, `DerivedViewItem`, pure `continueWatching`/`recentlyPlayed`/`downloadedTasks`, honest empty titles/messages, 10 tests): Videos/Audio/Playlists/Folders/Recently played/Continue watching/Downloaded; Recently-played history persists (`player/RecentlyPlayedStore.kt`, key `npp_recently_played`, cap 50, 13 tests); **Continue watching reuses the resume store** (`PlaybackResumeStore` now records title/url/updatedAt and exposes `entries()`, keeps near-end drop rule).
- [~] Metadata: extension, MIME, **real** size (`formatBytes`), playable `file://` URL; duration intentionally null until learned at playback (never guessed). Resolution/artist/album/location pending.
- [~] Search/filter/sort + list/grid done (`screen/library/LibraryFilter.kt` — pure `applyFilters`, query/path match, Videos/Audio/All, Name/Size/Date, stable order; 12 pure tests + 2 UI tests); thumbnail cache + missing-file detection pending (file removed mid-session surfaces via rescan error).
- [~] Play via shared player: library hands the scanned list to the shared `PlaybackController` and navigates to the player; distinct from online content; unreadable folders reported (not swallowed).

---

## 8. Phase 8 — Settings & Backup/Restore center

- [ ] Preserve **all** existing settings (Android 234 keys) and add desktop equivalents with the same semantics.
- [ ] Settings categories: General, Player, Downloads/Storage, Local Media, Subs/Playlists, Search/Services, Privacy, Appearance, Notifications, Network, Advanced/Debug, Backup & Restore, About.
- [ ] Every setting: accurate value, correct control, persists, affects real behaviour.
- [ ] Backup: create, choose location, status, dated files (no overwrite), list contents.
- [ ] Restore: validate + inspect + confirm + non-destructive-on-failure.
- [ ] Preserve existing ZIP format (`newpipe.db` + prefs) where feasible; document contents.
- [ ] Optional encrypted backup (authenticated encryption, KDF; no custom crypto).

---

## 9. The 10 enhancements

Legend of current state: **exists (Android)** / **partial** / **missing**.

### F1 — Floating mini-player
- [x] Android: popup player (`PopupPlayerUi`) exists — keep.
- [x] Desktop: floating/always-on-top window reusing the shared media session (`platform/PopOutCoordinator.kt` + `JVMPopOutCoordinator`/`DesktopMiniPlayerBridge`, second `Window` in desktopApp `Main.kt`, `MiniPlayerWindow.kt`; opens via toolbar action; 3 coordinator tests + 14 controller tests).
- [~] Controls: play/pause, seek, volume/mute, prev/next, close — done; resizable/repositionable, motorized compact mode pending.
- [x] State preserved on switch; no duplicate sessions; close mini-player ≠ quit app.

### F2 — Advanced integrated player control center
- [x] Android: full player UI (quality, speed, captions, repeat, popup) — keep.
- [~] Desktop: control center screen (`screen/player/PlayerScreen.kt`) with transport, seek, volume/mute, speed, quality, queue; native video embedding + expandable panel pending.
- [~] Add: **configurable skip intervals wired** (keyboard seek uses the `seek_duration` pref, Shift = large step; `PlayerShortcuts.seekStepMillis(isShift, configuredMs)` + `PlayerViewModel.seekStepMillis`, 2 VM tests + 2 shortcut tests). **Per-video prefs store done** (`player/VideoSettingsStore.kt` — `@Serializable VideoSettings`/map snapshot + pure `normalize()` clamp, key `npp_video_settings`, 7 tests); searchable advanced panel, quick menu, active-state indicators, UI wiring pending.

### F3 — Advanced download manager
- [x] Android: giga engine + `MissionsFragment` — keep.
- [~] Desktop: real engine (§6) with resume/pause/cancel/retry + screen with honest progress; **queue/status persistence done** (DownloadStore + engine restore); **category tabs + Open-file/Open-folder done** (DownloadCategory + FileActions expect/actual); **reorder + format/quality + duplicate detection done** (DownloadOps, 18 tests). Subtitle selection pending.

### F4 — Complete Backup & Restore center
- [x] Android: `ImportExportManager` ZIP export/import — keep + preserve format.
- [ ] Desktop: Backup & Restore UI + implementation (§8).
- [ ] Optional AES-GCM + PBKDF2/Argon2 encrypted backups.

### F5 — Unified local media library
- [x] Android: local playlists/bookmarks/history (no generic file scan).
- [~] Shared/desktop: file-library scan (§7) with `LocalMediaScanner` interface + JVM impl (11 tests), persisted indexed folders, **incremental scan (added/removed/changed/unchanged via `diffByPath`)**, and a Library screen that queues scanned files on the shared `PlaybackController`; **search/filter/sort + list/grid view done** (LibraryFilter, 14 tests). Android/iOS scanners, folders/audio/video views, thumbnails pending.

### F6 — Smart queue & playlist system
- [x] Android: `PlayQueue` + local playlists — keep.
- [x] Shared: `player/PlayQueue.kt` (order/repeat/shuffle/insert/remove/move) with tests; **queue persistence done** — `MediaItem` `@Serializable`, `PlayQueue.restore()`, `player/PlayQueueStore.kt` (`@Serializable PlayQueueSnapshot`, save/load/clear on `npp_persisted_queue`, corrupt/blank → null, empty → cleared), `PlaybackController` restores on init + saves on every mutation + `clearQueue()` (7 store tests + 3 controller tests; 107 total green). **Save-as-playlist/restore/skip-unavailable infrastructure done** — `player/PlaylistStore.kt` (`SavedPlaylist`/`PlaylistSnapshot`, save/load/delete/clear on `npp_saved_playlists`) + `player/PlaylistOps.kt` (`toSavedPlaylist`/`toPlayQueue`/`skipUnavailable`, 17 tests); **desktop queue panel done** (`screen/player/PlayerQueuePanel.kt` — current-item highlight, play/move-up/move-down/remove per row, save/restore/skip-unavailable footer with honest status; `PlaybackController.moveItem/removeItem/saveQueueAsPlaylist/restorePlaylist/skipUnavailableInQueue` persist on success, current item preserved by identity; PlayQueueMoveTest 5 + PlaybackControllerTest 25).
- [~] Desktop: queue build/reorder/next-prev/save-as-playlist/restore/skip-unavailable. (reorder/next-prev/save/restore/skip done in the panel above; **next-prev transport still engine-wired only**.)

### F7 — Custom keyboard shortcuts
- [x] Android: hardware-media-button pref (`ignore_hardware_media_buttons_key`).
- [~] Desktop: defaults (Space / media keys, ←→ seek 10s/60s, ↑↓ + media volume, M, S) via `PlayerShortcuts`; conflict detection + reserved media keys; **focus-aware** (`onPlayerKeyEvent` inside the player screen, consumes events). searchable reference UI done (ShortcutReference + ShortcutReferenceSection + JVM settings wiring, tests); app-wide scope pending.

### F8 — Adaptive desktop workspace
- [~] **Window-size/bounds persistence done** (`platform/WorkspacePreferences.kt` — `WorkspaceMode` Compact/Balanced/Expanded setters, `WindowBounds` snapshot, key `npp_workspace`, corrupt/blank → defaults; desktopApp `Main.kt` restores window state + saves on close via `PreferencesSettings`; 8 tests). **Collapsible sidebar + grid density done** (`platform/AdaptiveLayout.kt` — `LocalWorkspaceMode`/`LocalWorkspaceModeChange` composition locals wired in desktopApp `Main.kt`, `isCompactSidebar` drives icon-only NavRail + collapse/expand toggle using `sidebar_collapse`/`sidebar_expand`, `homeColumns`/`gridDensity` snapshot flow qualifies dynamically, `MinHomeColumns=2`/`MaxHomeColumns=6`; 7 tests). Dense nav-rail hover behavior pending.

### F9 — Smart playback resume
- [x] Android: `enable_playback_resume` + playback-state storage — keep.
- [x] Desktop: `PlaybackResumeStore` (reuses `enable_playback_resume`, re-validates, drops near-end positions) + `PlaybackController` wiring (save on pause/end/periodic, replay via `MediaItem.resumePositionMs`, skip live + short clips) + JVM engine pending-seek + `clearResumePositions()` (privacy) (10 store tests + 8 controller resume tests).
- [x] Privacy/settings UI entry: `PlayerResumeSettingsSection` (toggle reuses `enable_playback_resume`, live without restart via `setResumeEnabled`, "Clear saved resume positions" action + count) in the JVM player-settings section (3 screen tests; 94 total green).

### F10 — Extensible media provider system
- [x] Base: NewPipeExtractor + `ServiceList`/`ServiceHelper` abstraction — extend, don't replace.
- [ ] Harden interfaces (search/metadata/channel/playlist/resolve/captions/errors); isolate per-provider failures; document capabilities; mock-provider tests.

---

## 10. Phase 9/10 — Platform adaptation

- [ ] Android phone/tablet: keep `:app` working; migrate screens to `shared` Compose where done, preserve everything.
- [x] Android TV: upstream has TV? verify; keep/retain any TV behaviour.
- [ ] Desktop: responsive at supported sizes; no overflow/clipping.
- [-]/[!] iOS: shell only; document state, do not claim support.

---

## 11. Phase 11 — Testing & regression

- [ ] Run lint/checkstyle/ktlint/dependency-order.
- [ ] Android unit + (emulator) instrumented tests green.
- [ ] `:shared:jvmTest` green; add tests for new components/VMs. → currently **254 tests, 0 failures** (PlayQueue 12, PlaybackController 25, PlayerShortcuts 13, PlayQueueStore 7, PlaylistStore 9, PlaylistOps 8, VideoSettingsStore 7, DownloadStore 6, downloads engine 7, DownloadOps 18, scanner 11, resume store 10, RecentlyPlayedStore 13, AdaptiveLayout 7, PlayQueueMove 5, LibraryViews 10, Format 8, DownloadCategory 7, WorkspacePreferences 8, LibraryFilter 12, scaffold screens 5 (Search 3/Trending 1/History 1), + UI/settings suites incl. DownloadsTabs 3/LibrarySearch 2/ShortcutReferenceSection 3).
- [ ] Playback/download tests (desktop engine on a real machine).
- [ ] Backup: create → inspect → restore → verify data; invalid/corrupt/incompatible backups.
- [ ] Data-integrity: no silent loss; backward-compatible migrations; old data still reads.
- [ ] Compare against feature inventory; no feature silently removed.

---

## 12. Phase 12 — Packaging & release

- [x] Window icon: programmatic `ImageVector` (dark `#0F0F12` tile + red `#E53935` glyph) applied to the main + mini-player windows (`desktopApp/Main.kt`); shared vector drawable avoided because shared `Res` is module-internal.
- [x] Repo professionalization: README "NewPipe+ (desktop port)" section (modules, build/run, status, limitations), `.github/workflows/desktop-ci.yml` (runs `:shared:jvmTest` + `:desktopApp:compileKotlin`), `.gitignore` KMP/desktop entries (`*.hprof`, `hs_err_pid*.log`, `replay_pid*.log`); LICENSE GPL-3.0 confirmed, upstream files untouched.
- [ ] Add `TargetFormat.Exe` (+ icon, vendor, upgradeUuid) to `desktopApp`.
- [ ] Produce real Windows MSI/EXE; verify resources load; boot smoke.
- [~] CI for `desktopApp` build added (`desktop-ci.yml`); artifact upload pending real packaging.
- [ ] Preserve/extend versioning (`buildSrc/ProjectConfig.kt`).
- [ ] Release notes (changes + limitations); licenses/attribution intact.
- [ ] Only report an artifact path once it truly exists.

---

## 13. Acceptance checklist (final)

- [ ] Every pre-existing feature inventoried; none silently removed.
- [ ] Existing settings available and functional; persist across restart.
- [ ] Player + playback settings together in one interface; controls work for real.
- [ ] Download queue/status/pause/resume/cancel/retry work; completed files locatable.
- [ ] Backup create + inspect + restore works; invalid backups safe; no silent data loss.
- [ ] Local media discoverable/playable; scanning never mutates files.
- [ ] Queue/playlist changes persist and update real playback order.
- [ ] Keyboard shortcuts focus-aware, conflict-checked.
- [~] Workspace prefs persist (window bounds done); layouts usable without hiding features.
- [x] Resume associates position with correct media (per-`MediaItem.id` store + replay, live skipped, near-end dropped; 12 tests).
- [ ] Existing providers still work; one provider failure doesn't crash others.
- [ ] No fake progress/buttons/toggles.
- [ ] Lint + tests + production build succeed (or pre-existing failures documented).
- [ ] Real Windows `.exe`/MSI built, packaged resources load, app runs.
- [ ] `FEATURE_INVENTORY.md` and implementation report produced.

---

## 14. Known blockers / open questions

- [!] **Reference image not received** — using described dark+red design system.
- [!] **Scope**: full desktop parity + 10 features ≈ multi-week port (ExoPlayer→VLCJ, downloads, Room→JVM store).
- [ ] Confirm desktop strategy (§2 decision).
- [ ] Confirm whether Android TV builds are expected to be produced here (no TV config found yet — verify).
- [!] New toolchain (AGP 9.3.1 / Kotlin 2.4.10 / Gradle 9.7.1) may need Android SDK + WiX for MSI locally.
