/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the pure, network-free page-token plumbing used by playlist extraction in
 * [ExtractorMediaProvider].
 *
 * No test here touches the network or initialises NewPipeExtractor: pages are built by hand from the
 * extractor's public [ListExtractor.InfoItemsPage] constructor and fed straight into the pure mapping.
 */
class ExtractorPlaylistTest {

    private fun stream(
        url: String = "https://www.youtube.com/watch?v=abcdefghijk",
        name: String = "A video",
        type: StreamType = StreamType.VIDEO_STREAM
    ) = StreamInfoItem(SERVICE_ID, url, name, type)

    @Test
    fun pageWithoutTokenMapsToNullNextPageToken() {
        val page = ListExtractor.InfoItemsPage<InfoItem>(listOf(stream()), null, emptyList())

        val mapped = ExtractorMediaProvider.pureMapPage(page)

        assertNull(mapped.nextPageToken)
    }

    @Test
    fun pageWithTokenCarriesNextPageToken() {
        val page = ListExtractor.InfoItemsPage<InfoItem>(listOf(stream()), null, emptyList())

        val mapped = ExtractorMediaProvider.pureMapPage(page, "https://next")

        assertEquals("https://next", mapped.nextPageToken)
    }

    @Test
    fun pageStreamItemsMapToVideoKind() {
        val first = stream(url = "https://www.youtube.com/watch?v=first")
        val second = stream(url = "https://www.youtube.com/watch?v=second")
        val page = ListExtractor.InfoItemsPage<InfoItem>(listOf(first, second), null, emptyList())

        val mapped = ExtractorMediaProvider.pureMapPage(page)

        assertEquals(2, mapped.items.size)
        assertEquals(
            listOf(ProviderContentKind.VIDEO, ProviderContentKind.VIDEO),
            mapped.items.map { it.kind }
        )
    }

    private companion object {
        const val SERVICE_ID = 0
    }
}
