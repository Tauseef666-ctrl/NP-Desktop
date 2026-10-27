# PRIVACY.md

## Data Collection
This fork aims to minimize data collection. By default:

- **Network requests** go directly to the media services you select (e.g., YouTube, SoundCloud, PeerTube, Bandcamp, media.ccc.de). These requests follow each service's terms and policies.
- **No built-in telemetry/analytics** are added by this fork.
- **No account credentials** are stored unless explicitly configured by third-party services (and not by default).

## Local Data Storage
- **History/Subscriptions/Playlists**: Stored locally using the app's database (Room on Android, local storage on Desktop).
- **Downloads**: Saved to user-configured directories (Desktop: `Videos/NewPipe` by default; Android: app/media directories per user settings).
- **Preferences**: Stored via platform settings (SharedPreferences/Multiplatform Settings).
- **Cache**: Temporary files/cached metadata stored in app cache directories.

## Logging
- Application logs are written locally for debugging. Avoid sharing logs containing sensitive URLs/tokens.
- Debug logging should not be enabled in production contexts unnecessarily.

## Third-Party Services
When you play content from third-party platforms, those platforms may log your IP address, requests, and usage per their own privacy policies. This app acts as a client and cannot control their logging.

## Limitations
- This document describes verified behavior; external services may change behavior.
- Users are responsible for complying with local laws and service terms.
- No anonymity guarantees are made.
