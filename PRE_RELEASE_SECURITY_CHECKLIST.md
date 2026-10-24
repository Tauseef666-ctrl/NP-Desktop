# PRE-RELEASE SECURITY CHECKLIST

Complete before publishing Windows Desktop and/or Android TV releases.

## Repository & Privacy
- [ ] Working tree clean of unintended personal files (no local.properties, keystores, .env)
- [ ] Git history scanned for secrets/personal info
- [ ] Screenshots/docs reviewed for PII
- [ ] .gitignore updated appropriately
- [ ] Attribution/licenses intact (NewPipe GPL-3.0)

## Application Security
- [ ] Windows Desktop: file access, downloads, URL handling reviewed
- [ ] Android/Android TV: permissions minimal, exported components reviewed, no debuggable in release
- [ ] Network: TLS used, no cleartext unless necessary and documented
- [ ] Logging: no secrets/tokens in logs
- [ ] Local storage: no sensitive user data bundled

## Dependencies & CI
- [ ] Dependencies reviewed (no known critical vulns)
- [ ] GitHub Actions use least privilege
- [ ] Third-party actions pinned where practical
- [ ] Security workflow passing

## Release Artifacts
- [ ] Windows build (MSI/portable) tested
- [ ] Android TV APK tested on target device/emulator
- [ ] Artifacts scanned for secrets
- [ ] SHA-256 checksums generated and verified
- [ ] Release notes prepared (no sensitive details)
- [ ] Version/tag correct

## Publication
- [ ] No unresolved Critical/High findings
- [ ] Security/Privacy docs updated
- [ ] Correct repo/target confirmed
- [ ] Final review complete
