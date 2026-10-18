# NewPipe+ — Feature Inventory & Preservation Contract

Base: fresh clone of `TeamNewPipe/NewPipe`, branch `dev`, commit `4b6264de6f`.
This document is the **source of truth for behaviour**. No feature listed here may be
removed, disabled, or silently replaced. When a feature moves, its behaviour moves with it.

> Status: Phase-1 deliverable. Every row is grounded in the Phase-1 audit (file paths verified in-tree).

---

## 1. Starting condition (module map)

| Module | Language / stack | What it actually is |
|---|---|---|
| `:app` | Java/Kotlin + XML Views, ExoPlayer 2.19.1, Room, giga download engine, RxJava3 | **The complete NewPipe app.** Android-only. |
| `shared` | Kotlin Multiplatform + Compose Multiplatform (android / jvm) | **New Compose UI skeleton.** Currently About + Settings only. |
| `:desktopApp` | Compose Desktop (JVM) | `Main.kt` hosting `shared`'s `App()`; starts on About. |

Design system lives in `shared/src/commonMain/.../theme`; UI strings/drawables in
`shared/src/commonMain/composeResources`. Version = `buildSrc/ProjectConfig.kt` (`0.29.4`).

**Consequence:** all real features live in `:app`. NewPipe+ desktop functionality
(the whole point) is a **port** of `:app` behaviour onto `shared`/`jvmMain`/`:desktopApp`,
reusing `:app` as the behavioural reference — plus the 10 enhancements.

---

## 2. Feature inventory

Legend — **Where**: current implementation location. **State**: `full` / `partial` / `none` **on desktop**.

### 2.1 Navigation & shell
| Feature | Where (current) | Desktop state | Preservation / verification |
|---|---|---|---|
| Launcher + drawer (Subscriptions, Feed, Bookmarks, Downloads, History, Settings, Donation, About) | `app/.../MainActivity.java:123-130,241` | none | Recreate all destinations; verify each opens a real screen. |
| Tabs (Kiosk, Feed, Subscriptions, Bookmarks) | `MainFragment.java`, `settings/tabs/*` | none | Migrate tab set; keep saved-tabs behaviour. |
| Router (URL/intent → content) | `RouterActivity.java` | none | Optional on desktop (open-from-link). |
| Dialogs: download, playlist, select channel/feed/kiosk | `download/DownloadDialog`, `local/dialog/*`, `settings/Select*Fragment` | none | Recreate as Compose dialogs. |

### 2.2 Playback
| Feature | Where (current) | Desktop state | Preservation / verification |
|---|---|---|---|
| Player engine | ExoPlayer 2.19.1 (`player/Player.java`) | none | JVM engine (VLCJ) behind shared `PlayerEngine`; verify real decode. |
| Main / background / popup player modes | `player/ui/{Main,Background,Popup}PlayerUi` | none | Desktop: main + floating mini-player (§Feature 1). |
| Quality/resolution selection | `VideoPlayerUi` (quality popup `:1307`) | none | Keep; verify stream switches. |
| Playback speed / pitch / skip-silence | `Player.setPlaybackSpeed:957`, `PlaybackParameterDialog` | none | Keep; verify actual rate change. |
| Subtitles / captions (lang, scale, style) | `PlayerHelper` caption API + `VideoPlayerUi` | none | Keep; verify render. |
| Audio track selection | player audio-track wrapper | none | Keep. |
| Repeat / shuffle / auto-queue | `PlayQueueActivity:380,572-580`, `PlayerHelper.isAutoQueueEnabled` | none | Keep. |
| Resume playback | `enable_playback_resume_key` + playback-state DB | none | Keep (Feature 9). |
| Fullscreen / orientation lock | `VideoPlayerUi`, `PlayerHelper.globalScreenOrientationLocked` | none | Desktop: window fullscreen. |
| Picture-in-picture / popup | `PopupPlayerUi` (WindowManager overlay) | none | Desktop: always-on-top mini window. |
| Media session / notification | `player/mediasession`, `player/notification` | none | Desktop: OS media keys/tray (where supported). |
| Gesture controls (brightness/volume) | `PlayerUi` gestures, `PlayerHelper` | none | Desktop: mouse/keyboard equivalents. |
| Seek duration / inexact seek | `seek_duration`, `use_inexact_seek_key` | none | Keep prefs. |
| Playback errors | `ErrorActivity`, player error states | none | Keep error surfacing. |

### 2.3 Downloads
| Feature | Where (current) | Desktop state | Preservation / verification |
|---|---|---|---|
| Resumable engine (threads, retries) | `us/shandian/giga/DownloadMission.java` (threadCount 3) | none | JVM engine (Feature 3); verify resume/retry. |
| Queue + service + notification | `DownloadManager.java`, `DownloadManagerService.java` | none | Desktop queue; verify states. |
| Download dialog (video/audio/subtitle, quality, threads, naming) | `download/DownloadDialog.java` (1127 lines) | none | Keep all options + filename charset rules. |
| Missions list UI | `giga/ui/fragment/MissionsFragment.java` | none | Recreate as Downloads screen. |
| Post-processing muxers | `giga/postprocessing/*` (Mp4/M4a/WebM/Ogg/TtmlConverter) | none | Port where needed; TtmlConverter → SRT. |
| Destination & storage (SAF) | `download_path`, `download_path_audio`, `StoredDirectoryHelper` | none | Desktop: JVM paths; keep preferences. |

### 2.4 Local media & organization
| Feature | Where (current) | Desktop state | Preservation / verification |
|---|---|---|---|
| Local playlists (create/edit/reorder) | `local/playlist/LocalPlaylistFragment.java` + DAOs | none | Keep; Feature 6. |
| Bookmarks | `local/bookmark/BookmarkFragment` | none | Keep. |
| Watch history + stats | `local/history/*`, `HistoryRecordManager.kt` | none | Keep; Feature 9. |
| Search history | `database/history/SearchHistoryDAO` | none | Keep. |
| Local play queue | `PlayQueue` / `PlayQueueActivity` | none | Keep; Feature 6. |
| Local **file** library (generic video/audio scan) | — (not present; only playlists/bookmarks) | none | **New** (Feature 5) — URL/local file indexing. |
| Playlist export/share | `local/playlist/ExportPlaylist.kt` | none | Keep formats (WITH_TITLES/JUST_URLS/YT temp). |
| File picker (SAF-aware) | `util/FilePickerActivityHelper.java` | none | Desktop: JVM file chooser. |

### 2.5 Subscriptions / discovery
| Feature | Where (current) | Desktop state | Preservation / verification |
|---|---|---|---|
| Subscriptions | `local/subscription/SubscriptionFragment.kt` + DAOs | none | Keep; verify data. |
| Channel groups | `database/feed/*`, `FeedGroup*Dialog` | none | Keep. |
| Feed (from groups) | `local/feed/FeedFragment.kt` | none | Keep. |
| Import/export subscriptions | `workers/SubscriptionImport/Export*`, `SubscriptionData.kt` | none | Keep formats (NewPipe JSON, YT CSV, etc.). |
| Search | `list/search/SearchFragment.java` | none | Keep; add local results. |
| Trending / kiosks | `list/kiosk/*` | none | Keep (runtime kiosk list). |
| Channels + tabs | `list/channel/*` | none | Keep. |
| Remote playlists | `list/playlist/PlaylistFragment` | none | Keep. |
| Comments / replies | `list/comments/*` | none | Keep. |
| Related items / next | `list/videos/RelatedItemsFragment` | none | Keep. |

### 2.6 Settings (all preserved)
- 13 preference XMLs: `app/src/main/res/xml/*` (main, video_audio, appearance, download,
  history, content, notifications ×2, player_notification, update, exoplayer,
  backup_restore, debug).
- **234 keys** in `app/src/main/res/values/settings_keys.xml` (appearance, playback, open
  actions, content/language, history/search, feed, notifications, downloads/storage,
  import/export, tabs, ExoPlayer, misc).
- Shared-module equivalents already exist for Appearance (`AppearancePreferences`) and
  Video/Audio + ExoPlayer (`VideoAudioPreferences`, `ExoPlayerPreferences`).
- **Rule:** preserve every key + semantics; add desktop equivalents with the same meaning;
  no schema reset.

### 2.7 Backup & Restore
| Item | Where | Detail |
|---|---|---|
| Export | `settings/export/ImportExportManager.kt` | ZIP = `newpipe.db` + `newpipe.settings` (legacy serialized prefs) |
| Filenames | `settings/export/BackupFileLocator.kt` | `newpipe.db`, `newpipe.settings`, `preferences.json` |
| UI | `settings/BackupRestoreSettingsFragment.java` (`ZIP_MIME_TYPE`) | import/export flow |
| Safe IO | `util/ZipHelper.java` (SAF-aware) | zip load with validation |
- **Preserve format.** Feature 4 adds desktop UI + optional encryption; must not break the
  existing ZIP compatibility.

### 2.8 Services / providers
- 5 services via NewPipeExtractor + `ServiceList`/`ServiceHelper`: YouTube (incl. Kids/Music),
  SoundCloud, media.ccc.de, PeerTube (instance config), Bandcamp.
- Keep the extractor integration; Feature 10 hardens the provider boundary without replacing it.

### 2.9 Tests & build/release
- Android unit: 22 files (`app/src/test`); instrumented: 9 (`app/src/androidTest`).
- Shared commonTest: 12 files; desktopApp: 0.
- CI: `.github/workflows/ci.yml` (`assembleContinuous lintContinuous testDebugUnitTest`,
  emulator `connectedCheck` ×2 APIs), `build-release-apk.yml` (unsigned release APK on `master`).
  **Desktop is never built in CI.**
- Versioning: `buildSrc/ProjectConfig.kt`; tags `vX.Y.Z`; fastlane changelogs by versionCode.

---

## 3. Preservation checklist (checked only with evidence)

- [ ] All navigation destinations exist and open real screens.
- [ ] Video/audio playback works via a real engine (desktop).
- [ ] Every existing player control present and functional (or marked unavailable with reason).
- [ ] Quality / speed / subtitles / audio-track selection work and persist.
- [ ] Repeat / autoplay / queue / next-prev / shuffle work.
- [ ] Every existing setting present, persists, and affects behaviour.
- [ ] Downloads: queue + states + pause/resume/cancel/retry + open file/folder + delete.
- [ ] Destination + storage preferences preserved.
- [ ] Local library (playlists/bookmarks/history) data preserved; new file-scan added.
- [ ] Subscriptions / groups / feed / import-export preserved.
- [ ] Search / trending / channels / playlists / comments preserved.
- [ ] Backup create/inspect/restore preserved (ZIP format intact).
- [ ] Provider integrations preserved; isolated failures.
- [ ] No user data reset; backward-compatible migrations only.
- [ ] Existing release packaging conventions preserved; new desktop artifacts added.

---

## 4. Desktop gap analysis (what is net-new)

Everything in `:app` must be ported to `shared`/`jvmMain`/`:desktopApp` **except** the
parts already present in `shared` (About, Settings Home/Appearance/Video-Audio/Player).
Net-new subsystems: JVM player engine (VLCJ), JVM download engine, JVM persistence for
DB-backed data (subscriptions/playlists/history), local file-library scan, desktop
navigation shell, desktop packaging (EXE/MSI), AND the 10 enhancements.

This gap is why the effort is staged and why `:app` stays untouched-but-buildable as the reference.
