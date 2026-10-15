/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.download

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** Lifecycle of a download mission, independent of any UI. */
enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

/**
 * A request to download one URL to a local file.
 *
 * @param id Stable identity used to address the mission (also the resume key).
 * @param destinationPath Final absolute file path (the engine writes a sibling `.part` file while
 *   in progress and renames it on success).
 * @param format Requested quality label, recorded as metadata only (the engine downloads the raw
 *   stream and never re-encodes). `null` means "not recorded".
 * @param subtitleUrl Optional direct URL of a subtitle document to download as a real companion file
 *   next to the main file (see [subtitleDestinationPath]); `null` means "no subtitle requested".
 * @param subtitleLanguageTag Optional language tag of the companion subtitle, used to name the
 *   companion file (for example `en`); `null` falls back to `subtitle`.
 */
@Serializable
data class DownloadRequest(
    val id: String,
    val url: String,
    val destinationPath: String,
    val title: String = "",
    val mimeType: String? = null,
    val expectedSizeBytes: Long = 0L,
    val format: String? = null,
    val subtitleUrl: String? = null,
    val subtitleLanguageTag: String? = null
)

/**
 * Immutable snapshot of a mission. [totalBytes] is `0` until the server reports it; [progress] is
 * therefore `0` when the size is unknown (never faked).
 */
@Serializable
data class DownloadTask(
    val request: DownloadRequest,
    val status: DownloadState = DownloadState.QUEUED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val filePath: String? = null,
    val error: String? = null
) {
    val id: String get() = request.id

    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val isTerminal: Boolean
        get() = status == DownloadState.COMPLETED || status == DownloadState.FAILED
}

/**
 * Platform-independent download manager. Implementations perform real, verifiable transfers:
 * a mission is only ever marked [DownloadState.COMPLETED] after the final file exists on disk.
 */
interface DownloadEngine {

    val isAvailable: Boolean

    /** All missions, in insertion order. */
    val tasks: StateFlow<List<DownloadTask>>

    /** Register a mission (status [DownloadState.QUEUED]); returns the created task. */
    fun enqueue(request: DownloadRequest): DownloadTask

    /** Begin/resume downloading [id]. No-op if already active. */
    fun start(id: String)

    /** Stop [id], keeping the partial `.part` file for a later resume. */
    fun pause(id: String)

    /** Stop [id], delete its partial file and drop the mission. */
    fun cancel(id: String)

    /** Drop a finished/failed mission from the list (does not touch the downloaded file). */
    fun remove(id: String)

    /** Drop all completed missions from the list. */
    fun clearCompleted()
}
