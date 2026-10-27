# Phase 1: Repository Inventory and Security Baseline

## Upstream
- Upstream: https://github.com/TeamNewPipe/NewPipe
- Current branch: feature/windows-desktop
- Fork adds Windows desktop (Compose Desktop) improvements, UI/UX refinements, kiosk/feed, subscriptions, history, downloads, keyboard shortcuts, popup player, etc.

## Structure
- app/ - Android app
- shared/ - Kotlin Multiplatform shared code
- desktopApp/ - Compose Desktop entry
- androidApp/ - Android app module
- iosApp/ - iOS app
- .github/ - workflows
- gradle/ - wrapper
- docs/ - documentation
- assets/ - assets
- fastlane/ - metadata

## Build
- KMP/Compose Multiplatform, Gradle 9.8.2
- Windows desktop via desktopApp:run
- Android via app module

## Notes
- Baseline established.
