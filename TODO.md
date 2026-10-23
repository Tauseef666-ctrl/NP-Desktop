# NewPipe Windows Desktop — Project TODO

## Project Status
- Current phase: Phase 1 — Repository Audit (in progress)
- Current working build: Unknown (will verify)
- Last verified build: 2026-10-23 (compile + runtime on Windows, home feed loaded 15 kiosk items)
- Current blocker: None
- Next recommended task: Extend responsive grid to channel/playlist screens

## Phase 1 — Repository Audit
- [x] Inspect repo structure (modules: app, desktopApp, shared, iosApp)
- [x] Identify build system (Kotlin Multiplatform, Compose Multiplatform, Gradle 9.8.2)
- [x] Audit supported services (via NewPipeExtractor ServiceList)
- [x] Audit playback architecture (VLCJ/libVLC on desktop, ExoPlayer on Android)
- [x] Identify Android-specific deps (Context, PreferenceManager, etc.)
- [x] Identify reusable modules (shared commonMain theme/nav/composables)
- [x] Audit app layouts (113 XML layouts) reviewed
- [x] Audit MainActivity/fragments for navigation patterns
- [x] Audit player implementations (Android + desktop)
- [x] Audit downloads/subscriptions/history/playlists/settings structure
- [x] Document extraction architecture (ExtractorContentRepository, JvmDownloader)

## Phase 2 — UI Specification
- [x] Create comprehensive UI_SPECIFICATION.md with all screens
- [x] Define design tokens (colors, typography, spacing, radius, elevation)
- [x] Define responsive breakpoints (900, 1200, 1600+)
- [x] Document navigation structure (Android + desktop)
- [x] Document player layouts
- [x] Document all screens and states
- [x] Define animation system and reduced-motion
- [x] Verify against source (layouts, fragments, activities reviewed)

## Phase 3 — Windows Architecture
- [x] Select/confirm framework (Compose Desktop - already in use)
- [x] Implement application shell with bezel-less design (ModernShell)
- [x] Native window chrome integrated (Compose Desktop Window)
- [x] Navigation rail/sidebar responsive (collapsible)
- [x] Platform adapters (VLCJ/native locator, extractor jvmMain)
- [~] Persistence (Room KMP planned, not yet integrated)
- [x] Error handling/logging (Kermit, try/catch with mapping)

## Phase 4 — Desktop UI Implementation
- [ ] Redesign shell (navigation sidebar, toolbar, content area)
- [x] Home/feed with responsive media grid
- [ ] Search with filters
- [x] Player with two-column layout on wide screens
- [ ] Channel pages
- [ ] Subscriptions
- [ ] Playlists and queue
- [ ] History
- [ ] Downloads
- [ ] Settings
- [ ] Dialogs/menus
- [ ] Loading/empty/error states

## Phase 5 — Modern Visual Design & Animations
- [ ] Design token system
- [ ] Dark/light themes
- [ ] Bezel-less styling
- [ ] Hover/focus/pressed states
- [ ] Transitions
- [ ] Card animations
- [ ] Player animations
- [ ] Menu/dialog animations
- [ ] Reduced-motion support

## Phase 6 — Functional Integration
- [x] Extractor wired (JvmDownloader, ExtractorContentRepository)
- [x] VLCJ playback wired
- [ ] Full feature parity for core features
- [ ] Downloads
- [ ] Subscriptions/history/playlists persistence
- [ ] Settings

## Phase 7 — Responsive & Accessibility Testing
- [ ] Test all breakpoints
- [ ] Test scaling
- [ ] Keyboard navigation
- [ ] Focus states
- [ ] Reduced motion

## Phase 8 — Feature & Regression Testing
- [ ] E2E testing
- [ ] Service compatibility

## Phase 9 — Windows Release
- [ ] Packaging
- [ ] Clean build
- [ ] Documentation

## Final Acceptance
- [ ] All criteria met
- [ ] Docs updated
- [ ] Limitations documented

## Status Legend
- [ ] Not started
- [x] Implemented and verified
- [!] Blocked (explain)
- IN PROGRESS

Last Updated: 2026-10-22
