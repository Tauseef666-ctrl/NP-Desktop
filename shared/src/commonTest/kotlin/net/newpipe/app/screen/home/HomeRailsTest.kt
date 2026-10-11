/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.RecentlyPlayedEntry
import net.newpipe.app.player.ResumeEntry
import net.newpipe.app.player.SavedPlaylist

class HomeRailsTest {

    // --- continueWatchingItems ---

    @Test
    fun continueWatchingSortsNewestFirstAndPopulatesTitleUrlSubtitle() {
        val items = continueWatchingItems(
            listOf(
                ResumeEntry(id = "a", title = "", url = "file:///a", positionMs = 12_000L, updatedAtEpochMs = 2L),
                ResumeEntry(id = "b", title = "Bee", url = "file:///b", positionMs = 30_000L, updatedAtEpochMs = 1L)
            )
        )

        assertEquals(listOf("a", "b"), items.map { it.id })
        assertEquals("a", items[0].title, "blank title must fall back to the id")
        assertEquals("Bee", items[1].title)
        assertEquals("file:///a", items[0].url)
        assertEquals("0:12", items[0].subtitle)
        assertEquals("0:30", items[1].subtitle)
    }

    @Test
    fun continueWatchingTogetherShowsPositionOverDuration() {
        val items = continueWatchingItems(
            listOf(
                ResumeEntry(id = "a", title = "A", positionMs = 60_000L, durationMs = 120_000L, updatedAtEpochMs = 2L)
            )
        )

        assertEquals("1:00 / 2:00", items.single().subtitle)
    }

    @Test
    fun continueWatchingDropsZeroPositionAndNearEndEntries() {
        val items = continueWatchingItems(
            listOf(
                ResumeEntry(id = "zero", positionMs = 0L, durationMs = 90_000L, updatedAtEpochMs = 9L),
                ResumeEntry(id = "finished", positionMs = 85_000L, durationMs = 90_000L, updatedAtEpochMs = 8L),
                ResumeEntry(id = "ok", positionMs = 5_000L, durationMs = 90_000L, updatedAtEpochMs = 7L)
            )
        )

        assertEquals(listOf("ok"), items.map { it.id })
    }

    // --- recentlyPlayedItems ---

    @Test
    fun recentlyPlayedSortsNewestFirstAndFallsBackBlankTitle() {
        val items = recentlyPlayedItems(
            listOf(
                RecentlyPlayedEntry(id = "x", title = "", url = "file:///x", durationMs = 0L, playedAtEpochMs = 1L),
                RecentlyPlayedEntry(id = "y", title = "Why", url = "file:///y", durationMs = 120_000L, playedAtEpochMs = 5L)
            )
        )

        assertEquals(listOf("y", "x"), items.map { it.id })
        assertEquals("Why", items[0].title)
        assertEquals("file:///y", items[0].url)
        assertEquals("2:00", items[0].subtitle)
        assertEquals("x", items[1].title, "blank title must fall back to the id")
        assertEquals(null, items[1].subtitle, "unknown durations produce no subtitle")
    }

    // --- downloadsItems (COMPLETED only) ---

    @Test
    fun downloadsKeepsOnlyCompletedAndOrdersNewestFirst() {
        val items = downloadsItems(
            listOf(
                task("c1", DownloadState.COMPLETED, title = "Done", filePath = "/tmp/c1.mp4"),
                task("q", DownloadState.QUEUED, title = "Queued"),
                task("d", DownloadState.DOWNLOADING, title = "Active"),
                task("p", DownloadState.PAUSED, title = "Paused"),
                task("f", DownloadState.FAILED, title = "Failed"),
                task("c2", DownloadState.COMPLETED, title = "")
            )
        )

        assertEquals(listOf("c2", "c1"), items.map { it.id })
        assertEquals("c2", items[0].title, "blank title must fall back to the id")
        assertEquals("/tmp/c2", items[0].url, "missing filePath falls back to destinationPath")
        assertEquals("Done", items[1].title)
        assertEquals("/tmp/c1.mp4", items[1].url)
    }

    @Test
    fun downloadsWithNoCompletedMissionsIsEmpty() {
        assertTrue(
            downloadsItems(
                listOf(
                    task("q", DownloadState.QUEUED),
                    task("d", DownloadState.DOWNLOADING),
                    task("p", DownloadState.PAUSED),
                    task("f", DownloadState.FAILED)
                )
            ).isEmpty()
        )
    }

    // --- playlistsItems ---

    @Test
    fun playlistsSortByNameAndUseFirstTrackUrl() {
        val items = playlistsItems(
            listOf(
                SavedPlaylist(
                    id = "b",
                    name = "Beta",
                    items = listOf(MediaItem(id = "m1", title = "T1", url = "file:///m1"))
                ),
                SavedPlaylist(
                    id = "a",
                    name = "Alpha",
                    items = listOf(
                        MediaItem(id = "m2", title = "T2", url = "file:///m2"),
                        MediaItem(id = "m3", title = "T3", url = "file:///m3")
                    )
                )
            )
        )

        assertEquals(listOf("a", "b"), items.map { it.id })
        assertEquals("Alpha", items[0].title)
        assertEquals("2 items", items[0].subtitle)
        assertEquals("file:///m2", items[0].url, "play starts from the playlist's first track")
        assertEquals("1 item", items[1].subtitle)
    }

    @Test
    fun playlistsBlankNameFallsBackToIdAndBlankFirstTrackToNoUrl() {
        val items = playlistsItems(
            listOf(
                SavedPlaylist(id = "z", name = "", items = listOf(MediaItem(id = "m", title = "M", url = "file:///m"))),
                SavedPlaylist(id = "empty", name = "Empty", items = emptyList())
            )
        )

        assertEquals("z", items[0].title, "blank name must fall back to the id")
        assertEquals("file:///m", items[0].url)
        assertEquals("", items[1].url, "a playlist without tracks has nothing playable")
    }

    // --- buildHomeSections ---

    @Test
    fun buildHomeSectionsOmitsEmptyRailsInOrder() {
        val resume = listOf(ResumeEntry(id = "r", positionMs = 5_000L, updatedAtEpochMs = 1L))
        val recent = listOf(RecentlyPlayedEntry(id = "p", playedAtEpochMs = 1L))
        val downloads = listOf(task("c", DownloadState.COMPLETED, title = "C"))
        val playlists = listOf(SavedPlaylist(id = "pl", name = "Mix", items = listOf(MediaItem(id = "m", title = "M", url = "file:///m"))))

        val sections = buildHomeSections(resume, recent, downloads, playlists)

        assertEquals(
            listOf(
                HomeRailType.CONTINUE_WATCHING,
                HomeRailType.RECENTLY_PLAYED,
                HomeRailType.DOWNLOADS,
                HomeRailType.PLAYLISTS
            ),
            sections.map { it.type }
        )
    }

    @Test
    fun buildHomeSectionsDropsEmptySourcesWhileKeepingOthers() {
        val sections = buildHomeSections(
            resume = emptyList(),
            recent = listOf(RecentlyPlayedEntry(id = "p", playedAtEpochMs = 1L)),
            downloads = emptyList(),
            playlists = listOf(SavedPlaylist(id = "pl", name = "Mix", items = listOf(MediaItem(id = "m", title = "M", url = "file:///m"))))
        )

        assertEquals(
            listOf(HomeRailType.RECENTLY_PLAYED, HomeRailType.PLAYLISTS),
            sections.map { it.type }
        )
    }

    @Test
    fun buildHomeSectionsAllEmptyIsEmpty() {
        assertTrue(buildHomeSections(emptyList(), emptyList(), emptyList(), emptyList()).isEmpty())
    }

    private fun task(
        id: String,
        status: DownloadState,
        title: String = "",
        filePath: String? = null
    ) = DownloadTask(
        request = DownloadRequest(id = id, url = "https://example.org/$id", destinationPath = "/tmp/$id", title = title),
        status = status,
        filePath = filePath
    )
}