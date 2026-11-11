/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import co.touchlab.kermit.Logger

/**
 * An update that the platform updater found on the release channel.
 *
 * @param version the release version without the leading `v` of the tag, for example `0.25.6`
 * @param releaseUrl the web page of the release, used for manual downloads
 * @param installerUrl the direct download of the Windows installer asset
 * @param checksumUrl the direct download of the installer's SHA-256 checksum file; the
 * downloaded installer is verified against it before it is ever started
 */
data class UpdateInfo(
    val version: String,
    val releaseUrl: String?,
    val installerUrl: String,
    val checksumUrl: String? = null
)

/**
 * The progress of a running installer download: [totalBytes] is unknown when the server does not
 * send a `Content-Length`.
 */
data class UpdateProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?
)

/**
 * The state machine of the in-app updater.
 */
sealed interface UpdateStatus {

    /** No update process is running. */
    data object Idle : UpdateStatus

    /** A release lookup is running. */
    data object Checking : UpdateStatus

    /** A newer release with a Windows installer is published. */
    data class Available(val update: UpdateInfo) : UpdateStatus

    /** The running version is the newest release. */
    data object UpToDate : UpdateStatus

    /** The latest release does not ship a Windows installer yet. */
    data object NotAvailable : UpdateStatus

    /** An installer download is running; see the sibling progress flow. */
    data object Downloading : UpdateStatus

    /** The installer file is complete and the elevated setup is starting. */
    data object Installing : UpdateStatus

    /** The installer was started; the user finishes the update outside the app. */
    data class Installed(val installerPath: String?) : UpdateStatus

    /** A release lookup failed; the message lists the reason, no dialog is shown. */
    data class Error(val message: String) : UpdateStatus

    /** The download or the installer start failed; the message lists the reason. */
    data class UpdateFailed(val message: String) : UpdateStatus
}

/**
 * Platform-specific actions triggered from the updater UI: the silent startup check, the manual
 * check of the settings screen and the elevated installer run.
 */
interface UpdateActions {

    /** The state of the updater, driving both dialogs and the status lines. */
    val status: StateFlow<UpdateStatus>

    /** The download progress, `null` while no installer is downloading. */
    val progress: StateFlow<UpdateProgress?>

    /**
     * The version of the running desktop build, read from the desktop module build config;
     * `null` when the platform does not report a packaged version.
     */
    val currentVersion: String?

    /**
     * Starts the silent startup check in the background: offline and network errors are ignored,
     * and a release the user skipped earlier is not announced again.
     */
    fun checkOnStartup()

    /**
     * Runs the check from the settings screen: its result is shown in the status line, and an
     * available update opens the update dialog, also when the version was skipped before.
     */
    fun checkManually()

    /**
     * Downloads the announced update and runs the elevated Windows installer. Windows will show
     * the User Account Control prompt, which the dialogs tell the user about before.
     */
    fun install()

    /** Closes the open update prompt without remembering anything. */
    fun dismiss()

    /** Closes the update prompt and does not announce this version again at startup. */
    fun skip()
}

/**
 * Fallback for platforms without an updater implementation: the desktop uses the Windows
 * installer flow, while the other targets show nothing.
 */
internal object NoOpUpdateActions : UpdateActions {

    override val status: StateFlow<UpdateStatus> = MutableStateFlow(UpdateStatus.Idle)

    override val progress: StateFlow<UpdateProgress?> = MutableStateFlow(null)

    override val currentVersion: String? = null

    override fun checkOnStartup() = logUnsupported("checkOnStartup")

    override fun checkManually() = logUnsupported("checkManually")

    override fun install() = logUnsupported("install")

    override fun dismiss() = logUnsupported("dismiss")

    override fun skip() = logUnsupported("skip")

    private fun logUnsupported(action: String) =
        Logger.w(messageString = "Update action '$action' is unsupported on this platform")
}
