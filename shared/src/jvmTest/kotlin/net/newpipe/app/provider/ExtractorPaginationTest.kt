/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the pure, network-free next-page-token plumbing shared by every paginated listing
 * in [ExtractorMediaProvider] (search, trending, channels and playlists).
 *
 * No test here touches the network or initialises NewPipeExtractor: pages are built by hand from the
 * extractor's public [ListExtractor.InfoItemsPage] constructor and fed straight into the pure helper.
 */
class ExtractorPaginationTest {

    private fun stream(
        url: String = "https://www.youtube.com/watch?v=abcdefghijk",
        name: String = "A video"
    ) = StreamInfoItem(SERVICE_ID, url, name, StreamType.VIDEO_STREAM)

    @Test
    fun nextPageTokenIsSurfacedWhenPageHasANextPage() {
        val page = ListExtractor.InfoItemsPage<InfoItem>(
            listOf(stream()),
            Page("https://www.youtube.com/next"),
            emptyList()
        )

        assertEquals("https://www.youtube.com/next", ExtractorMediaProvider.pureNextPageToken(page))
    }

    @Test
    fun nextPageTokenIsNullWhenPageHasNoNextPage() {
        val page = ListExtractor.InfoItemsPage<InfoItem>(listOf(stream()), null, emptyList())

        assertNull(ExtractorMediaProvider.pureNextPageToken(page))
    }

    @Test
    fun nextPageTokenIsNullWhenNextPageUrlIsBlank() {
        val page = ListExtractor.InfoItemsPage<InfoItem>(listOf(stream()), Page(""), emptyList())

        assertNull(ExtractorMediaProvider.pureNextPageToken(page))
    }

    private companion object {
        const val SERVICE_ID = 0
    }
}
