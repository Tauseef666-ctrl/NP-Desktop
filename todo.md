# NewPipe+ — Build Todo (self-checklist)

Working checklist for the NewPipe+ redesign + 10 enhancements.
Source of truth for **behaviour** is the repo itself. Base commit: `4b6264de6f` (upstream `TeamNewPipe/NewPipe`, branch `dev`), cloned fresh.

Legend: `[x] done` · `[~] in progress` · `[ ] todo` · `[!] blocked/unsupported` · `[-] N/A`

---

## 0. Reality check (from Phase-1 audit)

| Area | Where it lives | State |
|---|---|---|
| Full app (player, downloads, subs, feed, history, settings, backup) | `:app` (Android, Java/XML + ExoPlayer 2.19.1 + giga + Room) | **exists**, Android-only |
| New Compose UI | `shared` (KMP: android/jvm) | **skeleton**, only About + Settings |
| Desktop shell | `:desktopApp` (18-line `Main.kt`) | **empty**, hosts `shared` About screen |
| Desktop packaging | `:desktopApp` targets MSI/DEB/EXE | MSI/DEB built + released in CI |
| iOS / macOS | — | **dropped** (out of scope; targets + sources removed) |
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

- [x] Home: desktop shell (NavRail + top toolbar + hero) via `screen/home/HomeScreen.kt`; **real store-fed rails done** (`viewmodel/home/HomeViewModel.kt` + pure `screen/home/HomeRails.kt` — Continue watching/Recently played/Downloads/Playlists, empty rails hidden, honest empty state; the previous fake placeholder rails were removed; 14 new tests).
- [x] Search: `screen/search/SearchScreen.kt` + `Destination.Search` + NavRail entry (live input, honest empty state); **search history + suggestions done** (`search/SearchHistoryStore.kt` persisted `npp_search_history`/cap 15 + pure `pureAdd`, pure `search/SearchSuggestions.kt` `matchSuggestions`, `SearchViewModel`; recent-search list + live suggestions + clear-history in the screen; 19 new tests). Provider-backed results wired (milestone 8) + real tap-to-play (milestone 9).
- [~] Subscriptions + Feed: **local subscriptions manager done** (`subscription/SubscriptionStore.kt` persisted `npp_subscriptions`/cap 500 + pure `pureAdd`, pure `subscription/SubscriptionOps.kt` url-normalize/id, `viewmodel/subscriptions/SubscriptionsViewModel.kt`, `screen/subscriptions/SubscriptionsScreen.kt` add/validate(dupe+url)/remove-with-confirm, route `Destination.Subscriptions` + Home entry, honest "new videos need a provider" note; 34 new tests). Provider-backed Feed (groups) pending.
- [x] Trending / Popular: `screen/trending/TrendingScreen.kt` + `Destination.Trending` + NavRail entry (honest empty state, 1 UI test); ranking/kiosk wired (milestone 8) + real tap-to-play (milestone 9).
- [x] Channels (tabs): `screen/channels/ChannelsScreen.kt` + `Destination.Channels` + `ChannelNavigation` (real first-tab content, loading/empty/error, tap-to-play; milestone 8/9); **per-tab (videos/shorts/live/playlists) selection done** (milestone 12: provider `channelTabs`/`channelTab`, `FilterChipRow` tab strip, per-tab pagination; honest fallback to the default tab when a provider exposes none).
- [x] Playlists: **local playlists screen done** (`screen/playlists/PlaylistsScreen.kt` + `viewmodel/playlists/PlaylistsViewModel.kt`, list/expand/play/delete with confirm; `PlaybackController.deletePlaylist`; route `Destination.Playlists` + Home rail entry); **remote (provider) playlists done** (milestone 10: `PlaylistScreen` + paging).
- [x] History: **wired to real data done** (`viewmodel/history/HistoryViewModel.kt` + pure `screen/history/HistoryGrouping.kt` Today/Yesterday/This week/Earlier, per-item remove via new `RecentlyPlayedStore.remove`/`pureRemove`, clear-all with confirm; honest empty state; 17 new tests).
- [x] Downloads: `screen/downloads/DownloadsScreen.kt` + `Destination.Downloads` + Home entry (real missions via the JVM engine; §6).
- [x] Local Media: `screen/library/LibraryScreen.kt` (indexed folders + scanned list + play), route `Destination.Library` registered + Home entry.
- [~] Settings (all existing categories preserved). **Content** (`screen/settings/content/ContentSettingsScreen.kt` + VM), **Local media** (`screen/settings/localmedia/LocalMediaSettingsScreen.kt` + VM: persisted indexed folders shared with the library blob, add/remove, real on-demand rescan reporting the found count and honestly surfacing unreadable folders; 20 tests), **Search & services** (`screen/settings/search/SearchServicesSettingsScreen.kt` + VM: real clear-search-history + connected-providers overview; 10 tests) and **Advanced** (`screen/settings/advanced/AdvancedSettingsScreen.kt` + VM: real persisted Kermit log level applied immediately + honest build variant; 17 tests) categories wired; remaining categories pending.
- [x] About / Licenses (already exists).
- [~] Navigation + routes: `Destination.Home`/`Library`/`Downloads`/`Search`/`Trending`/`History`/`Playlists`/`Subscriptions`/`ContentSettings`/… registered (`NavModule`), desktop entry starts at Home; remaining routes pending.

---

## 5. Phase 5 — Player (video + settings together)

- [x] Shared player foundation: `player/PlayerEngine.kt` (interface + `PlaybackState`/`PlaybackStatus`/`PlaybackError`/`Quality`), `player/MediaItem.kt`, `player/PlayQueue.kt` (repeat/shuffle, 12 tests green), `player/PlayerQueueState.kt`, `player/UnavailablePlayerEngine.kt`.
- [~] JVM playback engine (VLCJ) implementing `PlayerEngine` (`platform/JVMPlayerEngine.kt`, `@Singleton(binds=...)`); compiles + honest `isAvailable=false` when libVLC absent; real playback unverified (no libVLC on this machine).
- [x] Shared `PlayerViewModel` (`viewmodel/player/PlayerViewModel.kt`) + control center screen (`screen/player/PlayerScreen.kt`); route `Destination.Player` registered, Home nav entry added.
- [x] Shared `PlaybackController` (`player/PlaybackController.kt`, `@Singleton`, 7 tests): single owner of the queue + engine so playback survives navigation between player/library/other screens; `PlayerViewModel` is a thin wrapper over it.
- [~] Video surface + control overlay inside the player frame: native libVLC AWT surface embedded via `expect/actual PlayerVideoSurface` (`JVMPlayerVideoSurface` → `SwingPanel` + `EmbeddedMediaPlayer.videoSurface().set(...)`); video shows once a real item is loaded (verification needs libVLC).
- [~] Integrated expandable settings panel (speed, volume/mute, quality wired; subtitles/audio track/loop/autoplay wired; **PiP/background done** — the `minimize_on_exit` preference now really drives window behaviour: `player/BackgroundPlayback.kt` pure `closeActionFor` + `player/BackgroundPlaybackController.kt` `onCloseRequested()` returns whether the app keeps running, opens the floating mini player for POPUP, and desktop `Main.kt` minimises instead of exiting for BACKGROUND/POPUP). **Default playback speed** persisted (`PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED`) and really applied to the engine on every newly loaded item (`PlaybackController.prepareResumed`), with a picker in the JVM player-settings section; 11 new tests.
- [x] Timeline scrub, time, volume/mute, next/prev, queue (wired to engine + `PlayQueue`).
- [~] State preserved on panel open/close; settings change **real** playback. → panel expanded/collapsed state
  now hoisted into `PlayerViewModel` (`panelExpanded` StateFlow + `togglePanelExpanded`/`setPanelExpanded`, seeded
  collapsed, survives navigation back to the player screen; `PlayerScreen` wires it through `panelExpanded`/
  `onTogglePanelExpanded`/`onSetPanelExpanded` params so the same state drives recomposition). Speed/volume/quality
  and repeat-mode already reach the engine/controller; subtitles/audio-track remain gated honest-unavailable because
  the `PlayerEngine` interface exposes no subtitle/audio-track API (no fake toggle shown).
- [x] Buffering / loading / error states (`PlaybackStatus` chip + `ErrorState` for unavailable/errors).
- [~] Keyboard shortcuts + accessibility: default bindings + conflict detection + reserved media keys (`player/PlayerShortcuts.kt`, 13 tests), wired into `PlayerScreen` via platform-normalized key interceptor (`player/KeyInput.kt` + jvm/android actuals); **searchable reference UI in settings done** (`ShortcutReference` + `ShortcutReferenceSection`, wired into the JVM player-settings section; 9 pure + 3 UI tests); **app-wide scope done** (`player/AppShortcutScope.kt` — `isMediaKey`/`appActionFor`, `Modifier.plusAppShortcuts` bubble-phase `onKeyEvent`, wired into desktopApp `Main.kt` via `AppShortcutHost`, 7 tests; scope tags + hints in ShortcutsReference section). Focus-search & mini-player keys pending.
- [~] Verify no control is a no-op (or mark unavailable) — controls disabled when engine unavailable; video surface explicitly honest.

---

## 6. Phase 6 — Downloads (desktop)

- [x] JVM download engine behind a shared interface: `download/DownloadEngine.kt` + `platform/JVMDownloadEngine.kt` — real `HttpURLConnection` transfers to a `.part` file, HTTP `Range` resume, pause/cancel/remove/clear, honest progress/speed, only marked COMPLETED after the file exists (5 tests against a real local HTTP server).
- [x] Downloads screen (`screen/downloads/DownloadsScreen.kt`, route `Destination.Downloads` + Home entry): single mission list with status chip/progress/speed/size, per-row Pause/Resume/Retry/Cancel/Remove, Clear-completed, and an "add URL + destination folder" form. **Category tabs done** (`screen/downloads/DownloadCategory.kt`: All/Queued/Downloading/Paused/Completed/Failed + `select`/`tabCounts`, 7 pure tests + 3 tab-bar UI tests).
- [x] Actions: start/resume, pause, cancel, retry, remove, clear completed, **open file + open folder** (`platform/FileActions.kt` expect + JVM AWT `SystemFileActions` actuals, no-op android actuals; surfaced as Open-file/Open-folder on COMPLETED rows); **reorder done** (`download/DownloadOps.reorder` — queued/paused/failed missions movable, downloading/completed pinned to slot).
- [~] Destination folder per download supported (default `~/Downloads` via `defaultDownloadDirectory()` expect/actual); **format/quality selection + duplicate detection done** (`download/DownloadFormat` + `formatLabel`, `isDuplicate` on live missions, `DownloadOps` 18 tests); **subtitle selection done** — `DownloadRequest` gained `subtitleUrl`/`subtitleLanguageTag`; pure `subtitleDestinationPath` (companion file `<name>.<lang>.<ext>` beside the main file) + `subtitleCompanionRequest` (independent `-sub` mission reusing the subtitle document URL) in `DownloadOps`; `DownloadsViewModel.addFromUrl` optionally enqueues + starts the companion; add-download UI shows Subtitle-URL + Language fields; 5 pure + 3 VM + 1 UI tests.
- [x] Real progress/speed/size; verifies the file before marking complete.
- [x] Persist queue/status across restarts: `download/DownloadStore.kt` (`@Serializable DownloadSnapshot`, key `npp_download_tasks`, blank/corrupt/empty → null), `DownloadRequest`/`DownloadTask` `@Serializable`, engine restores on init (active → QUEUED, speed reset) and persists on every mutation (`@Configuration` DownloadModule needed for the Koin checker) — 6 store tests + 2 engine restart tests; 156 total green.

---

## 7. Phase 7 — Local media library

- [~] Indexed folders (user-selected, persisted via `LibraryPreferences.KEY_INDEXED_FOLDERS`), recursive scan, **never mutates files** (`player/LocalMedia.kt` + `platform/JVMLocalMediaScanner.kt`, 11 tests); incremental scan done (`ScanDiff` + pure `diffByPath` + `JVMLocalMediaScanner.incrementalScan(dir, previous)`, reports added/removed/changed/unchanged); Android scanner pending.
- [x] Views: single list + **derived views done** (`screen/library/LibraryViews.kt` — `KIND`/`LibraryViewTab` RECENT/CONTINUE/DOWNLOADED, `DerivedViewItem`, pure `continueWatching`/`recentlyPlayed`/`downloadedTasks`, honest empty titles/messages, 10 tests): **Videos / Audio / Folders / Playlists views done** (`LibraryViews.kt` — `audioTracks`/`videoTracks`/`FolderGroup`+`folderGroups` w/ Root fallback /`playlistTracks`, tabs + empty states + thumbnail-pending + missing-file markers on `LocalMediaRow`, saved-playlist queue wiring; 15 tests); Recently-played history persists (`player/RecentlyPlayedStore.kt`, key `npp_recently_played`, cap 50, 13 tests); **Continue watching reuses the resume store** (`PlaybackResumeStore` now records title/url/updatedAt and exposes `entries()`, keeps near-end drop rule).
- [~] Metadata: extension, MIME, **real** size (`formatBytes`), playable `file://` URL; duration intentionally null until learned at playback (never guessed). Resolution/artist/album/location pending.
- [~] Search/filter/sort + list/grid done (`screen/library/LibraryFilter.kt` — pure `applyFilters`, query/path match, Videos/Audio/All, Name/Size/Date, stable order; 12 pure tests + 2 UI tests); thumbnail cache + missing-file detection pending (file removed mid-session surfaces via rescan error).
- [x] Play via shared player: library hands the scanned list to the shared `PlaybackController` and navigates to the player; distinct from online content; unreadable folders reported (not swallowed).

---

## 8. Phase 8 — Settings & Backup/Restore center

- [~] Preserve **all** existing settings (Android 234 keys) and add desktop equivalents with the same semantics; **Downloads/Storage** (`screen/settings/downloads/DownloadsSettingsScreen.kt` + VM, persisted default folder `npp_default_download_directory` really consumed by `DownloadsScreen`) and **History/Privacy** (`screen/settings/history/HistorySettingsScreen.kt` + VM, `npp_watch_history_enabled` gates `RecentlyPlayedStore.record`, resume toggle + clear history/resume) done; remaining categories pending.
- [~] Settings categories: General, Player, Downloads/Storage, **Local Media (done)**, Subs/Playlists, **Search/Services (done)**, History/Privacy, Appearance, Notifications, Network, **Advanced (desktop done)**/Debug, Backup & Restore, About, Content.
- [ ] Every setting: accurate value, correct control, persists, affects real behaviour.
- [x] Backup: create, choose location, status, dated files (no overwrite), list contents — common `backup/` package (`BackupManifest` @Serializable, pure `BackupOps`, `BackupRepository` seam) + JVM `JVMBackupRepository` (real java.util.zip archive, manifest first, never throws; 12 ops + 5 content + 8 JVM repo round-trip tests) + honest unavailable Android no-op; `BackupRestoreScreen` + VM + `Destination.BackupRestore` registered + Settings entry; `chooseDirectory()` expect/actual (JVM `JFileChooser`, android no-op).
- [x] Restore: validate + inspect + confirm + non-destructive-on-failure — archive inspection dialog (`backup_contents_file_count`), validation before apply, honest failure states; wired into the store/restore pipeline via `BackupContent` + `JVMBackupRepository`.
- [ ] Preserve existing ZIP format (`newpipe.db` + prefs) where feasible; document contents.
- [x] Optional encrypted backup (authenticated encryption, KDF; no custom crypto) — `BackupEnvelope` framing + `JVMBackupCipher` (JCE AES-256-GCM + PBKDF2WithHmacSHA256); optional passphrase in `BackupRestoreScreen`, wrong-passphrase/locked states, `isEncrypted` detection.

---

## 9. The 10 enhancements

Legend of current state: **exists (Android)** / **partial** / **missing**.

### F1 — Floating mini-player
- [x] Android: popup player (`PopupPlayerUi`) exists — keep.
- [x] Desktop: floating/always-on-top window reusing the shared media session (`platform/PopOutCoordinator.kt` + `JVMPopOutCoordinator`/`DesktopMiniPlayerBridge`, second `Window` in desktopApp `Main.kt`, `MiniPlayerWindow.kt`; opens via toolbar action; 3 coordinator tests + 14 controller tests).
- [x] Controls: play/pause, seek, volume/mute, prev/next, close; **resizable + repositionable** window, bounds persisted across sessions (`WorkspaceSnapshot.miniPlayerBounds`), **compact (collapsed) mode** toggle persisted (`miniPlayerCompact`), AWT minimum size guard; 4 `WorkspacePreferences` tests added.
- [x] State preserved on switch; no duplicate sessions; close mini-player ≠ quit app.

### F2 — Advanced integrated player control center
- [x] Android: full player UI (quality, speed, captions, repeat, popup) — keep.
- [~] Desktop: control center screen (`screen/player/PlayerScreen.kt`) with transport, seek, volume/mute, speed, quality, queue; native video embedding pending.
- [~] Add: **configurable skip intervals wired** (keyboard seek uses the `seek_duration` pref, Shift = large step; `PlayerShortcuts.seekStepMillis(isShift, configuredMs)` + `PlayerViewModel.seekStepMillis`, 2 VM tests + 2 shortcut tests). **Per-video prefs store done** (`player/VideoSettingsStore.kt` — `@Serializable VideoSettings`/map snapshot + pure `normalize()` clamp, key `npp_video_settings`, 7 tests); **quick settings + expandable advanced panel done** (`screen/player/PlayerSettingsPanel.kt` — active-state `StatusChip` indicators for speed/quality/loop/autoplay/subtitles/audio-track, real loop/autoplay wires, engine-unavailable gating with honest WARNING chips, `autoplayNextEnabled`/`subtitlesEnabled`/`audioTrack` persisted via VideoSettingsStore, 2 UI tests). Picture-in-picture/background playback done (see the player-panel line above: real `minimize_on_exit` behaviour + mini-player pop-up).

### F3 — Advanced download manager
- [x] Android: giga engine + `MissionsFragment` — keep.
- [~] Desktop: real engine (§6) with resume/pause/cancel/retry + screen with honest progress; **queue/status persistence done** (DownloadStore + engine restore); **category tabs + Open-file/Open-folder done** (DownloadCategory + FileActions expect/actual); **reorder + format/quality + duplicate detection done** (DownloadOps, 18 tests); **subtitle selection done** (optional subtitle URL + language tag downloads a real companion file beside the video via `subtitleCompanionRequest`).

### F4 — Complete Backup & Restore center
- [x] Android: `ImportExportManager` ZIP export/import — keep + preserve format.
- [x] Desktop: Backup & Restore UI + implementation (§8) — create/inspect/restore flow + JVM zip repository, Desktop-only (desktop milestone-4 workstream A).
- [x] Optional AES-GCM + PBKDF2/Argon2 encrypted backups — implemented with AES-256-GCM + PBKDF2WithHmacSHA256 (standard JCE only), optional passphrase, honest wrong-passphrase/locked handling.

### F5 — Unified local media library
- [x] Android: local playlists/bookmarks/history (no generic file scan).
- [~] Shared/desktop: file-library scan (§7) with `LocalMediaScanner` interface + JVM impl (11 tests), persisted indexed folders, **incremental scan (added/removed/changed/unchanged via `diffByPath`)**, and a Library screen that queues scanned files on the shared `PlaybackController`; **search/filter/sort + list/grid view done** (LibraryFilter, 14 tests). **Folders/audio/video/playlist views done** (LibraryViews, 15 tests). Android scanner, thumbnails pending.

### F6 — Smart queue & playlist system
- [x] Android: `PlayQueue` + local playlists — keep.
- [x] Shared: `player/PlayQueue.kt` (order/repeat/shuffle/insert/remove/move) with tests; **queue persistence done** — `MediaItem` `@Serializable`, `PlayQueue.restore()`, `player/PlayQueueStore.kt` (`@Serializable PlayQueueSnapshot`, save/load/clear on `npp_persisted_queue`, corrupt/blank → null, empty → cleared), `PlaybackController` restores on init + saves on every mutation + `clearQueue()` (7 store tests + 3 controller tests; 107 total green). **Save-as-playlist/restore/skip-unavailable infrastructure done** — `player/PlaylistStore.kt` (`SavedPlaylist`/`PlaylistSnapshot`, save/load/delete/clear on `npp_saved_playlists`) + `player/PlaylistOps.kt` (`toSavedPlaylist`/`toPlayQueue`/`skipUnavailable`, 17 tests); **desktop queue panel done** (`screen/player/PlayerQueuePanel.kt` — current-item highlight, play/move-up/move-down/remove per row, save/restore/skip-unavailable footer with honest status; `PlaybackController.moveItem/removeItem/saveQueueAsPlaylist/restorePlaylist/skipUnavailableInQueue` persist on success, current item preserved by identity; PlayQueueMoveTest 5 + PlaybackControllerTest 25).
- [~] Desktop: queue build/reorder/next-prev/save-as-playlist/restore/skip-unavailable. (reorder/next-prev/save/restore/skip done in the panel above; **next-prev transport still engine-wired only**; **saved-playlists screen done** — list/expand/play/delete via `PlaylistsScreen`.)

### F7 — Custom keyboard shortcuts
- [x] Android: hardware-media-button pref (`ignore_hardware_media_buttons_key`).
- [x] Desktop: defaults (Space / media keys, ←→ seek 10s/60s, ↑↓ + media volume, M, S) via `PlayerShortcuts`; conflict detection + reserved media keys; **focus-aware** (`onPlayerKeyEvent` inside the player screen, consumes events). searchable reference UI done (ShortcutReference + ShortcutReferenceSection + JVM settings wiring, tests); **app-wide scope done** (`AppShortcutScope` `plusAppShortcuts` bubble-phase host in desktopApp `Main.kt` + scope tags/hints in reference UI, 7 tests). **Focus-search & mini-player keys done** — `PlayerAction.FOCUS_SEARCH`/`TOGGLE_MINI_PLAYER` with default `Ctrl+F`/`Ctrl+M` bindings (plain F unbound, plain M stays player-only MUTE), both claimed app-wide in `AppShortcutScope` (Ctrl-modified only; the player screen deliberately lets them bubble to the app scope), shortcut-reference labels included, +4 resolver +2 app-scope tests.

### F8 — Adaptive desktop workspace
- [~] **Window-size/bounds persistence done** (`platform/WorkspacePreferences.kt` — `WorkspaceMode` Compact/Balanced/Expanded setters, `WindowBounds` snapshot, key `npp_workspace`, corrupt/blank → defaults; desktopApp `Main.kt` restores window state + saves on close via `PreferencesSettings`; 8 tests). **Collapsible sidebar + grid density done** (`platform/AdaptiveLayout.kt` — `LocalWorkspaceMode`/`LocalWorkspaceModeChange` composition locals wired in desktopApp `Main.kt`, `isCompactSidebar` drives icon-only NavRail + collapse/expand toggle using `sidebar_collapse`/`sidebar_expand`, `homeColumns`/`gridDensity` snapshot flow qualifies dynamically, `MinHomeColumns=2`/`MaxHomeColumns=6`; 7 tests). Dense nav-rail hover behavior pending.

### F9 — Smart playback resume
- [x] Android: `enable_playback_resume` + playback-state storage — keep.
- [x] Desktop: `PlaybackResumeStore` (reuses `enable_playback_resume`, re-validates, drops near-end positions) + `PlaybackController` wiring (save on pause/end/periodic, replay via `MediaItem.resumePositionMs`, skip live + short clips) + JVM engine pending-seek + `clearResumePositions()` (privacy) (10 store tests + 8 controller resume tests).
- [x] Privacy/settings UI entry: `PlayerResumeSettingsSection` (toggle reuses `enable_playback_resume`, live without restart via `setResumeEnabled`, "Clear saved resume positions" action + count) in the JVM player-settings section (3 screen tests; 94 total green).

### F10 — Extensible media provider system
- [x] Base: NewPipeExtractor + `ServiceList`/`ServiceHelper` abstraction — extend, don't replace.
- [x] Real JVM provider (milestone 8): `JVMHttpDownloader` (JDK `HttpClient`, desktop User-Agent, gzip/deflate, 429/recaptcha → `ReCaptchaException`) + `ExtractorMediaProvider` (`@Singleton(binds=[MediaProvider::class])`; id `newpipe-extractor`, name `YouTube`; caps search/trending/channels/playlists; pure NPE `InfoItem`→`ProviderContentItem` mappers; listings paginate — the extractor's next-page URL is surfaced and an incoming `pageToken` is honoured, milestone 11) + `ExtractorProviderRegistrar` (`@Singleton(createdAtStart=true)` registers into `ProviderRegistry`); common seam `provider/ProviderContent.kt`, `provider/ProviderContentRepository.kt` (per-capability provider pick, runs on `Dispatchers.Default`, overridable for tests), `provider/ChannelNavigation.kt`; real Search results + real Trending + new Channel screen (loading/empty/error + card click → channel); 10 network-free mapping/registrar tests.
- [~] Harden interfaces (search/metadata/channel/playlist/resolve/captions/errors); isolate per-provider failures; document capabilities; mock-provider tests. → **foundation done** (`provider/MediaProvider.kt` interface + `ProviderCapabilities`, `provider/ProviderRegistry.kt` @Singleton register/unregister/byId/available, `provider/ProviderModule.kt` @ComponentScan, `ContentSettingsViewModel` + honest `ContentSettingsScreen`; 9 provider-registry tests). Stream resolve (milestone 9), playlist listing + paging (milestone 10), cross-listing pagination for search/trending/channels (milestone 11) channel content tabs (milestone 12) and stream metadata (milestone 13) and captions (milestone 14) now wired, and search is filterable (Videos/Channels/Playlists, milestone 15).
- [x] Play provider content end-to-end (milestone 9): common seam `provider/ResolvedMedia.kt` (`ResolvedMedia(streamUrl, isLive, audioOnly, durationSeconds?)` + `ProviderContentItem.toMediaItem`), `provider/MediaPlayback.kt` (`resolve`/`play`), `provider/ProviderPlayback.kt` (`@Singleton(binds=[MediaPlayback::class])`: resolves via `ProviderContentRepository.resolve` → `PlaybackController.playItem`), `provider/ProviderPlaybackState.kt`, `provider/ProviderPlaybackSession.kt` (StateFlow state, collapses overlapping requests) + shared `component/PlaybackStatusMessage.kt`; `ExtractorMediaProvider.resolve` (`ProviderCapabilities.resolve=true`, `StreamInfo.getInfo(ServiceList.YouTube, url)` → pure `pickResolvedMedia`: live prefers non-blank HLS else highest-height progressive else audio-only, null → `UNSUPPORTED`); Search/Trending/Channels VMs + screens wired (`mediaPlayback`/`play`/`acknowledgePlayback`/`playbackState` + `LaunchedEffect` on `Launched` → `Destination.Player`); 10 resolve + 6 playback + 6 session + 4 repository-resolve tests (plus 4 play tests each added to the search/trending/channels VMs). Live playback (VLCJ opening real googlevideo URLs, required headers/IP-locking) is wired but **unverifiable offline** — documented, not claimed working.
- [x] Provider playlists end-to-end (milestone 10): `MediaProvider.playlist` + `ProviderContentRepository.playlist` (picks a `capabilities.playlists` provider), `provider/PlaylistNavigation.kt` (@Singleton selection, mirrors `ChannelNavigation`), `Destination.Playlist` + `NavModule` route; `ExtractorMediaProvider.playlist` (`getPlaylistExtractor` → `initialPage`/`getPage(Page(token))`, honest next-token via `InfoItemsPage.nextPage.url`, `pureMapPage(page, nextPageToken)`); `viewmodel/playlist/PlaylistViewModel.kt` (Idle/Loading/Success(items, nextPageToken, loadingMore)/Failure, `load`/`retry`/`loadMore` append, guarded jobs) + `screen/playlist/PlaylistScreen.kt` (real list, load-more, tap-to-play → `Destination.Player`); pure `provider/ProviderClickAction.kt` (`PLAY`/`OPEN_CHANNEL`/`OPEN_PLAYLIST`) routes taps in Search/Trending/Channels so a `PLAYLIST` now opens the playlist instead of the uploader channel; 10 VM + 6 repository + 5 click-action + 4 navigation + 3 extractor-mapping tests. ("Play all"/queue-whole-playlist shipped in milestone 16; cross-listing pagination shipped in milestone 11.)
- [x] Cross-listing pagination (milestone 11): `ExtractorMediaProvider.search/trending/channel` now honour an incoming `pageToken` via the shared helpers `resolvePage(token, extractor)` (initial page vs `getPage(Page(token))`) and pure `pureNextPageToken(page)` (`InfoItemsPage.nextPage.url`, null when exhausted/blank) — all four listings (search/trending/channels/playlists) surface and accept a next-page token. Common `component/ProviderLoadMore.kt` renders the shared disabled-while-loading "Load more" affordance. The three `Success` states gained `nextPageToken`/`loadingMore`, and Search/Trending/Channels VMs gained `loadMore()` (append; failure keeps the visible list and only clears `loadingMore`; Search re-checks `activeQuery` "latest wins" after the suspend call) wired into the three screens. 3 extractor + 6 repository-passthrough + 4 VM-loadMore tests per listing (12 VM tests). "Play all" and NPE-only caveats (page tokens are extractor next-page URLs) still apply.
- [x] Channel content tabs (milestone 12): `MediaProvider.channelTabs(url)`/`channelTab(url, tabId, pageToken)` + provider-agnostic `provider/ProviderChannelTab(id, label)`, plus matching `ProviderContentRepository` passthroughs (picked via `capabilities.channels`). `ExtractorMediaProvider` maps `getChannelExtractor(url).tabs` (NPE `ListLinkHandler` list) into tabs and loads one through `getChannelTabExtractor(tab)` with the shared `resolvePage`/`pureNextPageToken` pagination; pure `channelTabLabel(id)` maps `ChannelTabs` ids and URL suffixes to display labels ("Live" for livestreams/streams, "Albums" for albums/releases, honest id fallback for unknown tabs). `viewmodel/channels/ChannelsViewModel.kt` gained `tabs`/`selectedTabId` + `selectTab(tabId)` (cancels in-flight loads, per-tab content and pagination; falls back to `channel(url)` when the provider exposes no tabs) and `screen/channels/ChannelsScreen.kt` renders a `FilterChipRow` strip above the content. 8 extractor-label + 6 repository-passthrough + 5 VM tests.
- [x] Stream metadata (milestone 13): `MediaProvider.streamMetadata(url)` + provider-agnostic `provider/StreamMetadata.kt` (title, uploader name/url/verified, subscriber/view/like counts, upload date, duration, thumbnail, description, category, tags, live flag), plus matching `ProviderContentRepository.streamMetadata` passthrough (picked via `capabilities.resolve` — metadata is read from the same extractor page as playback, so no new capability flag). `ExtractorMediaProvider.streamMetadata` reads `StreamInfo.getInfo(ServiceList.YouTube, url)` and maps it through pure `pureStreamMetadata(...)`, which normalises blank strings and zero counts to `null` (honest omission) and derives `isLive` from `StreamType.LIVE_STREAM`/`AUDIO_LIVE_STREAM`. New `viewmodel/streaminfo/StreamMetadataViewModel.kt` (`StreamMetadataState` Idle/Loading/Success/Unavailable, `load(mediaId)` with latest-wins + same-id no-op, `retry()`); `screen/player/PlayerScreen.kt` gained a "Now playing" `StreamInfoSection` wired to the VM via `LaunchedEffect(queue.current?.id)` (omits absent fields, never prints placeholders). 5 extractor-pure + 3 repository-passthrough + 7 VM tests.
- [x] Captions/subtitles (milestone 14): `MediaProvider.subtitles(url)` + provider-agnostic `provider/SubtitleTrack.kt` (`id`, `languageTag`, `displayName`, `isAutoGenerated`, `format?`, `url?`), plus matching `ProviderContentRepository.subtitles` passthrough (picked via `capabilities.resolve` — tracks are read from the same extractor page as playback/metadata, so no new capability flag). `ExtractorMediaProvider.subtitles` reads `StreamInfo.getSubtitles()` (NPE `List<SubtitlesStream>`) and maps it through pure `pureSubtitleTracks(...)`, which derives a stable track id (`languageTag|manual|format`), falls back to the language tag/display name when one is blank, and drops tracks with neither (honest). New `viewmodel/captions/CaptionsViewModel.kt` (`CaptionsState` Idle/Loading/Success(tracks)/Unavailable, `load(mediaId)` latest-wins + same-id no-op, `retry()`); `screen/player/PlayerScreen.kt` gained a read-only `CaptionsSection` (lists language + "(auto-generated)" + format, honest "No captions available for this stream." empty state) wired to the VM via `LaunchedEffect(queue.current?.id)`. Selecting/rendering a track in the engine is deliberately out of scope (documented pending). 7 extractor-pure + 4 repository-passthrough + 8 VM tests.
- [x] Search content filters (milestone 15): `provider/SearchFilter.kt` (`ALL`/`VIDEOS`/`CHANNELS`/`PLAYLISTS`, each with a stable `id` and a display `label`, plus `fromId`), `MediaProvider.searchFiltered(query, filter, pageToken = null)` (default delegates to `search`, so unfiltered providers are unaffected) + matching `ProviderContentRepository.searchFiltered`. `ExtractorMediaProvider.searchFiltered` passes the filter id to `getSearchExtractor(query, listOf(contentFilterId(filter)), "")` (empty list = all) and reuses the shared `resolvePage`/`pureNextPageToken` pagination; pure `contentFilterId(filter)` maps `ALL`→`""` and the rest to their id. Also hardened live detection: pure `isLiveStreamType(type)` (used by `pureMapItem` and `resolve`) now treats `AUDIO_LIVE_STREAM` as `ProviderContentKind.LIVE` too, not just `LIVE_STREAM`. `viewmodel/search/SearchViewModel.kt` gained `selectedFilter` + `setFilter(filter)` (re-runs the active query; monotonic `requestId` invalidates superseded in-flight searches, including same-query filter changes) and `loadMore()` now passes the selected filter. `screen/search/SearchScreen.kt` renders a `FilterChipRow` strip under the search bar (shown while the query is non-blank). 6 extractor-pure + 4 repository-passthrough + 6 VM tests.
- [x] Playlist "Play all" / queue-whole-playlist (milestone 16): `ProviderPlaybackSession.playAll(items)` iterates the supplied items in order, calling `playback.play` per item and stopping after the first `Success` (publishes `Launched` if any item was playable, `Failure(UNSUPPORTED)` if none were); it is ignored while a request is already in flight and is a no-op for an empty list. `viewmodel/playlist/PlaylistViewModel.kt` gained `playAll()` (delegates to the session with the current `Success` items; no-op otherwise) and `screen/playlist/PlaylistScreen.kt` renders a "Play all" `TextButton` above the item list, wired to the VM through a new `onPlayAll` parameter (same `Launched`→Player navigation as a single tap). 4 session + 2 VM tests.

---

## 10. Phase 9/10 — Platform adaptation

- [ ] Android phone/tablet: keep `:app` working; migrate screens to `shared` Compose where done, preserve everything.
- [x] Android TV: upstream has TV? verify; keep/retain any TV behaviour.
- [ ] Desktop: responsive at supported sizes; no overflow/clipping.
- [-] iOS / macOS: out of scope — dropped from the project (targets, sources, packaging removed).

---

## 11. Phase 11 — Testing & regression

- [ ] Run lint/checkstyle/ktlint/dependency-order.
- [ ] Android unit + (emulator) instrumented tests green.
- [ ] `:shared:jvmTest` green; add tests for new components/VMs. → currently **737 tests, 0 failures** (PlayQueue 12, PlaybackController 31, PlayerShortcuts 13, PlayQueueStore 7, PlaylistStore 9, PlaylistOps 8, VideoSettingsStore 10, DownloadStore 6, downloads engine 7, DownloadOps 23, DownloadsViewModel 3, scanner 11, resume store 10, RecentlyPlayedStore 13, AdaptiveLayout 7, PlayQueueMove 5, LibraryViews 15, Format 8, DownloadCategory 7, WorkspacePreferences 12, LibraryFilter 12, BackupOps 12, BackupContent 5, BackupEnvelope 9, JVMBackupRepository 13, JVMBackupCipher 9, BackupRestoreViewModel 10, AppShortcutScope 7, BackgroundPlayback 10, SearchHistoryStore 10, SearchSuggestions 5, SearchViewModel 25, PlaylistsViewModel 6, DownloadsSettingsViewModel 6, HistorySettingsViewModel 6, scaffold screens 5 (Search 6/Trending 1/History 1), + UI/settings suites incl. DownloadsTabs 4/LibrarySearch 2/PlayerSettingsPanel 2/ShortcutReferenceSection 3, SubscriptionOps 14, SubscriptionStore 14, SubscriptionsViewModel 6, ProviderRegistry 6, ExtractorProvider 10, ContentSettingsViewModel 3, HistoryGrouping 8, HistoryViewModel 6, HomeRails 11, HomeViewModel 5, RecentlyPlayedStore 17, LocalMediaSettingsViewModel 20, AdvancedSettingsViewModel 17, SearchServicesSettingsViewModel 10, DefaultPlaybackSpeed 8, PlayerDefaultsSettingsViewModel 5, TrendingViewModel 15, ChannelsViewModel 20, ExtractorStreamResolve 10, ProviderPlayback 6, ProviderPlaybackSession 10, ProviderContentRepositoryResolve 4, PlaylistViewModel 12, ProviderContentRepositoryPlaylist 6, ProviderClickAction 5, PlaylistNavigation 4, ExtractorPlaylist 3, ExtractorPagination 3, ProviderContentRepositoryPaging 6, ExtractorChannelTabs 8, ProviderContentRepositoryChannelTabs 6, ExtractorStreamMetadata 5, ProviderContentRepositoryStreamMetadata 3, StreamMetadataViewModel 7, ExtractorSubtitle 7, ProviderContentRepositorySubtitles 4, CaptionsViewModel 8, ExtractorSearchFilter 6, ProviderContentRepositorySearchFilter 4).
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
- [x] Real packaging in CI: `.github/workflows/release-desktop.yml` builds Windows `packageReleaseMsi` + Linux `packageReleaseDeb` and publishes a GitHub Release (`gh release`) with both installers; ProGuard non-obfuscating config (`desktopApp/proguard-rules.pro`) fixes jsoup/re2j `-dontwarn`. First **published release**: `v0.29.1-desktop.4` — `net.newpipe.app-0.29.1.msi` (54.9 MB) + `net.newpipe.app_0.29.1_amd64.deb` (49.3 MB). macOS/iOS dropped from the project.
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
- [~] Existing providers still work; one provider failure doesn't crash others (JVM extractor failures mapped to `ProviderFailureReason` + repository last-resort guard, tested; Android live path unverified).
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
