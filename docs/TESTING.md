# Testing

## Test Strategy
- Unit tests for models/repo/VMs
- Integration tests for extractor/service registry
- UI tests (Compose/Desktop) where feasible
- Manual E2E on Windows

## Current Status
- No unit/integration tests added yet
- Manual runtime verified: app starts, libVLC found, Koin initialized, no material3 crash

## Test Plan
- [ ] Domain models
- [ ] Service registry
- [ ] Search/pagination/cancellation
- [ ] Playback controller
- [ ] Navigation
- [ ] Theme switching
- [ ] Window resize/high-DPI

## Regression
Track bugs in TODO.md
## Watch Page Test Cases
- [ ] Open a stream from Home, Search and a related item; the correct title, channel, description and quality list appear.
- [ ] Start playback; video is visible, audio is audible, controls auto-hide during playback and reappear on pointer movement.
- [ ] Seek with the seek bar, -10 s, +10 s and a timestamp in the description; the position changes accordingly.
- [ ] Change video and audio quality; playback restarts with the selected streams.
- [ ] Copy link puts the canonical URL on the clipboard; Open in browser launches the system browser; View channel opens the channel.
- [ ] Expand/collapse the description; URLs are clickable and playback continues.
- [ ] Scroll the related column; more items load once the sentinel becomes visible and the list ends with "No more suggestions".
- [ ] Play a video, navigate to another stream and back; playback state stays unambiguous and metadata is not stale.
- [ ] Enter immersive mode; the rail and toolbar disappear and Escape/fullscreen exit restores the normal layout.
- [ ] Resize through 1280x720, 1366x768, 1440x900 and 1920x1080; the related column switches between side-by-side and stacked without clipping.
- [ ] Confirm no comments, likes, subscriptions, notifications, account or Create controls are shown.
