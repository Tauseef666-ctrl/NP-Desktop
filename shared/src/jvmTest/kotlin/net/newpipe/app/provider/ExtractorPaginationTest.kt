/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

        val token = ExtractorMediaProvider.pureNextPageToken(page)
        assertNotNull(token)
        assertEquals("https://www.youtube.com/next", ExtractorMediaProvider.decodePageToken(token).url)
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

    @Test
    fun continuationStateRoundTripsThroughToken() {
        val page = ListExtractor.InfoItemsPage<InfoItem>(
            listOf(stream()),
            Page(
                "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
                "CONTINUATION_TOKEN",
                """{"context":{"client":{"clientName":"WEB"}},"continuation":"X"}""".toByteArray()
            ),
            emptyList()
        )

        val token = ExtractorMediaProvider.pureNextPageToken(page)
        assertNotNull(token)
        assertTrue(token.startsWith("v1|"), "token must use the provider's own format: $token")

        val decoded = ExtractorMediaProvider.decodePageToken(token)
        assertEquals("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false", decoded.url)
        assertEquals("CONTINUATION_TOKEN", decoded.id)
        assertEquals(
            """{"context":{"client":{"clientName":"WEB"}},"continuation":"X"}""",
            decoded.body?.toString(Charsets.UTF_8)
        )
    }

    @Test
    fun continuationTokenWithoutBodyKeepsBodyNull() {
        val page = ListExtractor.InfoItemsPage<InfoItem>(
            listOf(stream()),
            Page("https://www.youtube.com/youtubei/v1/search?prettyPrint=false", "SEARCH_TOKEN"),
            emptyList()
        )

        val token = ExtractorMediaProvider.pureNextPageToken(page)
        assertNotNull(token)

        val decoded = ExtractorMediaProvider.decodePageToken(token)
        assertEquals("SEARCH_TOKEN", decoded.id)
        assertNull(decoded.body)
    }

    @Test
    fun plainTokenIsTreatedAsPageUrl() {
        val decoded = ExtractorMediaProvider.decodePageToken("https://www.youtube.com/next")

        assertEquals("https://www.youtube.com/next", decoded.url)
        assertNull(decoded.body)
    }

    @Test
    fun malformedOwnFormatTokenIsRejected() {
        assertFailsWith<ParsingException> {
            ExtractorMediaProvider.decodePageToken("v1|only|two")
        }
    }

    private companion object {
        const val SERVICE_ID = 0
    }
}
