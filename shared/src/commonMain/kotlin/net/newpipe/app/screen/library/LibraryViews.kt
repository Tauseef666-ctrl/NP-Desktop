/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.library

import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.player.LocalMediaFile
import net.newpipe.app.player.RecentlyPlayedEntry
import net.newpipe.app.player.ResumeEntry
import net.newpipe.app.player.SavedPlaylist
import net.newpipe.app.util.formatDuration
import net.newpipe.app.util.formatBytes

/** Which derived source a [DerivedViewItem] came from (used for icons/labels only). */
enum class KIND { RECENT, CONTINUE, DOWNLOADED, LOCAL }

/** The tabs shown above the library list. */
enum class LibraryViewTab { LOCAL, RECENT, CONTINUE, DOWNLOADED, VIDEOS, AUDIO, FOLDERS, PLAYLISTS }

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

/**
 * A group of media items that share the same parent folder.
 */
data class FolderGroup(
    val folderName: String,
    val items: List<DerivedViewItem>
)

/**
 * Filter local media files to audio tracks only (audio-only files).
 */
fun audioTracks(local: List<LocalMediaFile>): List<DerivedViewItem> =
    local
        .filter { it.audioOnly }
        .sortedBy { it.name.lowercase() }
        .map { file ->
            DerivedViewItem(
                id = file.id,
                title = file.name,
                subtitle = buildLocalMediaSubtitle(file),
                url = file.url,
                playablePositionMs = 0L,
                kind = KIND.LOCAL
            )
        }

/**
 * Filter local media files to video tracks only (non audio-only files).
 */
fun videoTracks(local: List<LocalMediaFile>): List<DerivedViewItem> =
    local
        .filter { !it.audioOnly }
        .sortedBy { it.name.lowercase() }
        .map { file ->
            DerivedViewItem(
                id = file.id,
                title = file.name,
                subtitle = buildLocalMediaSubtitle(file),
                url = file.url,
                playablePositionMs = 0L,
                kind = KIND.LOCAL
            )
        }

/**
 * Group all local media by parent directory. Media without a resolvable parent go under
 * the root folder name.
 */
fun folderGroups(local: List<LocalMediaFile>): List<FolderGroup> {
    val groups = mutableMapOf<String, MutableList<DerivedViewItem>>()
    val rootName = "Root"

    local.forEach { file ->
        val folderName = resolveParentFolder(file.path) ?: rootName
        val item = DerivedViewItem(
            id = file.id,
            title = file.name,
            subtitle = buildLocalMediaSubtitle(file),
            url = file.url,
            playablePositionMs = 0L,
            kind = KIND.LOCAL
        )
        groups.getOrPut(folderName) { mutableListOf() }.add(item)
    }

    return groups
        .map { (folderName, items) ->
            FolderGroup(
                folderName = folderName,
                items = items.sortedBy { it.title.lowercase() }
            )
        }
        .sortedBy { it.folderName.lowercase() }
}

/**
 * Convert saved playlists to derived view items (one row per playlist).
 */
fun playlistTracks(saved: List<SavedPlaylist>): List<DerivedViewItem> =
    saved
        .sortedBy { it.name.lowercase() }
        .map { playlist ->
            val count = playlist.items.size
            val subtitle = if (count == 1) "1 item" else "$count items"
            DerivedViewItem(
                id = playlist.id,
                title = playlist.name,
                subtitle = subtitle,
                url = "",
                playablePositionMs = 0L,
                kind = KIND.LOCAL
            )
        }

private fun buildLocalMediaSubtitle(file: LocalMediaFile): String {
    val parts = mutableListOf<String>()
    val sizeText = formatBytes(file.sizeBytes)
    if (sizeText.isNotBlank()) parts.add(sizeText)
    if (file.extension.isNotBlank()) parts.add(file.extension.uppercase())
    return parts.joinToString(" • ")
}

private fun resolveParentFolder(path: String): String? {
    val normalized = path.replace('\\', '/')
    val lastSlash = normalized.lastIndexOf('/')
    if (lastSlash <= 0) return null
    val parent = normalized.substring(0, lastSlash)
    if (parent.isBlank()) return null
    // Return just the folder name for the group
    val parentLastSlash = parent.lastIndexOf('/')
    return if (parentLastSlash >= 0 && parentLastSlash < parent.length - 1) {
        parent.substring(parentLastSlash + 1)
    } else if (parent == "/") {
        null
    } else {
        parent
    }
}
