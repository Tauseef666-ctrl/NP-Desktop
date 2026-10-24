# RELEASE_PROCESS.md

## Versioning
- Use semantic versioning where practical (e.g., 0.29.x)
- Update version in `buildSrc/src/main/kotlin/ProjectConfig.kt` and related configs

## Build (Windows Desktop)
```bash
./gradlew.bat :desktopApp:packageMsi
./gradlew.bat :desktopApp:packageDistributionForCurrentOS
```
Artifacts in `desktopApp/build/compose/binaries/main/`

## Build (Android/Android TV)
```bash
./gradlew.bat assembleRelease
```
Sign with release keystore (outside repo).

## Security Checks
1. Run secret scan (gitleaks)
2. Review dependencies
3. Check for PII in artifacts/screenshots
4. Verify permissions/configs

## Checksums
```bash
# Windows (PowerShell)
Get-FileHash .\desktopApp\build\compose\binaries\main\msi\*.msi -Algorithm SHA256
```
Publish checksums with release.

## Publishing
1. Create tag `vX.Y.Z`
2. Create GitHub Release for Windows-only (or combined as appropriate)
3. Upload artifacts + checksums
4. Complete PRE_RELEASE_SECURITY_CHECKLIST.md

## Notes
- Never commit keystores/secrets
- For this fork, focus on Windows Desktop releases as requested
- Keep Android/Android TV builds functional but separate
