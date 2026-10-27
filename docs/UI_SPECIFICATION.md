# NewPipe Windows Desktop Port — UI Specification

## Overview
This document tracks UI layout, components, and behavior for preserving NewPipe's original design on Windows.

## Android Reference Inventory
- MainActivity + fragments/tabs (home, subscriptions, history, downloads, settings)
- PlayerActivity / mini-player / popup player
- SearchFragment with filters
- ChannelFragment, PlaylistFragment, VideoDetailFragment
- SubscriptionFragment, LocalPlaylistFragment, HistoryFragment, DownloadFragment
- Settings activities/fragments
- Dialogs/bottom sheets

## Shared/UI (Compose Multiplatform)
- Theme, colors, service themes in shared/src/commonMain/kotlin/net/newpipe/app/theme/
- Navigation in shared/src/commonMain/kotlin/net/newpipe/app/navigation/
- Composables in shared/src/commonMain/kotlin/net/newpipe/app/composable/
- Screens in shared/src/commonMain/kotlin/net/newpipe/app/screen/

## Desktop Implementation (current)
- Shell: desktopApp/src/main/kotlin/net/newpipe/app/Main.kt (Window + App)
- Player: desktopApp/src/main/kotlin/net/newpipe/app/player/PlayerWindow.kt (floating window, VLCJ)
- Screens: Search, Stream, Channel, Playlist (wired via Nav3)

## Preservation Targets
- App bar structure, navigation behavior
- Media card hierarchy (thumbnail, title, uploader, duration, metadata)
- Search workflow, filters, service selection
- Player controls and stream selection
- Channel/playlist layouts
- Subscriptions/history/downloads (to be implemented)
- Settings hierarchy (to be implemented)
- Themes (light/dark/system)

## Windows Adaptations
- Floating player window (separate Window) for desktop UX
- Keyboard/mouse input
- Resizable window, high-DPI
- Native file dialogs (planned)

## Verification
- Compare against Android layouts/screenshots as they become available
- Test at different window sizes and DPI
## Watch Page (Desktop)

### Layout
- Wide windows (>= 900 dp of content width): main column with the player and the video information; independent related-videos column on the right, width clamp(300 dp, 30% of width, 420 dp).
- Narrow windows: single scrolling column (player, information, related videos).
- Player: full width of its column, 16:9, black background, no frame or heavy rounding.
- Content padding: 12-16 dp; no additional padding around the player inside its column.

### Information column
- Title: headlineSmall, semibold, wraps naturally.
- Channel row: 40 dp avatar (letter fallback), uploader name, subscriber count when available, "View channel" action.
- Actions: pill buttons for "Copy link" and a "More" menu containing "Open channel" and "Open in browser". No like/dislike, download, subscribe or join controls, because the backend does not support them.
- Description: surfaceContainerLow panel with view count, upload date, duration, LIVE state; collapsed to 3 lines with Show more/Show less; URLs open in the browser and timestamps seek the player.
- Stream selectors: pill dropdowns for video and audio quality.

### Related column
- Heading "Related videos" with a short subtitle.
- Items: 152 dp thumbnail with duration/LIVE badge, title (2 lines), uploader, views and upload date.
- Hover: subtle surfaceContainerHigh background, Motion.fast fade.
- Endless loading: a sentinel loads more channel uploads when visible and reports when the list is exhausted.

### Player overlay
- Auto-hiding controls after 3.5 s of playback without pointer interaction.
- Seek bar with times, play/pause, +/- 10 s, previous/next, volume/mute, speed 0.25x-3x, immersive toggle, close.
- Immersive mode hides the navigation rail and page toolbar.

### Deliberate exclusions
- Comments, likes, subscriptions, notifications, account/profile controls, Create/upload controls and playback queue are not rendered because the extraction engine does not expose the required data or actions.
