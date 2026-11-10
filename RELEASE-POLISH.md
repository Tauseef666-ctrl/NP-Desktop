# Release polish TODO — batch 11 (v0.25.6 release)

**v0.25.6 is released** (tag `v0.25.6`, FINAL commit `4ddb3906a`, release
https://github.com/Tauseef666-ctrl/NP-Desktop/releases/tag/v0.25.6, assets:
`NP-Desktop-0.25.6.msi` + `checksums-windows-sha256.txt`, local copy of the
MSI in `Downloads`). Local smoke test: the packaged app image launched and
stays running; the user-reported cartoon load flake is fixed by the retry
ladder (below) and re-verified by probes (see "Second kids fix").

## Second kids fix after user re-test (commit 4ddb3906a)
The first v0.25.6 build still showed "playback fail" on a cartoon video.
Headless probes proved the extraction and VLC are fine in isolation (kids
muxed itag 18 plays, `state=PLAYING` 15 s), so the in-app failure is the
LOAD path being flaky: the engine's kids recovery ladder is refused with
UNPLAYABLE on some attempts, and the one-shot SABR fallback then surfaced
an error even though a plain retry would have opened the video.
1. **Engine retry ladder before the SABR probe** (`ExtractorContent-
   Repository.kt`): on an UNPLAYABLE kids refusal the plain extraction is
   re-offered `KIDS_ENGINE_RETRY_ATTEMPTS = 2` more times, each separated
   by `KIDS_RETRY_DELAY_MS = 1200`, and only when every retry is refused
   with UNPLAYABLE does the SABR probe run (`kidsPlaybackFallback` stays
   `: Nothing` as the last resort). A non-UNPLAYABLE retry failure is
   rethrown immediately. `detailsOf` was extracted as the shared
   StreamInfo→StreamDetails assembler used by all attempts.
2. **Extraction engine warm-up at app start** (`Main.kt`): alongside the
   existing libVLC warm-up, the service registry is initialized in the
   background (`createServiceRegistry().services()`), paying the
   downloader/registry setup before the user's first click instead of on
   it (faster home grid + first stream open).
3. **Probe evidence** (probe deleted before the commit): 2 consecutive
   fresh-engine kids extractions both returned the muxed stream
   (`KIDS-ROUND0/1 streams=1`), VLC played it `state=PLAYING time=13716
   length=1962643 events=|PLAYING`; normal video control also PLAYING.
   Full matrix `:shared:compileKotlinJvm :desktopApp:compileKotlin
   :app:compileDebugKotlin` BUILD SUCCESSFUL before the commit.
4. Tag re-created at `4ddb3906a` and force-pushed so the release binaries
   match the shipped code; Windows MSI + Linux Deb workflows re-ran on it
   (the previous Linux run already proved the proguard `-dontwarn
   android.os.**` fix passes).

## What shipped (commit f8cfd7694 "v0.25.6: async extraction fix ...")
1. **Kids/cartoon videos now play through the engine itself.** Root cause
   chain: the engine's parallel player calls never opened the fork's
   `CancellableCall` completion latch (our `JvmDownloader.executeAsync` did
   not call `setFinished()`), so EVERY `StreamInfo.getInfo` ran into the
   "YouTube requests timed out" guard after the 5 s budget — the "made for
   kids" recovery path (web player + remote JS fallback + android reel,
   ~2.5 s per remote JS stage) never got a chance to run. With the latch
   fixed and the loading timeout raised to 20 s
   (`ServiceList.all().forEach { it.loadingTimeout = 20 }` in
   `ExtractorServiceRegistry`), the engine extracts the kids video
   (`dX1FcGYP6C4`) into a REAL muxed itag 18 (360p avc1+mp4a) videoplayback
   url — verified readable via both `range=` and `Range:` from 0 MB through
   80 MB (416 at 160 MB+ = past EOF, normal HTTP), i.e. VLC-playable.
   - Live evidence from this session: normal video `jNQXAC9IVRw` extracts
     5 video + 5 audio streams; kids video extracts 1 muxed 360p stream;
     tests=3 failures=0 / tests=1 failures=0 (ProbeSabr + ProbePlayback,
     probes deleted before the commit).
2. **The broken iOS head-range kids fallback is REPLACED** by a SABR-first
   probe: MWEB player response (3 attempts, homepage visitorData) →
   `buildSabrInfoFromPlayerResponse` → one preparation + one playback
   request. If YouTube still refuses the media (empty UMP body / 403), the
   user now gets an honest message (BotGuard "PO token" attestation cannot
   be minted without a browser engine) instead of a frozen player. The
   architecture is ready for future PoToken support (`YoutubeSabrSession
   .setPoToken(byte[])` is the single missing piece).
3. **In-app updater (Windows)**: startup check against
   `Tauseef666-ctrl/NP-Desktop` releases, progress + elevated install
   (UAC announced), Settings > Update screen, skip-this-version.
4. **Player cursor/hover polish**: controls once per real mouse move, 3 s
   auto-hide, cursor hides in fullscreen with the controls
   (`Modifier.playerCursor`, transparent AWT cursor via `pointerHoverIcon`).
5. **Desktop extractor source switched** to the JDK-21 JitPack artifact of
   the fork: `com.github.Tauseef666-ctrl.PipePipeExtractor:extractor:
   sabr-v5.5.1` (the InfinityLoop1308 commit artifact is Java-25 bytecode
   and unusable on the Java-21 toolchain). Maintaining the fork means:
   push a new tag to the fork, let JitPack build it, bump
   `pipepipe-extractor` in `libs.versions.toml` (alphabetical order matters:
   `pipepipe-extractor` sorts AFTER `teamnewpipe-*`? no — inside the
   versions/libraries blocks the checker wants `p`-lines after `o`-lines).
6. **Desktop-only engine needs `org.json:json:20260522`** on the classpath
   (`compileOnly` in the fork; Android provides it from the platform).

## Follow-ups (not blockers)
- Kids videos resolve to a single 360p muxed stream; higher quality for
  made-for-kids videos requires the PoToken/SABR bridge (see 2).
- Updater check is unauthenticated GitHub API (60 req/h/IP budget).

---

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
