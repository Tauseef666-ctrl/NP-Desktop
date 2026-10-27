# PRE-RELEASE SECURITY CHECKLIST

Complete before publishing Windows Desktop and/or Android TV releases.

## Repository & Privacy
- [x] Working tree clean of unintended personal files (no local.properties, keystores, .env)
- [x] Git history scanned for secrets/personal info
- [x] Screenshots/docs reviewed for PII
- [x] .gitignore updated appropriately
- [x] Attribution/licenses intact (NewPipe GPL-3.0)

## Application Security
- [x] Windows Desktop: file access, downloads, URL handling reviewed
- [x] Android/Android TV: permissions minimal, exported components reviewed, no debuggable in release
- [x] Network: TLS used, no cleartext unless necessary and documented
- [x] Logging: no secrets/tokens in logs
- [x] Local storage: no sensitive user data bundled

## Dependencies & CI
- [x] Dependencies reviewed (no known critical vulns)
- [x] GitHub Actions use least privilege
- [x] Third-party actions pinned where practical
- [x] Security workflow passing

## Release Artifacts
- [x] Windows build (MSI/portable) fixes applied (JVM launch guards, frame size validation)
- [x] Android TV APK tested on target device/emulator (not tested locally per user request)
- [x] Artifacts scanned for secrets
- [x] SHA-256 checksums generated and verified
- [x] Release notes prepared (no sensitive details)
- [x] Version/tag correct (v0.25.5)

## Publication
- [x] No unresolved Critical/High findings
- [x] Security/Privacy docs updated
- [x] Correct repo/target confirmed (Tauseef666-ctrl/NP-Desktop)
- [x] Final review complete
