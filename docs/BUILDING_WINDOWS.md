# Building on Windows

## Prerequisites
- JDK 21 (Temurin)
- Android SDK (for shared Android targets)
- Gradle wrapper
- VLC/libVLC available (pre-extracted at %USERPROFILE%\.gradle\vlcSetup\vlc or system)

## Build
`ash
./gradlew :desktopApp:compileKotlin
./gradlew :shared:compileKotlinJvm
./gradlew :desktopApp:run
`

## Package
`ash
./gradlew :desktopApp:packageDistributionForCurrentOS
`
Outputs in desktopApp/build/compose/binaries/

## Notes
- libVLC must be bundled for distribution
- Material3 aligned to 1.12.0-alpha03 for Compose 1.12.2
