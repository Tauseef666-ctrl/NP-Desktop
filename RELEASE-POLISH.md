# Release polish TODO — batch 10 (kids playback + popup return)

The app for v0.25.5 is fully working, tested end to end, and BUILT. Both
remaining user-verified bugs are FIXED and COMMITTED (`3528a2c76`, pushed to
`ytd`).

## Fixes in commit 3528a2c76 ("Kids-video playback fallback + popup close ...")
1. **Kids/cartoon videos: The stream could not be loaded — UNPLAYABLE "This
   video is not available"** — ROOT CAUSE: the extraction engine's default
   VISIONOS client is refused streaming data for "made for kids" content.
   A live client matrix (9 probe iterations) proved the ONLY usable route is
   the **YouTube iOS player client** (`clientName=IOS, version 21.05.2, id 5,
   osName iOS, osVersion 18.6.2.23G80` + matching `User-Agent`,
   `X-Youtube-Client-Name/Version`, `X-Apple-Product-Model`,
   `X-ios-bundle-identifier`, `X-Goog-Api-Format-Version: 2`). TV embed clients
   answer deprecated/unavailable, TVHTML5 404s, MWEB asks to reload, iOS/
   ANDROID with stale versions fail preconditions, the watch-page HTML
   `ytInitialPlayerResponse` carries NO stream URLs (SABR-era; only
   `serverAbrStreamingUrl`), and ANDROID v21 answers OK but URL-less.
   FIX: `ExtractorContentRepository.extractStreamDetails` now catches the
   exact `Got error UNPLAYABLE: "This video is not available"` on YouTube
   watch URLs and replays the video through the iOS client
   (`kidsPlaybackFallback` → `iosPlayerResponse`), building StreamDetails
   (title/uploader/duration/thumbnail/description, video+audio streams with
   plain URLs, subtitles) directly from the player response.
2. **Popup close must RETURN the video to the main player with playback
   continuing** — the popup window's title-bar X and its in-player close
   button both additionally called `controller.clear()` (stop). Both paths
   now only `PopupPlayer.close()`; the doc comment matches.

## Verification status (own testing, as the release gate)
- End-to-end probe `KidsPlaybackFallbackTest` (network probe, deleted after)
  vs the REAL repository pipeline: kids video `8UBqeWovDvY` extracted with 18
  video + 5 audio streams, real googlevideo URLs, duration 3978 s ✓; regular
  video `dQw4w9WgXcQ` still extracts via the normal path (no regression) ✓;
  tests=1 failures=0, BUILD SUCCESSFUL.
- `:shared:compileKotlinJvm :desktopApp:compileKotlin` — BUILD SUCCESSFUL.
- Probe/harness files removed before commit; jvmTest nanojson reverted;
  jvmMain gained `implementation(libs.newpipe.nanojson)` for the fallback.
- Player responses also verified: visualize microformat/captionTracks parse
  defensively (captions absent on the two test kids videos → empty list).

## Build + environment
- MSI v0.25.5 rebuilt after the commit (302,937,885 bytes,
  `desktopApp\build\compose\binaries\main\msi\NP-Desktop-0.25.5.msi`) and
  copied to `%USERPROFILE%\Downloads\NP-Desktop-0.25.5.msi`.
- App launched from the fresh app-dir build for user testing (popup return,
  kids videos, fullscreen cursor already in aaab4b2aa).

## Release mechanics (NEXT)
- Push `main` to `ytd`; force-retag `v0.25.5` at the final commit:
  `git tag -f v0.25.5 && git push ytd -f refs/tags/v0.25.5`.
- Re-dispatch `build-release-apk.yml` and `release-linux.yml` with
  `-f tag=v0.25.5`.
- Create the GitHub release for the tag (NONE exists yet) and upload
  MSI/EXE + fresh checksum files (`checksums-{windows,linux,apk}-sha256.txt`)
  — HTTP 408s on this network, retry in background.
- Verify all digests before advertising the release publicly.

## Environment rules (unchanged)
- Gradle: ONE run at a time; `--no-configuration-cache`; NEVER kill daemons;
  kill NP-Desktop.exe before `createDistributable`.
- Second AI coordinates via TODO files only; never commits.
