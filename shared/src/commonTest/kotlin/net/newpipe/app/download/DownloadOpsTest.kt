/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadOpsTest {

    private fun task(
        id: String,
        status: DownloadState = DownloadState.QUEUED,
        url: String = "https://example.org/$id"
    ) = DownloadTask(
        request = DownloadRequest(id = id, url = url, destinationPath = "/tmp/$id"),
        status = status
    )

    private fun ids(tasks: List<DownloadTask>): List<String> = tasks.map { it.id }

    // --- isDuplicate ---

    @Test
    fun duplicateDetectsQueuedDownloadingAndPaused() {
        val tasks = listOf(
            task("q", DownloadState.QUEUED, "https://example.org/a"),
            task("d", DownloadState.DOWNLOADING, "https://example.org/b"),
            task("p", DownloadState.PAUSED, "https://example.org/c")
        )
        assertTrue(isDuplicate(tasks, "https://example.org/a"))
        assertTrue(isDuplicate(tasks, "https://example.org/b"))
        assertTrue(isDuplicate(tasks, "https://example.org/c"))
    }

    @Test
    fun completedAndFailedMissionsDoNotBlockReAdd() {
        val tasks = listOf(
            task("c", DownloadState.COMPLETED, "https://example.org/a"),
            task("f", DownloadState.FAILED, "https://example.org/b")
        )
        assertFalse(isDuplicate(tasks, "https://example.org/a"))
        assertFalse(isDuplicate(tasks, "https://example.org/b"))
    }

    @Test
    fun duplicateMatchNormalizesWhitespaceAndCase() {
        val tasks = listOf(task("a", url = "  https://Example.org/Path  "))
        assertTrue(isDuplicate(tasks, "https://example.org/path"))
        assertTrue(isDuplicate(tasks, "HTTPS://EXAMPLE.ORG/PATH"))
    }

    @Test
    fun blankUrlIsNeverADuplicate() {
        val tasks = listOf(task("a", url = ""))
        assertFalse(isDuplicate(tasks, ""))
        assertFalse(isDuplicate(tasks, "   "))
    }

    @Test
    fun nonMatchingUrlIsNotADuplicate() {
        assertFalse(isDuplicate(listOf(task("a", url = "https://example.org/a")), "https://example.org/b"))
    }

    @Test
    fun emptyMissionListHasNoDuplicates() {
        assertFalse(isDuplicate(emptyList(), "https://example.org/a"))
    }

    // --- reorder ---

    @Test
    fun reorderMovesQueuedTaskAmongQueuedTasks() {
        val a = task("a")
        val b = task("b")
        val c = task("c")
        assertEquals(listOf("b", "a", "c"), ids(reorder(listOf(a, b, c), 0, 1)))
        assertEquals(listOf("b", "c", "a"), ids(reorder(listOf(a, b, c), 0, 2)))
        assertEquals(listOf("c", "a", "b"), ids(reorder(listOf(a, b, c), 2, 0)))
    }

    @Test
    fun reorderReturnsOriginalForSameOrOutOfBoundsIndices() {
        val list = listOf(task("a"), task("b"))
        assertEquals(list, reorder(list, 0, 0))
        assertEquals(list, reorder(list, -1, 0))
        assertEquals(list, reorder(list, 0, 5))
        assertEquals(list, reorder(list, 5, 6))
    }

    @Test
    fun reorderLeavesActiveDownloadInItsExactSlot() {
        val a = task("a")
        val d = task("d", DownloadState.DOWNLOADING)
        val b = task("b")
        val c = task("c")
        assertEquals(listOf("a", "d", "c", "b"), ids(reorder(listOf(a, d, b, c), 2, 3)))
    }

    @Test
    fun reorderRejectsMovingAnActiveDownload() {
        val a = task("a")
        val d = task("d", DownloadState.DOWNLOADING)
        assertEquals(listOf("a", "d"), ids(reorder(listOf(a, d), 1, 0)))
    }

    @Test
    fun reorderRejectsDroppingOntoAnActiveSlot() {
        val a = task("a")
        val d = task("d", DownloadState.DOWNLOADING)
        assertEquals(listOf("a", "d"), ids(reorder(listOf(a, d), 0, 1)))
    }

    @Test
    fun reorderNeverMovesCompletedTasks() {
        val a = task("a")
        val c = task("c", DownloadState.COMPLETED)
        val b = task("b")
        assertEquals(listOf("b", "c", "a"), ids(reorder(listOf(a, c, b), 0, 2)))
        assertEquals(listOf("a", "c", "b"), ids(reorder(listOf(a, c, b), 1, 2)))
    }

    @Test
    fun reorderOnEmptyOrSingleListIsUnchanged() {
        assertEquals(emptyList(), reorder(emptyList(), 0, 1))
        val single = listOf(task("a"))
        assertEquals(single, reorder(single, 0, 0))
    }

    @Test
    fun reorderAllActiveListIsUnchanged() {
        val list = listOf(
            task("a", DownloadState.DOWNLOADING),
            task("b", DownloadState.COMPLETED)
        )
        assertEquals(list, reorder(list, 0, 1))
    }

    @Test
    fun reorderIsPureAndDoesNotMutateInput() {
        val list = listOf(task("a"), task("b"))
        reorder(list, 0, 1)
        assertEquals(listOf("a", "b"), ids(list))
    }

    // --- format metadata ---

    @Test
    fun formatLabelIsStableNonLocalizedMetadata() {
        assertEquals("original", formatLabel(DownloadFormat.ORIGINAL))
        assertEquals("high", formatLabel(DownloadFormat.HIGH))
        assertEquals("medium", formatLabel(DownloadFormat.MEDIUM))
        assertEquals("low", formatLabel(DownloadFormat.LOW))
    }

    @Test
    fun formatEntriesKeepDeclaredUiOrder() {
        assertEquals(
            listOf(
                DownloadFormat.ORIGINAL,
                DownloadFormat.HIGH,
                DownloadFormat.MEDIUM,
                DownloadFormat.LOW
            ),
            DownloadFormat.entries.toList()
        )
    }

    @Test
    fun isReorderableCoversQueuedPausedFailedOnly() {
        assertTrue(task("q", DownloadState.QUEUED).isReorderable())
        assertTrue(task("p", DownloadState.PAUSED).isReorderable())
        assertTrue(task("f", DownloadState.FAILED).isReorderable())
        assertFalse(task("d", DownloadState.DOWNLOADING).isReorderable())
        assertFalse(task("c", DownloadState.COMPLETED).isReorderable())
    }

    // --- subtitle companion ---

    @Test
    fun subtitlePathSitsBesideMainFileWithLanguageAndExtension() {
        assertEquals(
            "/dl/video.en.vtt",
            subtitleDestinationPath("/dl/video.mp4", "en", "vtt")
        )
        assertEquals(
            "C:\\dl\\video.de.srt",
            subtitleDestinationPath("C:\\dl\\video.mp4", "de", "srt")
        )
    }

    @Test
    fun subtitlePathFallsBackToSubtitleAndVttWhenBlank() {
        assertEquals("/dl/video.subtitle.vtt", subtitleDestinationPath("/dl/video.mp4", "", null))
        assertEquals("/dl/video.subtitle.vtt", subtitleDestinationPath("/dl/video.mp4", "  ", "  "))
    }

    @Test
    fun subtitlePathIsEmptyWhenMainDestinationIsBlank() {
        assertEquals("", subtitleDestinationPath("", "en", "vtt"))
        assertEquals("", subtitleDestinationPath("   ", "en", "vtt"))
    }

    @Test
    fun companionRequestIsNullWithoutSubtitleUrl() {
        val request = DownloadRequest(id = "m", url = "https://e.org/v", destinationPath = "/dl/v.mp4")
        assertEquals(null, subtitleCompanionRequest(request))
        assertEquals(
            null,
            subtitleCompanionRequest(request.copy(subtitleUrl = "   "))
        )
    }

    @Test
    fun companionRequestDerivesPathAndIdFromMainRequest() {
        val request = DownloadRequest(
            id = "m",
            url = "https://e.org/v",
            destinationPath = "/dl/v.mp4",
            title = "v",
            format = "high",
            subtitleUrl = "https://e.org/subs.en.vtt",
            subtitleLanguageTag = "en"
        )
        val companion = subtitleCompanionRequest(request)!!
        assertEquals("m-sub", companion.id)
        assertEquals("https://e.org/subs.en.vtt", companion.url)
        assertEquals("/dl/v.en.vtt", companion.destinationPath)
        assertEquals("v", companion.title)
        assertEquals("high", companion.format)
        assertEquals(null, companion.subtitleUrl)
        assertEquals("en", companion.subtitleLanguageTag)
    }

    @Test
    fun companionRequestExtensionComesFromSubtitleUrl() {
        val request = DownloadRequest(
            id = "m",
            url = "https://e.org/v",
            destinationPath = "/dl/v.mp4",
            subtitleUrl = "https://e.org/subs.de.srt?v=2",
            subtitleLanguageTag = "de"
        )
        assertEquals("/dl/v.de.srt", subtitleCompanionRequest(request)!!.destinationPath)
    }
}
