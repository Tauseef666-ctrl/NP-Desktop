# Polish Round TODO

Working checklist for this polish round. Ticks update as items land.

## Carry-over from the player batch (pre-work, must ship with the release)
- [x] Controller robustness: dedupe double-start, auto-retry once on VLC error, log error context, stop stale audio (VlcPlaybackController)
- [x] Controls do not auto-hide while quality/audio/speed menu is open (VideoPlayer)
- [x] Centered replay button after the video finishes (VideoPlayer)
- [x] Fullscreen exit restores the pre-fullscreen window size; title bar always "NP Desktop" (Main.kt)
- [x] Next button plays the next QUEUE video when the queue is set, else first related (StreamScreen)
- [x] Search results load more on scroll (snapshotFlow fix, SearchScreen)
- [x] Related videos load more on scroll (WatchPage)
- [x] Auto-queue on/off toggle next to the related panel (QueueRepository + WatchPage)
- [x] Kids videos show an honest single quality option instead of a broken picker
- [x] Delete temp probe (ProbeAfsana.kt) before commit

## Main list
- [x] 1. Audit UI load pipeline: screen fetches, caching, refetch on navigation

### Audit findings (ExtractorContentRepository.kt + screens)
- Kiosk/search TTL only 2 min (the two most navigated surfaces); stream 5 min; channel/playlist 10 min; LRU max 24.
- FeedScreen (rail kiosks) holds state in `remember`, refetches page 0 with a full spinner on every rail switch.
- SubscriptionsScreen refetches every subscribed channel on every visit, state in plain `remember`.
- Search results are lost when the entry pops; revisiting search always starts blank.
- StreamScreen shows a full-screen spinner on every open of the same video after the 5-min TTL; extraction can block ~2.5 s extra on bot-wall retries.
- Home is the only genuinely cheap path (stack bottom, retained VM).
- No content prefetch anywhere; libVLC warmUp is media-only.
- Fix set chosen for item 2: raise TTLs, session caches for kiosk feed + subscriptions feed + last search, background prefetch of the next queue/related video.
- [x] 2. Add caching/prefetch layer for instant screen + video loads
- [x] 3. Implement download options UI (quality, audio/video, formats) + fix download function
- [x] 4. Apply glassmorphism panels theme app-wide (Windows + TV) — single-point override in Theme.kt: `withGlassSurfaces()` makes surfaceContainer* tiers translucent, so system surfaces (dropdown menus, dialogs, cards, snackbars) render as frosted glass. TV-specific polish follows in item 5.
- [ ] 5. Split/prioritize Windows vs Android TV layout handling
- [ ] 6. TV remote/D-pad fixes + layout scaling (awaiting user's detailed list)
- [ ] 7. Live player scrubber bogus duration fix (from earlier)
- [ ] 8. Rebuild + refresh release after polish round

## Notes
- One Gradle run at a time, `--no-configuration-cache`, never kill daemons.
- Probes must use `--aout=dummy` (background audio incident).
- Release assets must match shipped code: re-tag v0.25.6 after the batch lands.
