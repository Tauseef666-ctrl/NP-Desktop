# NewPipe-1 Desktop — Resume Context (read this first each session)

## Env
- Windows, PowerShell only. Working dir: `C:\Users\Tauseef khan\git\web\NewPipe-1`
- `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"`
- Compile: `.\gradlew.bat :shared:compileKotlinJvm -PskipFormatKtlint --console=plain` (ktlint/checkstyle only in `:app`)
- **User interrupts long builds — ALWAYS run builds in background**: `Start-Process cmd.exe /c "set JAVA_HOME=...&& .\gradlew.bat ... > compilelog.txt"` then poll file; foreground commands must stay short.
- Screenshots: System.Drawing `CopyFromScreen` → `C:\Users\TAUSEE~1\AppData\Local\Temp\opencode\<name>.png`; analyze via pixel brightness/color sampling (cannot view images). Screen 1536x864, window at -7,-7.
- App launch: kill `Get-Process | Where-Object { $_.MainWindowTitle -like "*YT Desktop*" }`; then `Start-Process cmd.exe /c "set JAVA_HOME=...&& .\gradlew.bat :desktopApp:run -PskipFormatKtlint --console=plain > runlogN.txt"` minimized; poll `MainWindowTitle -like "*YT Desktop*"` ≤180s.

## Git
- Remote `fork` = https://github.com/Tauseef666-ctrl/YT-DESKTOP-.git, branch `dev`, HEAD `306685b62`, tag v0.29.4, version in `buildSrc/src/main/kotlin/ProjectConfig.kt` — don't change. User authorized commit+push.
- Do NOT commit CONTEXT.md.

## Frozen (do not edit)
`composeResources/**/strings.xml`, `navigation/Destination.kt`, `NavModule.kt`, `SettingsHomeScreen.kt`, `ProviderMessages.kt`. New UI strings = hardcoded English.

## Active skill
ponytail (lazy senior dev): smallest complete change; never cut error handling/security/a11y/user asks; end reply with skipped items + risk. Read `C:\Users\Tauseef khan\.config\opencode\skills\ponytail\SKILL.md` if needed.

## Goal
Make the player screen look/work like official NewPipe Android (repo has layouts under `app/src/main/res/layout/`). Also: video must actually play (libVLC crash fix in flight).

## Android reference (studied)
- `player.xml` (fullscreen overlay): black, surface fills; top = close/title+channel/quality/speed/queue/expand_more; center = prev/play/next; bottom = time/seek/time/rotation; secondary row behind expand_more.
- `fragment_video_detail.xml` (what opens on video tap, portrait): player top; title+expand toggle; uploader row (avatar+name+verified); "views · date"; action buttons (append/popup/download/share/browser); meta separator + description; ViewPager tabs "Next"/"Related"/Comments — queue rows = thumbnail + title + channel + duration (`play_queue_item.xml`), current highlighted; queue header has repeat/shuffle (`itemsListControl`).
- Android colors: dark bg #222222 accent #FF5252; light bg #EEEEEE accent #E53935 (already in Color.kt/PlusColors.kt).

## Playback facts
- vlcj 4.11.0. `MediaPlayerFactory(String...)` exists; `MediaApi.startPaused(String, String...)` exists; `StatusApi` has NO `errorMessage()` (use `status().state()`).
- Failure (runlog3): `[NewPipe+] libVLC playback error (state=ERROR)` then JVM died `0xC0000409` (native crash, likely Windows hw decoder). Extraction/resolve fine.
- Fix applied (compiled, **not yet verified live**): `JVMPlayerEngine.kt` `startPaused(url, HTTP_USER_AGENT_OPTION, ":avcodec-hw=none")` (SOFTWARE_DECODING_OPTION); prepare logs `[NewPipe+] prepare: host=... surfaceHosts=... displayable=...`.
- Escalate if still crashing: `MediaPlayerFactory("--file-logging", "--logfile=...", "-vvv")`.
- Play flow: TrendingScreen click → `ProviderPlayback.play` → `ExtractorMediaProvider` (jvmMain, NewPipeExtractor, max-height progressive) → `PlaybackController.playItem` → `prepareResumed` → engine.prepare. No auto-prepare at startup.
- `StreamMetadata` fields: title, uploaderName, uploaderUrl, uploaderVerified, subscriberCount, viewCount, likeCount, uploadDate, durationSeconds, thumbnailUrl, description, category, tags, isLive. **No avatar URL** — use first-letter circle.
- `MediaItem` fields: id, title, url, durationMs, isLive, audioOnly, thumbnailUrl, resumePositionMs. **No uploader** — queue row subtitle = Live/duration only.
- `PlayerQueueState`: items, currentIndex, isShuffled, repeatMode (enum RepeatMode { OFF, ALL, ONE } in PlayQueue.kt), current, isEmpty.
- `rememberThumbnailPainter(url): Painter?` in `image/ThumbnailPainter.kt`; usage: `if (painter != null) Image(painter, null, ContentScale.Crop, Modifier.fillMaxSize())`.
- PlusColors has: background, surface, surfaceElevated, surfaceSunken, border, divider, accent, onAccent, accentPressed, accentMuted, textPrimary, textSecondary, textMuted, success, warning, danger, overlayScrim.
- Theme spaces: XXSmall 2, XSmall 4, Small 8, Medium 10, Normal 12, Large 16, XLarge 32 dp.
- FilterChipRow(options, selectedIndex, onSelect, modifier, contentPadding=horizontal Large).
- HorizontalDivider already used elsewhere (BackupRestoreScreen.kt) — available.
- Drawables present: ic_arrow_back, ic_fullscreen(_exit), ic_pause, ic_play_arrow, ic_skip_next/prev, ic_stop, ic_search, ic_trending_up (created), ic_palette, ic_settings_backup_restore, ic_volume_*, ic_language, ic_history... **no ic_settings gear, no queue/repeat/shuffle icons** — use TextButtons.
- `newpipe.shared.generated` = `newpipe.shared.generated.resources` package.

## Player screen architecture (current, after rework)
`shared/.../screen/player/PlayerScreen.kt` (~865 lines): no scaffold/toolbar; Column bg=plusColors().background; `PlayerFrame` (video 16:9, widthIn max 1280, centered, onTapVideo toggles controlsVisible) then scroll Column weight(1f). `controlsVisible` auto-hides after 3s while playing (CONTROLS_AUTO_HIDE_MS). `showOverlay` = controlsVisible || empty || error || !engine || status in PAUSED/READY/ENDED/LOADING. `TransportControls` (private, same file): top = back(ic_arrow_back)/title+uploader/quality/speed/Mini text buttons; center = prev/play(64dp)/next; bottom = time/seek Slider(times)/fullscreen. White tint forced. Mini player = PopOutCoordinator toggle ("Mini" button).
- `PlayerFrame.kt`: MaxVideoWidth=1280.dp, params showControls/onTapVideo/video/controls.
- `PlayerSettingsPanel.kt`: `PlayerQuickSettings` (always-visible indicator chips + expand toggle → `PlayerAdvancedSettings` loop/autoplay/subtitles/audio-track) + private SettingsChipRow/formatSpeedLabel.
- `PlayerQueuePanel.kt`: SectionHeader title + rows (position#, title, subtitle=Live/duration, StatusChip current, ↑↓✕ QueueIconButton) + footer save/restore/skip playlist.
- Old below-video = web-style StreamInfoSection + queue + captions + quick settings + PlaybackSettings(sliders) + shuffle TextButton footer — REJECTED by user 3x.

## APPROVED PLAN (user said "go")
1. Below-video = Android detail order: **title (2-line) → uploader row (36dp first-letter circle + name + ✓ + subs) → "views · date" row → action row (Mini player TextButton + Settings TextButton toggles settingsExpanded) → HorizontalDivider → description (4 lines + Show more/less) → category·tags muted line**. Composable: `VideoDetailSection` (replaces StreamInfoSection in PlayerScreen.kt).
2. **Tabs** `FilterChipRow(["Up next", "Captions"])` with `selectedTab` state in PlayerScreenContent; overlay gets **"Queue" TextButton** → selectedTab=0. Captions tab shows CaptionsSection (Success) else "No captions available…" text; strip SectionHeader from CaptionsSection (tab labels it).
3. Queue = Android up-next: PlayerQueuePanel gains params isShuffled/repeatMode/onToggleShuffle/onRepeatMode; header Row = "Up next (N)" + "Repeat: Off/All/One" cycle button (OFF→ALL→ONE→OFF) + "Shuffle: on/off" button (move shuffle here from footer — delete shuffle footer row in PlayerScreen); rows = 120dp 16:9 thumbnail (rememberThumbnailPainter, 4dp clip, surfaceElevated bg) + title(2 lines)+Live/duration + StatusChip current + keep ↑↓✕ buttons (preserve reorder feature).
4. Fold `PlaybackSettings` (Speed chips / Volume slider+mute / Quality chips) into `PlayerAdvancedSettings` (params onSpeed/onVolume/onToggleMute/onQuality + state: PlaybackState); delete `PlaybackSettings` composable from PlayerScreen.kt; private SpeedOptions list duplicated in PlayerSettingsPanel.kt (same package).
5. Verify: compile in background → launch → click video → check `[NewPipe+] prepare:` in runlog, no state=ERROR/crash → screenshot → commit+push fork/dev → `:app:assembleDebug`.

### Explicitly skipped (tell user)
append-to-queue/share/open-in-browser buttons (MediaItem has stream URL only, no watch-page URL — would be dishonest); chapters/cast/kodi (no data model); drag-reorder (buttons stay); avatar image (no URL in metadata).

## Progress
- [x] Android port (nav rail, colors, toolbar, settings) — commit 306685b62 pushed
- [x] PlayQueueStore.save overflow catch; search/library responsive grids; ic_trending_up; PlayerFrame sizing; controls auto-hide; avcodec-hw=none + prepare log
- [x] PlayerScreen immersive restructure (no toolbar, back in overlay) — compiled
- [ ] **NOW: plan steps 1–4 (code edits not yet written)** → step 5 verify → commit/push
- Playback fix still unverified live.

## Key gotchas
- Never edit strings.xml (frozen). Hardcode English.
- FilterChipRow contentPadding for full-bleed inside padded parents.
- `StatusApi.state()` not errorMessage(). formatDuration/formatCount/formatSpeed exist.
- Previews at file bottoms must still compile (PlayerQueuePanelPreview, PlayerScreen*Preview) — update signatures when adding params with defaults.
- After edits: background compile `:shared:compileKotlinJvm`, poll compilelog for BUILD SUCCESSFUL, then relaunch app + screenshot, then commit.
