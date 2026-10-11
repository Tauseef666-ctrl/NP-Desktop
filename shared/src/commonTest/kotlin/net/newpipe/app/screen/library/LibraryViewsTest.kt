/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.player.LocalMediaFile
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.RecentlyPlayedEntry
import net.newpipe.app.player.ResumeEntry
import net.newpipe.app.player.SavedPlaylist

class LibraryViewsTest {

    private fun resume(
        id: String,
        positionMs: Long,
        durationMs: Long = 120_000L,
        updatedAt: Long = 0L,
        title: String = ""
    ) = ResumeEntry(
        id = id,
        title = title,
        url = "file://$id",
        positionMs = positionMs,
        updatedAtEpochMs = updatedAt,
        durationMs = durationMs
    )

    private fun task(
        id: String,
        status: DownloadState,
        title: String = "Task $id",
        filePath: String? = "/downloads/$id"
    ) = DownloadTask(
        request = DownloadRequest(
            id = id,
            url = "https://example.org/$id",
            destinationPath = "/downloads/$id",
            title = title
        ),
        status = status,
        filePath = filePath
    )

    @Test
    fun continueWatchingDropsFinishedAndNearEnd() {
        val entries = listOf(
            resume("fresh", positionMs = 30_000L, durationMs = 120_000L),
            resume("finished", positionMs = 120_000L, durationMs = 120_000L),
            resume("nearEnd", positionMs = 115_000L, durationMs = 120_000L),
            resume("zero", positionMs = 0L, durationMs = 120_000L)
        )
        val result = continueWatching(entries, nowEpochMs = 1_000_000L)
        assertEquals(listOf("fresh"), result.map { it.id })
    }

    @Test
    fun continueWatchingKeepsUnknownDurationWhenStarted() {
        val result = continueWatching(
            listOf(resume("live", positionMs = 5_000L, durationMs = 0L)),
            nowEpochMs = 1_000_000L
        )
        assertEquals(listOf("live"), result.map { it.id })
    }

    @Test
    fun continueWatchingSortsByUpdatedAtDescending() {
        val entries = listOf(
            resume("old", positionMs = 1_000L, updatedAt = 100L),
            resume("new", positionMs = 1_000L, updatedAt = 300L),
            resume("mid", positionMs = 1_000L, updatedAt = 200L)
        )
        val result = continueWatching(entries, nowEpochMs = 10_000L)
        assertEquals(listOf("new", "mid", "old"), result.map { it.id })
    }

    @Test
    fun continueWatchingCarriesResumePositionAndKind() {
        val result = continueWatching(
            listOf(resume("a", positionMs = 42_000L, updatedAt = 1L)),
            nowEpochMs = 10_000L
        ).single()
        assertEquals(42_000L, result.playablePositionMs)
        assertEquals(KIND.CONTINUE, result.kind)
    }

    @Test
    fun continueWatchingFallsBackToIdWhenTitleMissing() {
        val result = continueWatching(
            listOf(resume("no-title", positionMs = 1_000L)),
            nowEpochMs = 10_000L
        ).single()
        assertEquals("no-title", result.title)
    }

    @Test
    fun recentlyPlayedSortsNewestFirst() {
        val entries = listOf(
            RecentlyPlayedEntry("a", playedAtEpochMs = 100L),
            RecentlyPlayedEntry("b", playedAtEpochMs = 300L),
            RecentlyPlayedEntry("c", playedAtEpochMs = 200L)
        )
        val result = recentlyPlayed(entries)
        assertEquals(listOf("b", "c", "a"), result.map { it.id })
        assertTrue(result.all { it.kind == KIND.RECENT })
    }

    @Test
    fun recentlyPlayedFallsBackToIdAndCarriesUrl() {
        val result = recentlyPlayed(
            listOf(RecentlyPlayedEntry("id-only", playedAtEpochMs = 1L))
        ).single()
        assertEquals("id-only", result.title)
        assertEquals(0L, result.playablePositionMs)
    }

    @Test
    fun downloadedTasksFilterToCompletedNewestFirst() {
        val tasks = listOf(
            task("first", DownloadState.COMPLETED),
            task("second", DownloadState.DOWNLOADING),
            task("third", DownloadState.COMPLETED),
            task("fourth", DownloadState.FAILED)
        )
        val result = downloadedTasks(tasks)
        assertEquals(listOf("third", "first"), result.map { it.id })
        assertTrue(result.all { it.kind == KIND.DOWNLOADED })
    }

    @Test
    fun downloadedTasksUseFilePathAndFallBackToDestination() {
        val withPath = task("a", DownloadState.COMPLETED, filePath = "/downloads/a.mp4")
        val withoutPath = task("b", DownloadState.COMPLETED, filePath = null)
        val result = downloadedTasks(listOf(withPath, withoutPath))
        assertEquals(listOf("b", "a"), result.map { it.id })
        assertEquals("/downloads/a.mp4", result.first { it.id == "a" }.url)
        assertEquals("/downloads/b", result.first { it.id == "b" }.url)
    }

    private fun localMedia(
        id: String,
        path: String,
        name: String = id,
        extension: String = "mp4",
        mimeType: String = "video/mp4",
        url: String = "file://$path",
        sizeBytes: Long = 1000L,
        audioOnly: Boolean = false
    ) = LocalMediaFile(
        id = id,
        path = path,
        name = name,
        extension = extension,
        mimeType = mimeType,
        url = url,
        sizeBytes = sizeBytes,
        modifiedAt = 0L,
        durationMs = null,
        audioOnly = audioOnly
    )

    @Test
    fun audioTracksFiltersAndSorts() {
        val tracks = listOf(
            localMedia("b", "/music/b.mp3", name = "b.mp3", extension = "mp3", audioOnly = true),
            localMedia("a", "/music/a.mp3", name = "a.mp3", extension = "mp3", audioOnly = true),
            localMedia("v", "/video/v.mp4", name = "v.mp4", audioOnly = false)
        )
        val result = audioTracks(tracks)
        assertEquals(listOf("a", "b"), result.map { it.id })
        assertTrue(result.all { it.kind == KIND.LOCAL })
    }

    @Test
    fun videoTracksFiltersAndSorts() {
        val tracks = listOf(
            localMedia("b", "/video/b.mp4", name = "b.mp4", audioOnly = false),
            localMedia("a", "/video/a.mp4", name = "a.mp4", audioOnly = false),
            localMedia("m", "/music/m.mp3", audioOnly = true)
        )
        val result = videoTracks(tracks)
        assertEquals(listOf("a", "b"), result.map { it.id })
        assertTrue(result.all { it.kind == KIND.LOCAL })
    }

    @Test
    fun folderGroupsGroupsByParentAndFallsBackToRoot() {
        val tracks = listOf(
            localMedia("v1", "/Videos/movie.mp4", name = "movie.mp4"),
            localMedia("v2", "/Videos/clip.mp4", name = "clip.mp4"),
            localMedia("m1", "/Music/song.mp3", name = "song.mp3", extension = "mp3", audioOnly = true),
            localMedia("r1", "/rootfile.mp4", name = "rootfile.mp4"),
            localMedia("r2", "root2.mp4", name = "root2.mp4")
        )
        val groups = folderGroups(tracks)
        // Should have 3 groups
        assertEquals(3, groups.size)
        // Find root group
        val rootGroup = groups.find { it.folderName == "Root" }
        assertTrue(rootGroup != null)
        assertEquals(listOf("root2.mp4", "rootfile.mp4"), rootGroup!!.items.map { it.title })
    }

    @Test
    fun folderGroupsSortsFoldersAndItems() {
        val tracks = listOf(
            localMedia("b", "/z/b.mp4", name = "b.mp4"),
            localMedia("a", "/z/a.mp4", name = "a.mp4"),
            localMedia("m", "/a/m.mp4", name = "m.mp4")
        )
        val groups = folderGroups(tracks)
        assertEquals(listOf("a", "z"), groups.map { it.folderName })
        assertEquals(listOf("m.mp4"), groups[0].items.map { it.title })
        assertEquals(listOf("a.mp4", "b.mp4"), groups[1].items.map { it.title })
    }

    @Test
    fun playlistTracksMapsCorrectly() {
        val playlists = listOf(
            SavedPlaylist(id = "p1", name = "Favorites", items = listOf(MediaItem("1", "t1", "url1"))),
            SavedPlaylist(id = "p2", name = "rock", items = listOf(MediaItem("2", "t2", "url2"), MediaItem("3", "t3", "url3")))
        )
        val result = playlistTracks(playlists)
        assertEquals(listOf("Favorites", "rock"), result.map { it.title })
        assertEquals("2 items", result.first { it.id == "p2" }.subtitle)
        assertEquals("1 item", result.first { it.id == "p1" }.subtitle)
        assertTrue(result.all { it.kind == KIND.LOCAL })
    }

    @Test
    fun emptyInputsYieldEmptyViews() {
        assertTrue(continueWatching(emptyList(), nowEpochMs = 0L).isEmpty())
        assertTrue(recentlyPlayed(emptyList()).isEmpty())
        assertTrue(downloadedTasks(emptyList()).isEmpty())
        assertTrue(audioTracks(emptyList()).isEmpty())
        assertTrue(videoTracks(emptyList()).isEmpty())
        assertTrue(folderGroups(emptyList()).isEmpty())
        assertTrue(playlistTracks(emptyList()).isEmpty())
    }
}
