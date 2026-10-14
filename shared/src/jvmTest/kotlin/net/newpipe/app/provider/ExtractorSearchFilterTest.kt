/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure search-filter and live-detection helpers of [ExtractorMediaProvider].
 *
 * No test here touches the network: the mapping functions are fed hand-built extractor items.
 */
class ExtractorSearchFilterTest {

    private fun stream(
        url: String = "https://www.youtube.com/watch?v=abcdefghijk",
        name: String = "A stream",
        type: StreamType = StreamType.VIDEO_STREAM
    ) = StreamInfoItem(SERVICE_ID, url, name, type)

    @Test
    fun contentFilterIdMapsAllToEmptyAndOthersToTheirId() {
        assertEquals("", ExtractorMediaProvider.contentFilterId(SearchFilter.ALL))
        assertEquals("videos", ExtractorMediaProvider.contentFilterId(SearchFilter.VIDEOS))
        assertEquals("channels", ExtractorMediaProvider.contentFilterId(SearchFilter.CHANNELS))
        assertEquals("playlists", ExtractorMediaProvider.contentFilterId(SearchFilter.PLAYLISTS))
    }

    @Test
    fun isLiveStreamTypeRecognisesVideoAndAudioLive() {
        assertEquals(true, ExtractorMediaProvider.isLiveStreamType(StreamType.LIVE_STREAM))
        assertEquals(true, ExtractorMediaProvider.isLiveStreamType(StreamType.AUDIO_LIVE_STREAM))
        assertEquals(false, ExtractorMediaProvider.isLiveStreamType(StreamType.VIDEO_STREAM))
        assertEquals(false, ExtractorMediaProvider.isLiveStreamType(StreamType.AUDIO_STREAM))
        assertEquals(false, ExtractorMediaProvider.isLiveStreamType(StreamType.POST_LIVE_STREAM))
        assertEquals(false, ExtractorMediaProvider.isLiveStreamType(StreamType.POST_LIVE_AUDIO_STREAM))
        assertEquals(false, ExtractorMediaProvider.isLiveStreamType(StreamType.NONE))
    }

    @Test
    fun audioLiveStreamMapsToLiveKind() {
        val mapped = ExtractorMediaProvider.pureMapItem(stream(type = StreamType.AUDIO_LIVE_STREAM))!!
        assertEquals(ProviderContentKind.LIVE, mapped.kind)
    }

    @Test
    fun videoLiveStreamMapsToLiveKind() {
        val mapped = ExtractorMediaProvider.pureMapItem(stream(type = StreamType.LIVE_STREAM))!!
        assertEquals(ProviderContentKind.LIVE, mapped.kind)
    }

    @Test
    fun plainVideoStreamMapsToVideoKind() {
        val mapped = ExtractorMediaProvider.pureMapItem(stream(type = StreamType.VIDEO_STREAM))!!
        assertEquals(ProviderContentKind.VIDEO, mapped.kind)
    }

    @Test
    fun filterFromIdResolvesKnownIdsAndFallsBackToAll() {
        assertEquals(SearchFilter.VIDEOS, SearchFilter.fromId("videos"))
        assertEquals(SearchFilter.CHANNELS, SearchFilter.fromId("channels"))
        assertEquals(SearchFilter.PLAYLISTS, SearchFilter.fromId("playlists"))
        assertEquals(SearchFilter.ALL, SearchFilter.fromId("all"))
        assertEquals(SearchFilter.ALL, SearchFilter.fromId("unknown"))
        assertEquals(SearchFilter.ALL, SearchFilter.fromId(null))
    }

    private companion object {
        const val SERVICE_ID = 0
    }
}
