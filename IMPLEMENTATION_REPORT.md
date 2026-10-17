# Implementation report

Status of the NewPipe+ port: a work-in-progress **superset** of upstream NewPipe that adds a desktop
(Windows/Linux, JVM + Compose Multiplatform) companion while keeping the Android app buildable.

## Modules

| Module | What it is |
| --- | --- |
| `:app` | Upstream Android NewPipe, preserved and buildable. It now also depends on `:shared` (`app/build.gradle.kts`), so the new UI can be reused on Android. |
| `:shared` | Kotlin Multiplatform module (Android + JVM) holding the new Compose UI, theme, and the player / downloads / library / settings view models plus pure logic. |
| `:desktopApp` | Plain JVM Compose Desktop shell hosting the shared UI, the floating mini-player window, and window/workspace persistence. |

Supported targets are **Android** and **JVM** (Windows/Linux). macOS and iOS were dropped from the
project (the iOS targets, `iosApp/`, and the `.dmg` packaging format were removed).

## Verification

* `:shared:jvmTest` — **752 tests across 97 suites, 0 failures** (aggregated from
  `shared/build/test-results/jvmTest/*.xml`).
* `:desktopApp:compileKotlin` — compiles.
* Desktop packaging and the Android APK are produced by CI, not on the development machine.

## What changed vs upstream

* **Storage seam** — a multiplatform `Settings` abstraction (`SharedPreferencesSettings` on Android,
  `PreferencesSettings(Preferences.userRoot())` on the JVM) and JSON stores for queue, playlists,
  resume, recently played, downloads, subscriptions, search history, and workspace.
* **Design system** — dark cinematic UI with a red `#E53935` accent, documented in
  [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md).
* **Honest UI** — real empty/loading/error states; no fake progress, no dead toggles. Settings
  categories that cannot work on desktop are hidden rather than shown as no-ops.
* **Encrypted backups** — AES-256-GCM with PBKDF2-HMAC-SHA256 key derivation over a framed archive;
  see [`BACKUP_FORMAT.md`](BACKUP_FORMAT.md). Preference/schema changes follow
  [`MIGRATION.md`](MIGRATION.md).
* **Notifications** — a real notification seam (`Notifier`) with a JVM system-tray implementation,
  a persisted `npp_notifications_enabled` toggle, a settings screen with a test notification, and
  download-complete alerts gated on the toggle. Android binds a no-op implementation.
* **Mini-player (F1)** — resizable/repositionable floating window with a compact mode; bounds and mode
  persisted.
* **Nav-rail hover (F8)** — dense navigation rail that peeks expanded on hover; the expansion decision
  is a pure, unit-tested function.
* **Windows EXE packaging** — added alongside MSI/DEB: installer icon, vendor/description/copyright,
  Start-menu and desktop shortcuts, per-user install, and a fixed upgrade UUID.

## Releases

| Release | Trigger tag | Assets |
| --- | --- | --- |
| `v0.29.2` | `v*` | `yt-desktop-0.29.2.exe` (59.6 MB), `.msi` (58.7 MB), `yt-desktop_0.29.2_amd64.deb` (52.4 MB), `NewPipe_v0.29.2_phone_tv.apk` (12.2 MB) |

Workflows: a single `.github/workflows/release.yml` publishes every artifact into one GitHub
Release — the Windows job builds MSI + EXE, the Linux job builds the DEB, and the Android job builds
the APK. The Android APK is built with
`:app:assembleContinuous` — upstream's `continuous` build type, which is `release` initialised but
**debug-signed**, so it installs without a release key. A single APK serves phones and Android TV:
the manifest declares `android.software.leanback`, a TV banner, and a `LEANBACK_LAUNCHER` intent.

## Honest limitations

* **Real playback** requires libVLC. The JVM engine reports `isAvailable = false` when it is absent
  and does not fake success. It is not installed on the current development machine.
* **Not verified on real hardware:** the Windows installer run, Android install on a phone/TV, and
  libVLC playback. These need machines with the SDK/emulator or the actual devices.
* **Blocked on environment (~55 todo items):** Android SDK/emulator checks (`connectedCheck`, lint,
  instrumented tests, on-device local-media scanners) and real-machine smoke tests. These are
  environment blockers, not missing effort.
* Desktop preference storage (`java.util.prefs`) has no transactions; a crash mid-write can leave one
  key unwritten.
* Android (`newpipe.db`) and desktop (JSON stores) data are **not** interchangeable today; see
  [`BACKUP_FORMAT.md`](BACKUP_FORMAT.md).

## Tracking

The authoritative task list is [`todo.md`](todo.md) (enhancements F1–F10 and phases 1–14). The
feature-preservation contract is [`FEATURE_INVENTORY.md`](FEATURE_INVENTORY.md).
