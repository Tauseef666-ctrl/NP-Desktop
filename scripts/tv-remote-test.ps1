<#
.SYNOPSIS
    Debug + TV remote-control smoke test for the NewPipe phone+TV / Android TV APK.

.DESCRIPTION
    1. Verifies the Android TV configuration of an APK with aapt (leanback launcher, TV banner,
       touchscreen + leanback declared as not required).
    2. Installs the APK on a connected device/emulator (adb).
    3. Launches the leanback launcher activity and drives a D-pad / media-remote key sequence
       over adb so the player and remote handling can be exercised on a real Android TV box
       or an Android TV emulator image.

    Without a device connected, run with -ConfigOnly to still assert the TV packaging.

.PARAMETER Apk
    Path to the APK. Defaults to the locally built continuous APK.

.PARAMETER ConfigOnly
    Only verify the Android TV packaging with aapt; do not install or drive a device.

.PARAMETER SkipInstall
    Install is attempted, but if the package is already present it is not reinstalled.

.PARAMETER Serial
    Optional adb device serial (use when several devices are attached).

.EXAMPLE
    # Verify TV packaging only (no device needed)
    .\scripts\tv-remote-test.ps1 -ConfigOnly

.EXAMPLE
    # Full debug + remote test on a connected Android TV device
    .\scripts\tv-remote-test.ps1 -Apk app\build\outputs\apk\continuous\app-continuous.apk
#>
param(
    [string]$Apk = "app/build/outputs/apk/continuous/app-continuous.apk",
    [switch]$ConfigOnly,
    [switch]$SkipInstall,
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    Write-Host "FAIL: $Message" -ForegroundColor Red
    exit 1
}

function Ok([string]$Message) {
    Write-Host "OK:   $Message" -ForegroundColor Green
}

# ---------------------------------------------------------------------------------------------
# Resolve SDK tooling
# ---------------------------------------------------------------------------------------------
$sdk = $env:ANDROID_HOME
if (-not $sdk -and $env:ANDROID_SDK_ROOT) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "C:\Android\Sdk" }
if (-not (Test-Path $sdk)) {
    Fail "Android SDK not found (ANDROID_HOME is '$sdk'). Install the SDK or set ANDROID_HOME."
}

$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Fail "adb not found at $adb" }

$bt = Get-ChildItem (Join-Path $sdk "build-tools") -Directory | Sort-Object Name -Descending | Select-Object -First 1
if (-not $bt) { Fail "No build-tools installed under $sdk\build-tools" }
$aapt = Join-Path $bt.FullName "aapt.exe"
if (-not (Test-Path $aapt)) { Fail "aapt not found at $aapt" }

if (-not (Test-Path $Apk)) { Fail "APK not found at '$Apk'" }
$Apk = (Resolve-Path $Apk).Path
Write-Host "APK:   $Apk"

# ---------------------------------------------------------------------------------------------
# 1. Verify the Android TV packaging
# ---------------------------------------------------------------------------------------------
$badging = & $aapt dump badging $Apk | Out-String
$pkgLine = $badging | Select-String -Pattern "^package: name='([^']+)'" | Select-Object -First 1
if (-not $pkgLine) { Fail "Could not read package name from aapt badging." }
$pkg = $pkgLine.Matches[0].Groups[1].Value
Ok "package $pkg"

$tvRequirements = @(
    "leanback-launchable-activity",
    "uses-feature-not-required: name='android.software.leanback'",
    "uses-feature-not-required: name='android.hardware.touchscreen'",
    "banner='"
)
foreach ($req in $tvRequirements) {
    if ($badging -notlike "*$req*") { Fail "Android TV packaging missing: $req" }
    Ok "packaging contains '$req'"
}

$activityLine = $badging | Select-String -Pattern "leanback-launchable-activity: name='([^']+)'" | Select-Object -First 1
$activity = $activityLine.Matches[0].Groups[1].Value
Ok "leanback launcher activity $activity"

if ($ConfigOnly) {
    Write-Host "`nAndroid TV packaging verified (config only; no device was used)." -ForegroundColor Cyan
    exit 0
}

# ---------------------------------------------------------------------------------------------
# 2. Require a connected device / emulator
# ---------------------------------------------------------------------------------------------
$serialArgs = if ($Serial) { @("-s", $Serial) } else { @() }
$state = & $adb @serialArgs get-state 2>&1 | Out-String
if ($state -notmatch "device") {
    $msg = "No adb device is connected, so the remote-control test cannot run."
    $msg += " 'adb get-state' said: $state"
    $msg += " Connect an Android TV box / TV emulator (adb connect <ip>) or run with -ConfigOnly."
    Fail $msg
}
Ok "adb device connected"

# ---------------------------------------------------------------------------------------------
# 3. Install the APK
# ---------------------------------------------------------------------------------------------
if (-not $SkipInstall) {
    Write-Host "Installing APK..."
    $install = & $adb @serialArgs install -r $Apk 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -and $install -notmatch "Success") { Fail "adb install failed: $install" }
    Ok "installed"
} else {
    Ok "install skipped"
}

# ---------------------------------------------------------------------------------------------
# 4. Launch the leanback activity
# ---------------------------------------------------------------------------------------------
$component = "$pkg/$activity"
Write-Host "Launching $component ..."
& $adb @serialArgs shell am start -n $component | Out-Null
Start-Sleep -Seconds 6

# ---------------------------------------------------------------------------------------------
# 5. Drive a D-pad / media remote key sequence
# ---------------------------------------------------------------------------------------------
# name           : code  (Android KeyEvent keycode)
$remoteKeys = @(
    @{ name = "DPAD_UP";           code = 19 },
    @{ name = "DPAD_DOWN";         code = 20 },
    @{ name = "DPAD_LEFT";         code = 21 },
    @{ name = "DPAD_RIGHT";        code = 22 },
    @{ name = "DPAD_CENTER (OK)";  code = 23 },
    @{ name = "BACK";              code = 4 },
    @{ name = "MEDIA_PLAY_PAUSE";  code = 85 },
    @{ name = "MEDIA_NEXT";        code = 87 },
    @{ name = "MEDIA_PREVIOUS";    code = 88 },
    @{ name = "MEDIA_FAST_FORWARD";code = 90 },
    @{ name = "MEDIA_REWIND";      code = 89 },
    @{ name = "MEDIA_PLAY";        code = 126 },
    @{ name = "MEDIA_PAUSE";       code = 127 }
)

Write-Host "`nSending remote key sequence (open a video first so the player receives transport keys)..."
foreach ($k in $remoteKeys) {
    & $adb @serialArgs shell input keyevent $k.code | Out-Null
    Write-Host ("  sent {0,-20} (keycode {1})" -f $k.name, $k.code)
    Start-Sleep -Milliseconds 900
}

Write-Host "`nRemote key sequence delivered." -ForegroundColor Cyan
Write-Host "Watch the device: D-pad should move focus, OK should select/play, media keys should"
Write-Host "control playback, LEFT/RIGHT should seek while the player controls are hidden."
exit 0
