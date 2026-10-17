# NewPipe+ — Design System & Port Plan

Companion to `FEATURE_INVENTORY.md`. Defines the visual system, reusable components,
and the architecture for porting `:app` behaviour onto `shared`/JVM for a real Windows app.

> Reference image not supplied; palette below follows the brief (premium near-black,
> charcoal, blue-gray borders, NewPipe red accent). Adjust once the image arrives.

---

## 1. Principles

1. **Dark-first, cinematic.** Near-black backgrounds, charcoal cards, restrained red accent.
2. **Compact but legible.** Media-forward density; consistent 16:9 thumbnails.
3. **Player + settings together.** Never split the player from its controls.
4. **No fake affordances.** Every control changes real behaviour or is marked unavailable.
5. **Consistent across platforms.** Same tokens on desktop/Android/TV; layout adapts.
6. **Preserve everything.** Restyle/move, never remove (see feature inventory).

---

## 2. Color tokens

Semantic tokens are exposed via `plusColors()` (a `CompositionLocal` provided by `AppTheme`,
derived from the active color scheme so light/dark both work). Material3 roles keep working.

| Token | Dark | Light | Use |
|---|---|---|---|
| `background` | `#0A0A0B` | `#F7F7F9` | App canvas |
| `surface` | `#0F0F12` | `#FFFFFF` | Panels, cards base |
| `surfaceElevated` | `#17171C` | `#FFFFFF` | Raised cards, thumbnails |
| `surfaceSunken` | `#070708` | `#EFEFF3` | Wells, inputs |
| `border` | `#2A2A33` | `#D9D9E0` | Blue-gray 1px borders |
| `divider` | `#1E1E25` | `#E6E6EC` | Hairlines |
| `accent` | `#E53935` | `#CD201F` | Primary actions, active nav, progress |
| `onAccent` | `#FFFFFF` | `#FFFFFF` | Text/icon on accent |
| `accentPressed` | `#B71C1C` | `#9E1918` | Pressed state |
| `accentMuted` | `#3A1416` | `#FFDAD6` | Accent containers |
| `textPrimary` | `#F2F2F5` | `#1A1A1E` | Titles |
| `textSecondary` | `#A8A8B3` | `#55555F` | Meta/channel |
| `textMuted` | `#6E6E7A` | `#8A8A94` | Disabled/subtle |
| `success` | `#4CAF50` | `#2E7D32` | Completed |
| `warning` | `#FFB300` | `#EF6C00` | Paused/attention |
| `danger` | `#EF5350` | `#C62828` | Failed/delete |
| `overlayScrim` | `#CC000000` | `#66000000` | Media overlay, badges |

Dark scheme surfaces in `theme/Color.kt` are updated to these values; Material3 roles
(`onSurface`, `outline`, etc.) are aligned so existing screens stay readable.
Per-service brand tints (`ServiceTheme.kt`) are unchanged.

---

## 3. Typography, spacing, shape

- **Type:** Material3 `MaterialExpressiveTheme` defaults (no custom fonts yet). Hierarchy:
  `headlineSmall` (screen/section hero) → `titleMedium` (section header) → `titleSmall`
  (card title) → `bodySmall` (meta). Numbers use tabular feel where practical.
- **Spacing:** existing `theme/Dimens.kt` (`spaceXXSmall 2 … spaceXLarge 32`). Add
  `spaceXXLarge 48`.
- **Radius:** `4` badges, `8` inputs/chips, `10` thumbnails/cards, `12` panels, `999` pills.
- **Elevation:** flat-first; rely on `surfaceElevated` + 1px `border` instead of heavy shadows.
- **Motion:** 120–180ms ease-out for hover/selection; reduced-motion respected.

---

## 4. Component catalogue (`shared/.../component`)

| Component | API (sketch) | Notes |
|---|---|---|
| `SectionHeader` | `(title, actionLabel?, onAction?)` | Rail/section title + optional "See all". |
| `VideoCard` | `(title, subtitle?, thumbnail?, durationText?, isLive?, badge?, onClick)` | 16:9, rounded, duration/LIVE badges. |
| `VideoRail` | `(title, items: List<VideoCardModel>, onSeeAll?)` | `LazyRow` of cards. |
| `NavEntry` / `NavRail` | `(entries: List<NavEntry>, header?)` | Sidebar items, active indicator, tooltips. |
| `SearchBarField` | `(value, onValueChange, onSubmit, suggestions?)` | Debounced suggestions hook. |
| `StatusChip` | `(text, kind: Info/Success/Warning/Danger/Live)` | Download + live states. |
| `DownloadItem` | `(title, thumbnail?, progress, speed?, size?, status, actions…)` | Real progress only. |
| `LocalMediaRow` | `(title, subtitle?, duration?, size?, onClick)` | Library rows. |
| `PlayerFrame` | `(video: @Composable, controls: @Composable, settings: @Composable)` | Player + integrated controls/settings. |
| `LoadingState` | `(message?)` | Centered spinner. |
| `EmptyState` | `(title, message?, actionLabel?, onAction?)` | Empty rails/screens. |
| `ErrorState` | `(message, retryLabel?, onRetry?)` | Mirrors `ErrorCard` taxonomy. |
| `PlusScaffold` | `(rail: @Composable?, toolbar: @Composable?, content)` | Desktop shell frame. |

All components: previews via `ThemePreviewProvider`, keyboard-focusable, content descriptions,
`testTag`s on interactive elements for UI tests.

---

## 5. Screen blueprints

- **Shell:** left `NavRail` (collapsible) · top toolbar (back, title, search, actions) ·
  content · optional bottom player bar that expands to full `PlayerFrame`.
- **Home:** rails — Continue watching → Trending → Subscriptions → Local → Playlists. Real data only.
- **Search:** field + suggestions + history; tabs Videos/Channels/Playlists/Local.
- **Subscriptions / Feed:** groups sidebar + item list.
- **Trending / Popular:** kiosk chips + grid.
- **Channels:** header (avatar/subs/desc) + content tabs.
- **Playlists:** remote + local; reorder for local.
- **History:** list + clear (privacy).
- **Downloads:** filter chips (All/Queued/Downloading/Paused/Completed/Failed) + `DownloadItem` list.
- **Local Media:** folder management + Videos/Audio/Playlists/Folders/Recent views.
- **Settings:** all existing categories; append Backup & Restore + About.
- **Player:** `PlayerFrame` — surface + overlay controls + expandable settings panel
  (quality, speed, subtitles, audio, loop, autoplay, PiP, background).

### Responsive breakpoints (desktop)
- **Compact** `< 900dp`: rail collapses to icons; single-column rails.
- **Balanced** `900–1280dp`: full rail, 3–4 cards/row.
- **Expanded** `> 1280dp`: full rail + wider rails, 5–6 cards/row, optional 2-pane player+list.

---

## 6. Accessibility

- Visible focus rings on all interactive elements; full keyboard traversal.
- Content descriptions / `contentDescription` for icons; `testTag` for tests.
- Minimum 4.5:1 text contrast on the token pairs above.
- Destructive actions (delete file, reset, restore-overwrite) require confirmation.
- Status conveyed by icon/text, not color alone.
- Reduced-motion: disable non-essential animation.

---

## 7. Shared / JVM port plan

**Rule:** extend `shared`; never fork a second provider/player/download implementation.
Expose platform behaviour through `expect`/`actual` interfaces in `shared/commonMain` with
JVM `actual`s in `jvmMain`/`:desktopApp`.

| Subsystem | Interface (commonMain) | Desktop impl (jvmMain/desktopApp) | Reference |
|---|---|---|---|
| Player | `PlayerEngine` (state, play/pause/seek/speed/quality/subs/audio/queue) | VLCJ (`uk.co.caprica:vlcj`) wrapping the bundled libVLC | `:app` `player/Player.java`, `PlayerHelper` |
| Extraction | reuse NewPipeExtractor (JVM-compatible) | OkHttp `Downloader` | `:app` `App.kt`, `ExtractorHelper` |
| Downloads | `DownloadEngine`/`DownloadManager` (states, resume) | JVM block downloader + muxers | `giga/*`, `postprocessing/*` |
| Persistence | repository interfaces | JVM store (SQLite/KV) mirroring Room shape | `database/*`, schemas `2..9` |
| Local media | `LocalLibrary.scan(folder)` | filesystem scan | (new) |
| Settings | existing `Settings` (multiplatform-settings) | `PreferencesSettings` (already added) | `preferences/*` |
| Backup | `BackupManager` (ZIP: `newpipe.db`+prefs) | JVM zip + optional AES-GCM | `settings/export/*` |

**Desktop packaging:** add `TargetFormat.Exe` + `vendor` + `iconFile` + `upgradeUuid` to
`desktopApp/build.gradle.kts`; add a CI Windows job to build MSI/EXE and boot-smoke.

---

## 8. Stage → feature mapping

| Stage | Features | Screens/subsystems |
|---|---|---|
| 2 | F1 mini-player, F2 player center, F3 downloads, F9 resume | `PlayerFrame`, `NavRail`, `DownloadItem`, JVM player + download engines |
| 3 | F4 backup, F5 local library, F6 queue/playlists | Backup screen, Local Media, queue controller |
| 4 | F7 shortcuts, F8 workspace | Shell, settings, persistence |
| 5 | F10 providers | Extractor boundary + mocks |
| 6 | regression + packaging | tests, MSI/EXE, release |

---

## 9. Non-goals / limitations

- No bypass of DRM, auth, or access controls.
- iOS/macOS are out of scope (targets, sources and packaging removed).
- Android `:app` stays as-is reference until screens are migrated; must keep building.
- No new telemetry/analytics.
- No custom cryptography (use vetted AES-GCM + KDF only).
