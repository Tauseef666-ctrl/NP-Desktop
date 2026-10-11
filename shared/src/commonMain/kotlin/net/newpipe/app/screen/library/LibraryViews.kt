/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.library

import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.player.RecentlyPlayedEntry
import net.newpipe.app.player.ResumeEntry
import net.newpipe.app.util.formatDuration

/** Which derived source a [DerivedViewItem] came from (used for icons/labels only). */
enum class KIND { RECENT, CONTINUE, DOWNLOADED, LOCAL }

/** The tabs shown above the library list. */
enum class LibraryViewTab { LOCAL, RECENT, CONTINUE, DOWNLOADED }

/**
 * A single row rendered by one of the derived library views. Pure presentation data: [url] is the
 * engine-playable location and [playablePositionMs] is where playback should resume (`0` = start).
 */
data class DerivedViewItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val playablePositionMs: Long = 0L,
    val kind: KIND
)

/** Positions closer than this to the end count as finished, matching the resume store. */
private const val NEAR_END_MS = 8_000L

/**
 * Continue watching: media that has started but not finished, newest first. Entries at/near the end
 * (or with no positive position) are dropped rather than presented as resumable. [nowEpochMs] is
 * used only for an honest relative "last played" note in the subtitle.
 */
fun continueWatching(resumeEntries: List<ResumeEntry>, nowEpochMs: Long): List<DerivedViewItem> =
    resumeEntries
        .filter { it.positionMs > 0L }
        .filter { it.durationMs <= 0L || it.positionMs < it.durationMs - NEAR_END_MS }
        .sortedByDescending { it.updatedAtEpochMs }
        .map { entry ->
            DerivedViewItem(
                id = entry.id,
                title = entry.title.ifBlank { entry.id },
                subtitle = continueSubtitle(entry, nowEpochMs),
                url = entry.url,
                playablePositionMs = entry.positionMs,
                kind = KIND.CONTINUE
            )
        }

/** Recently played: the stored history, newest first. */
fun recentlyPlayed(recentEntries: List<RecentlyPlayedEntry>): List<DerivedViewItem> =
    recentEntries
        .sortedByDescending { it.playedAtEpochMs }
        .map { entry ->
            DerivedViewItem(
                id = entry.id,
                title = entry.title.ifBlank { entry.id },
                subtitle = durationSubtitle(entry.durationMs),
                url = entry.url,
                playablePositionMs = 0L,
                kind = KIND.RECENT
            )
        }

/**
 * Downloaded: completed missions only, newest first. The tasks list is in insertion order, so the
 * most recently added completed mission is the last one — reversing yields newest first.
 */
fun downloadedTasks(tasks: List<DownloadTask>): List<DerivedViewItem> =
    tasks
        .filter { it.status == DownloadState.COMPLETED }
        .reversed()
        .map { task ->
            val path = task.filePath ?: task.request.destinationPath
            DerivedViewItem(
                id = task.id,
                title = task.request.title.ifBlank { task.request.url },
                subtitle = path,
                url = path,
                playablePositionMs = 0L,
                kind = KIND.DOWNLOADED
            )
        }

private fun continueSubtitle(entry: ResumeEntry, nowEpochMs: Long): String {
    val progress = if (entry.durationMs > 0L) {
        "${formatDuration(entry.positionMs / 1000)} / ${formatDuration(entry.durationMs / 1000)}"
    } else {
        formatDuration(entry.positionMs / 1000)
    }
    val age = relativeAge(nowEpochMs, entry.updatedAtEpochMs)
    return if (age == null) progress else "$progress • $age"
}

private fun durationSubtitle(durationMs: Long): String =
    if (durationMs > 0L) formatDuration(durationMs / 1000) else ""

/** Coarse, honest "time since" label; null when there is nothing trustworthy to show. */
private fun relativeAge(nowEpochMs: Long, thenEpochMs: Long): String? {
    if (thenEpochMs <= 0L || nowEpochMs <= thenEpochMs) return null
    val minutes = (nowEpochMs - thenEpochMs) / 60_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 24L * 60L -> "${minutes / 60L}h ago"
        else -> "${minutes / (24L * 60L)}d ago"
    }
}
