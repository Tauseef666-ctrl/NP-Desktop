# Feature Parity Plan

## Scope
Compare NewPipe Android behavior against the Windows Desktop port (feature-by-feature).

## Parity Matrix
| Area | Android | Desktop (current) | Status | Notes |
|---|---|---|---|---|
| Services (YouTube/SoundCloud/media.ccc/Bandcamp/PeerTube) | Yes | Yes via ExtractorServiceRegistry | Partial/Verified | Registry uses ServiceList; real extraction wired on JVM. |
| Search (streams/channels/playlists) | Yes | Yes (SearchScreen + ExtractorContentRepository) | In progress/Verified by compile+run | Real search path implemented; live end-to-end verification needed in UI session. |
| Stream page (info/streams/audio-video selection) | Yes | Yes (WatchPage + StreamViewModel) | Implemented | Watch page redesign: two-column layout, description with links/timestamps, channel row, related column with endless scrolling, real copy-link/open-channel/open-in-browser actions. |
| Channel page | Yes | Yes (ChannelScreen) | In progress | Implemented. |
| Playlist page | Yes | Yes (PlaylistScreen) | In progress | Implemented. |
| Playback (local) | ExoPlayer | VLCJ/libVLC | Implemented | Separate audio slave, controls, floating window. |
| Subscriptions/History/Playlists/DB | Room/Android | Watch history persisted as JSON in Settings; feed uses it | Partial | History is recorded and persisted. Subscriptions and playlist storage are still pending. |
| Downloads | Android service | Not started | Missing | Phase H. The desktop watch page therefore shows no download button. |
| Settings/Preferences | Android | Shared preferences (multiplatform-settings) present in shared | Partial | UI/settings screens not fully ported. |
| Import/Export | Yes | Not started | Missing | Phase G. |
| Packaging (MSI/ZIP) | N/A | Not started | Missing | Phase J. |

## Verification Plan
- [ ] Clean machine test (Windows) - blocked locally (no clean VM). Must run in CI.
- [x] Desktop compile green after fixes
- [x] Runtime starts with libVLC found, Koin started, no material3 skew crash
- [x] Extractor initialized early (ensured in registry/repo), search no longer shows cancellation cascade
- [ ] Manual: search \"lofi hip hop\", open stream, play in player window, seek/volume/speed/mute
- [ ] Manual: navigate between screens while playing

## Watch page scope
- Implemented: title, channel avatar/name/subscriber count, copy link, open channel, open in browser, expandable description with clickable URLs and timestamps, related videos with hover states and endless loading, video/audio quality selectors, immersive player.
- Intentionally omitted because the extraction engine offers no data or actions for them: comments, likes/dislikes, subscriptions, notification controls, account/profile controls, Create/upload controls, playback queue. No placeholder or fake controls are rendered for these.

## Notes
- Licensing: NewPipe GPL-3.0-or-later; libVLC LGPL-2.1+/GPL-2.1+ - notices required.
- No mock data in implementation.
