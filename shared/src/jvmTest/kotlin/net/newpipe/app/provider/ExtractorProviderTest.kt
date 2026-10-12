/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for the pure, network-free mapping layer of [ExtractorMediaProvider] and for eager
 * provider registration by [ExtractorProviderRegistrar].
 *
 * No test here touches the network or initialises NewPipeExtractor: the mapping functions are fed
 * hand-built extractor items, and registration is exercised with a local fake [MediaProvider].
 */
class ExtractorProviderTest {

    private fun stream(
        url: String = "https://www.youtube.com/watch?v=abcdefghijk",
        name: String = "A video",
        type: StreamType = StreamType.VIDEO_STREAM
    ) = StreamInfoItem(SERVICE_ID, url, name, type)

    private fun thumbnail(url: String, height: Int) =
        Image(url, height, if (height == Image.HEIGHT_UNKNOWN) Image.WIDTH_UNKNOWN else height * 16 / 9, Image.ResolutionLevel.UNKNOWN)

    @Test
    fun streamMapsToVideoKindWithUploaderAndDuration() {
        val item = stream().apply {
            uploaderName = "Some Channel"
            uploaderUrl = "https://www.youtube.com/@somechannel"
            duration = 212
        }

        val mapped = ExtractorMediaProvider.pureMapItem(item)!!

        assertEquals(ProviderContentKind.VIDEO, mapped.kind)
        assertEquals(item.url, mapped.id)
        assertEquals(item.name, mapped.title)
        assertEquals("Some Channel", mapped.uploader)
        assertEquals("https://www.youtube.com/@somechannel", mapped.uploaderUrl)
        assertEquals(212L, mapped.durationSeconds)
    }

    @Test
    fun liveStreamMapsToLiveKind() {
        val mapped = ExtractorMediaProvider.pureMapItem(stream(type = StreamType.LIVE_STREAM))!!

        assertEquals(ProviderContentKind.LIVE, mapped.kind)
    }

    @Test
    fun nonPositiveDurationBecomesNull() {
        val unknown = stream().apply { duration = -1 }
        val zero = stream().apply { duration = 0 }

        assertNull(ExtractorMediaProvider.pureMapItem(unknown)!!.durationSeconds)
        assertNull(ExtractorMediaProvider.pureMapItem(zero)!!.durationSeconds)
    }

    @Test
    fun bestThumbnailPrefersLargestKnownHeight() {
        val item = stream().apply {
            thumbnails = listOf(
                thumbnail("https://img/small", 90),
                thumbnail("https://img/large", 720),
                thumbnail("https://img/unknown", Image.HEIGHT_UNKNOWN)
            )
        }

        assertEquals("https://img/large", ExtractorMediaProvider.pureMapItem(item)!!.thumbnailUrl)
    }

    @Test
    fun bestThumbnailFallsBackToFirstWhenHeightsUnknown() {
        val item = stream().apply {
            thumbnails = listOf(
                thumbnail("https://img/first", Image.HEIGHT_UNKNOWN),
                thumbnail("https://img/second", Image.HEIGHT_UNKNOWN)
            )
        }

        assertEquals("https://img/first", ExtractorMediaProvider.pureMapItem(item)!!.thumbnailUrl)
    }

    @Test
    fun commentMapsToNull() {
        val comment = CommentInfoItem("https://www.youtube.com/watch?v=abcdefghijk&lc=1", "A comment")

        assertNull(ExtractorMediaProvider.pureMapItem(comment))
    }

    @Test
    fun channelMapsToChannelKindWithoutUploader() {
        val item = ChannelInfoItem(
            SERVICE_ID,
            "https://www.youtube.com/channel/UC1234567890",
            "Some Channel"
        ).apply {
            description = "Channel description"
            thumbnails = listOf(thumbnail("https://img/avatar", 176))
        }

        val mapped = ExtractorMediaProvider.pureMapItem(item)!!

        assertEquals(ProviderContentKind.CHANNEL, mapped.kind)
        assertEquals(item.url, mapped.id)
        assertEquals("Some Channel", mapped.title)
        assertEquals("https://img/avatar", mapped.thumbnailUrl)
        assertNull(mapped.uploader)
        assertNull(mapped.uploaderUrl)
        assertNull(mapped.durationSeconds)
    }

    @Test
    fun playlistMapsUploaderFields() {
        val item = PlaylistInfoItem(
            SERVICE_ID,
            "https://www.youtube.com/playlist?list=PL1234567890",
            "A playlist"
        ).apply {
            uploaderName = "Some Channel"
            uploaderUrl = "https://www.youtube.com/@somechannel"
        }

        val mapped = ExtractorMediaProvider.pureMapItem(item)!!

        assertEquals(ProviderContentKind.PLAYLIST, mapped.kind)
        assertEquals("Some Channel", mapped.uploader)
        assertEquals("https://www.youtube.com/@somechannel", mapped.uploaderUrl)
    }

    @Test
    fun pageMapsItemsDropsNullsAndHasNoNextToken() {
        val stream = stream()
        val comment = CommentInfoItem("https://www.youtube.com/watch?v=abcdefghijk&lc=1", "A comment")
        val page = ListExtractor.InfoItemsPage<InfoItem>(listOf(stream, comment), null, emptyList())

        val mapped = ExtractorMediaProvider.pureMapPage(page)

        assertEquals(1, mapped.items.size)
        assertEquals(stream.url, mapped.items.single().id)
        assertNull(mapped.nextPageToken)
    }

    @Test
    fun registrarRegistersProviderIntoRegistry() {
        val registry = ProviderRegistry()
        val fake = FakeRegistrarProvider(id = "fake-provider", name = "Fake")

        ExtractorProviderRegistrar(registry, fake)

        assertSame(fake, registry.byId("fake-provider"))
        assertTrue(registry.providers.value.contains(fake))
        assertTrue(registry.available.value.contains(fake))
    }

    private companion object {
        const val SERVICE_ID = 0
    }
}

/** Minimal in-memory [MediaProvider] so registration can be tested without the extractor. */
private class FakeRegistrarProvider(
    override val id: String,
    override val name: String,
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(search = true)
) : MediaProvider

/** A concrete [InfoItem] carrying the [InfoItem.InfoType.COMMENT] type for mapping tests. */
private class CommentInfoItem(url: String, name: String) :
    InfoItem(InfoItem.InfoType.COMMENT, 0, url, name)
