# NewPipe Windows Desktop — Project TODO

## Project Status
- Current phase: Phase 1 — Repository Audit (in progress)
- Current working build: Unknown (will verify)
- Last verified build: N/A
- Current blocker: None
- Next recommended task: Complete full repository audit and update UI_SPECIFICATION.md

## Phase 1 — Repository Audit
- [x] Inspect repo structure (modules: app, desktopApp, shared, iosApp)
- [x] Identify build system (Kotlin Multiplatform, Compose Multiplatform, Gradle 9.8.2)
- [x] Audit supported services (via NewPipeExtractor ServiceList)
- [x] Audit playback architecture (VLCJ/libVLC on desktop, ExoPlayer on Android)
- [x] Identify Android-specific deps (Context, PreferenceManager, etc.)
- [x] Identify reusable modules (shared commonMain theme/nav/composables)
- [ ] Audit all app layouts (113 XML layouts) for UI patterns
- [ ] Audit fragments/activities for navigation and screens
- [ ] Audit player implementations
- [ ] Audit downloads/subscriptions/history/playlists/settings
- [ ] Document extraction architecture in detail

## Phase 2 — UI Specification
- [ ] Create comprehensive UI_SPECIFICATION.md with all screens
- [ ] Define design tokens (colors, typography, spacing, radius, elevation)
- [ ] Define responsive breakpoints (900, 1200, 1600+)
- [ ] Document navigation structure
- [ ] Document player layouts
- [ ] Document all screens and states
- [ ] Define animation system and reduced-motion
- [ ] Verify against source

## Phase 3 — Windows Architecture
- [ ] Select/confirm framework (Compose Desktop - already in use)
- [ ] Implement application shell with bezel-less design
- [ ] Custom window chrome or native with integration
- [ ] Navigation rail/sidebar responsive
- [ ] Platform adapters
- [ ] Persistence (Room KMP planned)
- [ ] Error handling/logging

## Phase 4 — Desktop UI Implementation
- [ ] Redesign shell (navigation sidebar, toolbar, content area)
- [ ] Home/feed with responsive media grid
- [ ] Search with filters
- [ ] Player with two-column layout on wide screens
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
