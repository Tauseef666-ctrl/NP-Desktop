/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.newpipe.app.platform.UpdateInfo
import net.newpipe.app.platform.UpdateProgress
import net.newpipe.app.platform.UpdateStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The in-app updater of the desktop build.
 *
 * The latest GitHub release of the fork is looked up, the bundled Windows installer (`msi`) is
 * downloaded into the temporary folder and started elevated through `msiexec`. The elevation with
 * the User Account Control prompt is expected: every dialog tells the user about it before the
 * installer is started. All failures stay inside the status flows or the logs, so the background
 * check can never take the startup down; the dialogs are only driven by the status values.
 */
object UpdateService {

    private const val RELEASE_URL =
        "https://api.github.com/repos/Tauseef666-ctrl/NP-Desktop/releases/latest"
    private const val RELEASE_ACCEPT = "application/vnd.github+json"
    private const val INSTALLER_PREFIX = "NP-Desktop-"
    private const val INSTALLER_SUFFIX = ".msi"
    private const val CHECKSUM_SUFFIX = "checksums-windows-sha256.txt"
    private const val USER_AGENT_FALLBACK = "NP-Desktop"

    /** Bytes copied between two progress emissions (16 KiB keeps the UI smooth). */
    private const val PROGRESS_CHUNK = 16 * 1024

    private val updaterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The version of the running desktop build that the desktop module passes in at startup,
     * for example `0.25.6`; empty when the app runs unpackaged (development launches).
     */
    @Volatile
    var appVersion: String = ""
        private set

    /** Whether the app starts with a packaged version that updates are comparable against. */
    val isVersionKnown: Boolean
        get() = appVersion.isNotBlank()

    val status: MutableStateFlow<UpdateStatus> = MutableStateFlow(UpdateStatus.Idle)

    val progress: MutableStateFlow<UpdateProgress?> = MutableStateFlow(null)

    /** Whether a release lookup is running, so concurrent checks cannot interleave. */
    private val checking = AtomicBoolean(false)

    /** Whether the download with the following installer start is running. */
    private val installing = AtomicBoolean(false)

    /**
     * Hands the version of the packaged app into the updater; the desktop entry point calls it
     * with the constant of `DesktopVersions.kt` before the UI starts.
     */
    fun initialize(version: String) {
        if (appVersion.isBlank() && version.isNotBlank()) {
            appVersion = version.trim()
        }
    }

    /**
     * Looks up the latest GitHub release and compares its version against the running one. The
     * result is stored in [status] and also returned, so callers can react on the outcome.
     */
    suspend fun checkForUpdate(): UpdateStatus {
        if (!isVersionKnown) {
            Logger.w(messageString = "The updater cannot check without a known app version")
            return UpdateStatus.Error("The current version is unknown")
        }
        // A second check while one is running returns the state the running check will settle into
        if (!checking.compareAndSet(false, true)) {
            return status.value
        }
        status.value = UpdateStatus.Checking
        progress.value = null
        runCatching { lookupLatestRelease() }
            .fold(
                onSuccess = { result -> updateStatus(result) },
                onFailure = { failure ->
                    Logger.w(messageString = "The update check failed", throwable = failure)
                    updateStatus(UpdateStatus.Error(describe(failure)))
                }
            )
        checking.set(false)
        return status.value
    }

    /**
     * Downloads the installer of [info] into the temporary folder and starts the elevated
     * Windows installer. Runs asynchronously; the status flow follows every step.
     */
    fun downloadInstall(info: UpdateInfo) {
        if (!installing.compareAndSet(false, true)) {
            Logger.w(messageString = "An update is already being installed")
            return
        }
        updaterScope.launch {
            runCatching { downloadAndLaunch(info) }
                .onFailure { failure ->
                    Logger.w(messageString = "The update installation failed", throwable = failure)
                    updateStatus(UpdateStatus.UpdateFailed(describe(failure)))
                }
            installing.set(false)
        }
    }

    /**
     * Replaces the current status, used by the platform wiring to open the update dialog after a
     * successful startup check or to close it again.
     */
    fun announce(next: UpdateStatus) {
        status.value = next
    }

    /**
     * Closes every open dialog by returning to [UpdateStatus.Idle].
     */
    fun reset() {
        status.value = UpdateStatus.Idle
    }

    /**
     * Fetches the latest release and derives the updater state from its metadata.
     */
    private suspend fun lookupLatestRelease(): UpdateStatus = withContext(Dispatchers.IO) {
        val body = fetchReleaseBody(RELEASE_URL)
            ?: throw IOException("The release information could not be read")
        val release = json.parseToJsonElement(body).jsonObject
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (tag.isEmpty()) {
            throw IOException("The release has no version tag")
        }
        val version = tag.removePrefix("v").removePrefix("V")
        val assets = release["assets"] as? JsonArray ?: JsonArray(emptyList())
        val installerUrl = installerAssetUrl(assets)
        val checksumUrl = checksumAssetUrl(assets)
        val releaseUrl = release["html_url"]?.jsonPrimitive?.contentOrNull

        when {
            // Same or older version: the user is already up to date, nothing to announce
            !isNewer(version, appVersion) -> UpdateStatus.UpToDate
            installerUrl == null -> UpdateStatus.NotAvailable
            else -> UpdateStatus.Available(
                UpdateInfo(
                    version = version,
                    releaseUrl = releaseUrl,
                    installerUrl = installerUrl,
                    checksumUrl = checksumUrl
                )
            )
        }
    }

    /**
     * Reads the release endpoint with the headers GitHub requires: [RELEASE_ACCEPT] and a
     * `User-Agent`, without which every request is rejected.
     */
    private fun fetchReleaseBody(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", RELEASE_ACCEPT)
            .header("User-Agent", userAgent())
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> return response.body?.string()
                response.code == 403 -> throw IOException(
                    "GitHub rejected the update check, likely because the rate limit is exhausted"
                )
                else -> throw IOException("The release lookup failed with HTTP ${response.code}")
            }
        }
    }

    /**
     * Finds the Windows installer asset of the release. Only the project's own installer names
     * are accepted (`NP-Desktop-<version>.msi`) — an arbitrary `.msi` asset of some other chore
     * must never be executed on the user's machine.
     */
    private fun installerAssetUrl(assets: JsonArray): String? =
        assets.asSequence()
            .map { element -> runCatching { element.jsonObject }.getOrNull() }
            .filterNotNull()
            .firstOrNull { asset ->
                val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: ""
                name.startsWith(INSTALLER_PREFIX, ignoreCase = true) &&
                    name.endsWith(INSTALLER_SUFFIX, ignoreCase = true)
            }
            ?.get("browser_download_url")
            ?.jsonPrimitive
            ?.contentOrNull

    /**
     * Finds the SHA-256 checksum file of the release, which the release workflow publishes
     * alongside the installer. The updater refuses to install without it.
     */
    private fun checksumAssetUrl(assets: JsonArray): String? =
        assets.asSequence()
            .map { element -> runCatching { element.jsonObject }.getOrNull() }
            .filterNotNull()
            .firstOrNull { asset ->
                (asset["name"]?.jsonPrimitive?.contentOrNull ?: "")
                    .endsWith(CHECKSUM_SUFFIX, ignoreCase = true)
            }
            ?.get("browser_download_url")
            ?.jsonPrimitive
            ?.contentOrNull

    /**
     * Downloads the installer into the temporary directory, publishes the byte progress and then
     * starts the elevated installer, keeping the app open in the meanwhile.
     */
    private suspend fun downloadAndLaunch(info: UpdateInfo) = withContext(Dispatchers.IO) {
        val target = installerTarget(info)
        status.value = UpdateStatus.Downloading
        progress.value = null
        val request = Request.Builder()
            .url(info.installerUrl)
            .header("User-Agent", userAgent())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("The installer could not be downloaded (HTTP ${response.code})")
            }
            val body = response.body ?: throw IOException("The installer response was empty")
            val contentLength = body.contentLength().takeIf { length -> length >= 0 }
            target.delete() // remove a stale download of an earlier, interrupted attempt
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(PROGRESS_CHUNK)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        progress.value = UpdateProgress(copied, contentLength)
                    }
                }
            }
        }

        status.value = UpdateStatus.Installing
        verifyChecksum(target, info.checksumUrl)
        launchElevatedInstaller(target)
        updateStatus(UpdateStatus.Installed(target.absolutePath))
    }

    /**
     * Verifies the downloaded installer against the SHA-256 checksum file the release
     * publishes, before the installer is ever started. A mismatch, a missing checksum file or
     * a missing entry for the installer aborts the installation and deletes the download — an
     * unverified installer must never run elevated on the user's machine.
     */
    private fun verifyChecksum(installer: File, checksumUrl: String?) {
        val expected = checksumUrl?.let { url -> expectedChecksumOf(installer.name, url) }
            ?: throw IOException(
                "The update was aborted: the release publishes no SHA-256 checksums file, " +
                    "so the installer cannot be verified"
            )
        val actual = installer.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(PROGRESS_CHUNK)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
        if (!actual.equals(expected, ignoreCase = true)) {
            installer.delete()
            throw IOException(
                "The update was aborted: the downloaded installer does not match the " +
                    "published checksum (expected $expected, got $actual)"
            )
        }
    }

    /**
     * Fetches the checksum file and returns the published hash of [assetName]; a missing entry
     * is an error, not a free pass.
     */
    private fun expectedChecksumOf(assetName: String, checksumUrl: String): String {
        val request = Request.Builder()
            .url(checksumUrl)
            .header("User-Agent", userAgent())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "The update was aborted: the checksum file could not be downloaded " +
                        "(HTTP ${response.code})"
                )
            }
            val body = response.body?.string().orEmpty()
            val line = body.lineSequence()
                .filter { it.isNotBlank() }
                .firstOrNull { entry ->
                    entry.split(Regex("\\s+")).any { it.equals(assetName, ignoreCase = true) }
                }
                ?: throw IOException(
                    "The update was aborted: the checksum file has no entry for $assetName"
                )
            // Checksum files use the `<hash> <filename>` convention of sha256sum/certutil.
            return line.split(Regex("\\s+")).first { it.matches(Regex("[0-9a-fA-F]{64}")) }
        }
    }

    /**
     * Runs the downloaded MSI elevated through `msiexec` in quiet mode with the final success
     * dialog (`/qn+`): Windows asks for confirmation with the User Account Control prompt, and a
     * declined prompt makes the PowerShell layer exit with a failure code.
     */
    private fun launchElevatedInstaller(installer: File) {
        val path = installer.absolutePath.replace("'", "")
        val script = "Start-Process msiexec -ArgumentList '/i','$path','/qn+','/norestart' -Verb RunAs"
        val process = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { line -> output.appendLine(line) }
        val exit = process.waitFor()
        if (exit != 0) {
            val details = output.toString().trim().takeLast(200)
            throw IOException(
                if (details.isEmpty()) {
                    "The update was cancelled: the User Account Control prompt was declined " +
                        "or the installer could not start"
                } else {
                    "The update was cancelled: the User Account Control prompt was declined " +
                        "or the installer could not start ($details)"
                }
            )
        }
    }

    /**
     * The temporary file the installer is downloaded to, named after the release asset.
     */
    private fun installerTarget(info: UpdateInfo): File {
        val name = info.installerUrl.substringBeforeLast('?')
            .substringAfterLast('/')
            .takeIf { it.endsWith(INSTALLER_SUFFIX, ignoreCase = true) }
            ?: "NP-Desktop-${info.version}.msi"
        return File(tempDirectory(), name)
    }

    /**
     * The user temporary directory of the JVM, reachable without special permissions.
     */
    private fun tempDirectory(): File = File(System.getProperty("java.io.tmpdir") ?: ".")

    /**
     * The `User-Agent` GitHub needs for every API request, versioned when the version is known.
     */
    private fun userAgent(): String = when {
        isVersionKnown -> "$USER_AGENT_FALLBACK/$appVersion"
        else -> USER_AGENT_FALLBACK
    }

    /**
     * Copies [next] into the status flow and clears the download progress when the download
     * phase is over.
     */
    private fun updateStatus(next: UpdateStatus) {
        if (next !is UpdateStatus.Downloading) {
            progress.value = null
        }
        status.value = next
    }

    /**
     * A short user readable reason of a failure, derived from its message.
     */
    private fun describe(failure: Throwable): String =
        failure.message?.takeIf { it.isNotBlank() } ?: failure::class.simpleName ?: "Unknown error"

    /**
     * Numeric comparison of dot separated versions, so `0.9` is older than `0.10` and a tag like
     * `v0.25.6` becomes `0.25.6`. A missing component counts as zero.
     */
    internal fun isNewer(candidate: String, current: String): Boolean {
        val newer = components(candidate)
        val running = components(current)
        for (index in 0 until maxOf(newer.size, running.size)) {
            val left = newer.getOrElse(index) { 0 }
            val right = running.getOrElse(index) { 0 }
            when {
                left > right -> return true
                left < right -> return false
            }
        }
        return false
    }

    /**
     * Numeric components of [version], ignoring any non digit suffix of a component.
     */
    private fun components(version: String): List<Int> = version.trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.')
        .map { component -> component.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
