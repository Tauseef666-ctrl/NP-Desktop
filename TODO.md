# NP Desktop — QA & Release TODO

Track of this engineering pass. Build every step: `gradlew :desktopApp:createDistributable`,
launch, verify, and terminate the test app after each check.

## Wave 1 — YouTube web session + error screen — DONE
- [x] `YtSession` + `YtSessionStore` (expect/actual) + `JvmDownloader` cookies/SAPISIDHASH.
- [x] `withBotWallRetry` ladder; `ErrorPanel` on the stream error branch (bot-wall friendly).
- [x] Settings -> Account (paste field, Save session, status, Sign out).

## Wave 2 — fix verification — DONE
- [x] Right-click works everywhere: verified on feed cards + related/history surfaces, hover
      targeting (`SecondaryClickBridge`), glass `VideoActionsMenu`.
- [x] Home Feed default, search pill, mini player legibility.
- [x] All temporary diagnostics removed.

## Wave 3 — queue & watch later — DONE (soak pending)
- [x] `QueueRepository` + auto-advance + `QueuePanel` + MiniPlayer queue button.
- [x] Watch later + Add to queue in the context menu.

## Wave 4 — release preparation
- [x] Playback/lazy-VLC fix verified live; kiosk paging NPE fixed; Trending loads via search
      fallback (`Loaded 20 items`).
- [x] Live playback FIXED and verified on three channels: live streams now play their HLS
      manifest (`StreamViewModel.pickPreferredVideo`) without the separate audio slave
      (`StreamScreen.playStream`). Commit `ab66f211e`.
- [x] Release installers BUILT with all fixes:
      `desktopApp\build\compose\binaries\main-release\msi\NP-Desktop-0.25.5.msi`
      and `...\exe\NP-Desktop-0.25.5.exe` (144 MB each; ProGuard rules in
      `desktopApp/proguard-rules.pro`).
- [x] Copied to `Downloads\` + `checksums-sha256.txt` regenerated:
      msi `a249595d…`, exe `088fc0b9…`.
- [x] Commits pushed to `feature/windows-desktop` -> `Tauseef666-ctrl/NP-Desktop`:
      `8e07e7e33` (waves 1-3), `bc63b5b05` (ProGuard), `ab66f211e` (live playback).
- [x] Repo cleanup: tree clean; `.gitignore` covers build outputs + release artifacts
      (`*.msi`/`*.exe` untracked deliberately).
- [x] Soak evidence: 13 automated play/navigate cycles stable (mem 315-705 MB, GC cycling,
      no crash); keep the app in daily use as the real soak.
- [ ] Optional full 30-min soak on request.
- [ ] v0.25.5 release tag assets on `Tauseef666-ctrl/NP-Desktop`:
  - [ ] Windows: Upload MSI + EXE + checksums -> `gh release upload v0.25.5 --clobber`.
  - [ ] Linux: Deb (+ AppImage if feasible). NOTE: `.deb` cannot be built on Windows by
        jpackage — needs a Linux CI job or WSL; extend the GitHub workflow.
  - [ ] Android: APK + Android TV APK on the same release tag.
- [ ] Final publish decision stays with the user.
