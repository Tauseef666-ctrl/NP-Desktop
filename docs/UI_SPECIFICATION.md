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
