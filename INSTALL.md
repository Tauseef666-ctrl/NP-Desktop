# Installing NP Desktop

This guide covers installing NP Desktop on Windows and Linux, verifying your download, and troubleshooting the most common problems.

## Download

1. Go to the [Releases page](https://github.com/Tauseef666-ctrl/NP-Desktop/releases).
2. Open the latest release (currently **v0.25.6**).
3. Download the file for your platform:

| Platform | File | Notes |
| --- | --- | --- |
| Windows 10/11 (x64) | `NP-Desktop-<version>.msi` | Bundles a JRE and the VLC libraries — no separate installs needed |
| Debian/Ubuntu (amd64) | `np-desktop_<version>_amd64.deb` | Same |
| Android | `NewPipe_v<version>.apk` | Android phone and Android TV builds |

*(Exact filenames can vary slightly between releases — use the file listed on the release page.)*

## Verify the checksum (recommended)

Each release ships SHA-256 checksum files (for example `checksums-windows-sha256.txt` and `checksums-linux-sha256.txt`). Verify your download before installing.

**Windows** (PowerShell or Command Prompt):

```powershell
certutil -hashfile .\NP-Desktop-<version>.msi SHA256
```

or:

```powershell
(Get-FileHash .\NP-Desktop-<version>.msi -Algorithm SHA256).Hash
```

**Linux:**

```bash
sha256sum np-desktop_<version>_amd64.deb
```

Compare the output with the corresponding checksum file attached to the release. If they do not match, re-download the file; if the mismatch persists, do not install it and open an issue.

## Windows Installation

**Prerequisites:** none beyond the installer. The MSI bundles its own JRE and the VLC libraries, and targets Windows 10/11 (64-bit). You do not need to install Java or VLC.

### Step 1 — Run the installer

Double-click the `.msi` file and follow the wizard. The directory chooser is enabled, so you can pick the installation folder during setup. A Start Menu entry and a desktop shortcut are created automatically.

### Step 2 — The SmartScreen warning ("Windows protected your PC")

Because NP Desktop is a free-software project without a code-signing (EV) certificate, the installer is **not signed**. Windows SmartScreen flags unsigned installers that have not yet built up download reputation. This is a **reputation filter, not a virus detection** — it means Windows does not yet know the file, not that it found malware.

When you see the dialog:

1. Click **More info**.
2. Click **Run anyway**.

Alternative (if you prefer to unblock the file first):

- Right-click the `.msi` → **Properties** → tick **Unblock** → **OK**, or
- In PowerShell: `Unblock-File -Path .\NP-Desktop-<version>.msi`

then run the installer.

### Step 3 — If your antivirus flags the file

Unsigned installers are occasionally matched by generic signatures such as `Trojan:Win32/Wacatac`-style detections. If that happens:

1. **Do not disable Microsoft Defender** — never turn off your antivirus to install software.
2. Verify the file's SHA-256 against the release checksum file (see above).
3. If the hash matches, this is a false positive: report it to your antivirus vendor (for Defender, you can submit the file via [Microsoft's sample submission page](https://www.microsoft.com/en-us/wdsi/filesubmission)) and restore/quarantine-release the file yourself. You may add an exclusion **only for the exact file you verified by hash**.

## Linux Installation (Debian/Ubuntu)

**Prerequisites:** a Debian-based distribution on amd64. The package bundles its own JRE and VLC libraries.

```bash
sudo apt install ./np-desktop_<version>_amd64.deb
```

If you prefer the two-step variant:

```bash
sudo dpkg -i np-desktop_<version>_amd64.deb
sudo apt -f install   # installs any missing dependencies
```

### Launch

Launch **NP Desktop** from your applications menu (menu group "NP Desktop"). If you prefer the terminal, the binary is installed under `/opt/` (typically `/opt/NP-Desktop/bin/`).

The deb package maintainer is `NewPipe e.V. <https://newpipe-ev.de>`.

## Troubleshooting

### "Failed to launch JVM" on Windows

The packaged application bundles every JDK module specifically to prevent this error, so it should not occur. If you see it, download the installer again, verify the checksum, and reinstall. If the problem persists, open an issue on the [issue tracker](https://github.com/Tauseef666-ctrl/NP-Desktop/issues).

### SmartScreen keeps blocking after "Unblock"

Re-download the installer from the official [Releases page](https://github.com/Tauseef666-ctrl/NP-Desktop/releases), verify the SHA-256 checksum, and unblock the freshly downloaded file. If a copy came from anywhere else, do not use it.

### Antivirus quarantined the installer

Verify the SHA-256 hash of the original download against the release checksum. If it matches, restore the file from quarantine and add an exclusion **for that verified file only** — not for a folder or for all unsigned installers. Then consider reporting the false positive to your antivirus vendor.

## Build From Source

### Prerequisites

- **JDK 21** — required. The project modules use `jvmToolchain(21)`, so Gradle needs a JDK 21 toolchain (Gradle can download one automatically if configured).
- Git
- Android SDK (only for the Android builds)

### Build commands

**Windows:**

```bash
git clone https://github.com/Tauseef666-ctrl/NP-Desktop.git
cd NP-Desktop
gradlew.bat --no-configuration-cache :desktopApp:packageMsi
```

**Linux:**

```bash
git clone https://github.com/Tauseef666-ctrl/NP-Desktop.git
cd NP-Desktop
./gradlew --no-configuration-cache :desktopApp:packageDeb
```

The installer/package is written to `desktopApp/build/compose/binaries/`.
