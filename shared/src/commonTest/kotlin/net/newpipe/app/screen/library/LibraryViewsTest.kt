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
import net.newpipe.app.player.RecentlyPlayedEntry
import net.newpipe.app.player.ResumeEntry

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

    @Test
    fun emptyInputsYieldEmptyViews() {
        assertTrue(continueWatching(emptyList(), nowEpochMs = 0L).isEmpty())
        assertTrue(recentlyPlayed(emptyList()).isEmpty())
        assertTrue(downloadedTasks(emptyList()).isEmpty())
    }
}
