# Keyboard Shortcuts

NP Desktop keyboard shortcuts (from `shared/src/commonMain/kotlin/net/newpipe/app/ui/PlayerShortcuts.kt` and `desktopApp/src/main/kotlin/net/newpipe/app/Main.kt`).

Shortcuts are handled in preview at the app root and are dispatched from the desktop window. While a text field has focus, shortcuts are ignored except `Esc`.

## Playback

| Shortcut | Action |
| --- | --- |
| `Space` / `K` | Play / pause |
| `J` | Seek backward by the configured seek duration (default 10 s; from Video & Audio settings) |
| `L` | Seek forward by the configured seek duration |
| `Left Arrow` | Seek backward by half the configured seek duration |
| `Right Arrow` | Seek forward by half the configured seek duration |
| `,` (while paused) | Step backward one frame (~40 ms) |
| `.` (while paused) | Step forward one frame (~40 ms) |
| `Home` | Seek to start |
| `End` | Seek near end (`duration - 1 s`) |
| `0`–`9` | Seek to 0%–90% of the video duration (`0` seeks to start when duration is unknown) |
| `Shift` + `N` | Next item (via screen hook) |
| `Shift` + `P` | Previous item (via screen hook) |
| `Ctrl` + `Right Arrow` | Next chapter (via screen hook) |
| `Ctrl` + `Left Arrow` | Previous chapter (via screen hook) |

## Volume

| Shortcut | Action |
| --- | --- |
| `Up Arrow` | Volume up (+5, max 100) |
| `Down Arrow` | Volume down (-5, min 0) |
| `M` | Toggle mute |

## Speed

| Shortcut | Action |
| --- | --- |
| `Shift` + `,` | Decrease playback speed by 0.25× (min 0.25×) |
| `Shift` + `.` | Increase playback speed by 0.25× (max 3×) |

## Captions

| Shortcut | Action |
| --- | --- |
| `C` | Toggle captions (via screen hook) |
| `O` | Cycle caption brightness (via screen hook) |
| `W` | Cycle caption background (via screen hook) |
| `=` | Increase caption size (via screen hook) |
| `-` | Decrease caption size (via screen hook) |

*Caption/chapter hooks are only active when the current screen provides them.*

## Navigation

| Shortcut | Action |
| --- | --- |
| `/` | Open Search |
| `Shift` + `/` | Show shortcuts/help (via screen hook) |
| `I` | Toggle popup player (hook, or `PopupPlayer.toggle()`) |

## Fullscreen / Window

| Shortcut | Action |
| --- | --- |
| `F` | Toggle video fullscreen |
| `T` | Toggle immersive/theatre mode |
| `Esc` | Exit fullscreen, then app fullscreen, then immersive mode, then close popup (if open); otherwise not consumed |
| `F11` | Toggle app fullscreen (desktop window; handled in `desktopApp/Main.kt`) |
