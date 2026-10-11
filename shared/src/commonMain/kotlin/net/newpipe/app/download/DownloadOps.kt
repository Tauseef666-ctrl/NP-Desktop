/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.download

/**
 * Requested format/quality for a new download.
 *
 * This is purely descriptive metadata: the desktop engine transfers the raw stream and never
 * re-encodes, so the chosen value is recorded on the [DownloadRequest] but does not change the
 * bytes fetched. Declared order (original → low) is the stable order shown in the UI.
 */
enum class DownloadFormat { ORIGINAL, HIGH, MEDIUM, LOW }

/** Stable, non-localized metadata label recorded on a request for a [DownloadFormat]. */
fun formatLabel(format: DownloadFormat): String = when (format) {
    DownloadFormat.ORIGINAL -> "original"
    DownloadFormat.HIGH -> "high"
    DownloadFormat.MEDIUM -> "medium"
    DownloadFormat.LOW -> "low"
}

/** A mission in one of these states still occupies the URL it points at. */
private val DownloadState.holdsUrl: Boolean
    get() = this == DownloadState.QUEUED ||
        this == DownloadState.DOWNLOADING ||
        this == DownloadState.PAUSED

/** A mission the user may reposition: queued, paused or failed — never an active transfer. */
fun DownloadTask.isReorderable(): Boolean = when (status) {
    DownloadState.QUEUED, DownloadState.PAUSED, DownloadState.FAILED -> true
    DownloadState.DOWNLOADING, DownloadState.COMPLETED -> false
}

private fun normalizeUrl(url: String): String = url.trim().lowercase()

/**
 * True when [url] matches (after trim + lowercase) the URL of a mission that is still active —
 * queued, downloading or paused. Completed, failed and removed missions never block a re-add.
 * A blank [url] is never treated as a duplicate.
 */
fun isDuplicate(tasks: List<DownloadTask>, url: String): Boolean {
    val needle = normalizeUrl(url)
    if (needle.isEmpty()) return false
    return tasks.any { it.status.holdsUrl && normalizeUrl(it.request.url) == needle }
}

/**
 * Moves the mission at [fromIndex] to the position of the mission at [toIndex].
 *
 * Rule: only reorderable missions (queued/paused/failed) participate. Missions that are actively
 * [DownloadState.DOWNLOADING] or already [DownloadState.COMPLETED] are pinned to their exact slot
 * and never move. Both [fromIndex] and [toIndex] must address reorderable missions inside [tasks];
 * otherwise — including equal, reversed-immovable or out-of-bounds indices — the original list is
 * returned unchanged. Among the participating missions order changes as described; pinned missions
 * keep their indices.
 */
fun reorder(tasks: List<DownloadTask>, fromIndex: Int, toIndex: Int): List<DownloadTask> {
    if (fromIndex == toIndex) return tasks
    if (fromIndex !in tasks.indices || toIndex !in tasks.indices) return tasks
    if (!tasks[fromIndex].isReorderable() || !tasks[toIndex].isReorderable()) return tasks

    val movableIndices = tasks.indices.filter { tasks[it].isReorderable() }
    val fromPos = movableIndices.indexOf(fromIndex)
    val toPos = movableIndices.indexOf(toIndex)
    if (fromPos < 0 || toPos < 0) return tasks

    val movable = movableIndices.map { tasks[it] }.toMutableList()
    val moved = movable.removeAt(fromPos)
    movable.add(toPos, moved)

    val result = tasks.toMutableList()
    movableIndices.forEachIndexed { position, index -> result[index] = movable[position] }
    return result
}
