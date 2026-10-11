/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.home

import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.player.RecentlyPlayedEntry
import net.newpipe.app.player.ResumeEntry
import net.newpipe.app.player.SavedPlaylist
import net.newpipe.app.util.formatDuration

/** The kinds of real, store-fed rails shown on home. */
enum class HomeRailType { CONTINUE_WATCHING, RECENTLY_PLAYED, DOWNLOADS, PLAYLISTS }

/**
 * One playable tile in a home rail. [title] is always a real value (falls back to [id]); [url] is
 * the engine-playable location, blank when the source never recorded one.
 */
data class HomeRailItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val url: String = ""
)

/** A titled rail of [items] fed by a single persisted source. */
data class HomeSection(
    val type: HomeRailType,
    val items: List<HomeRailItem>
)

/** Positions closer than this to the end count as finished, matching the resume store. */
private const val NEAR_END_MS = 8_000L

/** Continue watching: resumable entries, most recently updated first. Finished/blank are dropped. */
fun continueWatchingItems(resume: List<ResumeEntry>): List<HomeRailItem> =
    resume
        .filter { it.positionMs > 0L }
        .filter { it.durationMs <= 0L || it.positionMs < it.durationMs - NEAR_END_MS }
        .sortedByDescending { it.updatedAtEpochMs }
        .map { entry ->
            HomeRailItem(
                id = entry.id,
                title = entry.title.ifBlank { entry.id },
                subtitle = if (entry.durationMs > 0L) {
                    "${formatDuration(entry.positionMs / 1000)} / ${formatDuration(entry.durationMs / 1000)}"
                } else {
                    formatDuration(entry.positionMs / 1000)
                },
                url = entry.url
            )
        }

/** Recently played: the stored history, newest first. */
fun recentlyPlayedItems(recent: List<RecentlyPlayedEntry>): List<HomeRailItem> =
    recent
        .sortedByDescending { it.playedAtEpochMs }
        .map { entry ->
            HomeRailItem(
                id = entry.id,
                title = entry.title.ifBlank { entry.id },
                subtitle = entry.durationMs.takeIf { it > 0L }?.let { formatDuration(it / 1000) },
                url = entry.url
            )
        }

/**
 * Downloaded: completed missions only. The tasks list is in insertion order, so the most recently
 * added completed mission is last — reversing yields newest first.
 */
fun downloadsItems(tasks: List<DownloadTask>): List<HomeRailItem> =
    tasks
        .filter { it.status == DownloadState.COMPLETED }
        .reversed()
        .map { task ->
            val path = task.filePath ?: task.request.destinationPath
            HomeRailItem(
                id = task.id,
                title = task.request.title.ifBlank { task.id },
                subtitle = path,
                url = path
            )
        }

/** Saved playlists sorted by name; playing starts the first track's url. */
fun playlistsItems(playlists: List<SavedPlaylist>): List<HomeRailItem> =
    playlists
        .sortedBy { it.name.lowercase() }
        .map { playlist ->
            val count = playlist.items.size
            HomeRailItem(
                id = playlist.id,
                title = playlist.name.ifBlank { playlist.id },
                subtitle = if (count == 1) "1 item" else "$count items",
                url = playlist.items.firstOrNull()?.url.orEmpty()
            )
        }

/** Builds only the non-empty rails, in canonical home order. */
fun buildHomeSections(
    resume: List<ResumeEntry>,
    recent: List<RecentlyPlayedEntry>,
    downloads: List<DownloadTask>,
    playlists: List<SavedPlaylist>
): List<HomeSection> {
    val sections = mutableListOf<HomeSection>()
    val continueItems = continueWatchingItems(resume)
    if (continueItems.isNotEmpty()) sections += HomeSection(HomeRailType.CONTINUE_WATCHING, continueItems)
    val recentItems = recentlyPlayedItems(recent)
    if (recentItems.isNotEmpty()) sections += HomeSection(HomeRailType.RECENTLY_PLAYED, recentItems)
    val downloadItems = downloadsItems(downloads)
    if (downloadItems.isNotEmpty()) sections += HomeSection(HomeRailType.DOWNLOADS, downloadItems)
    val playlistItems = playlistsItems(playlists)
    if (playlistItems.isNotEmpty()) sections += HomeSection(HomeRailType.PLAYLISTS, playlistItems)
    return sections
}