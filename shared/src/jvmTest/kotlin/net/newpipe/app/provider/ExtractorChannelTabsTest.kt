/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure, network-free channel tab label mapping of [ExtractorMediaProvider].
 *
 * No test here touches the network or initialises NewPipeExtractor: only the pure
 * [ExtractorMediaProvider.channelTabLabel] function is exercised.
 */
class ExtractorChannelTabsTest {

    @Test
    fun videosTabMapsToVideos() {
        assertEquals("Videos", ExtractorMediaProvider.channelTabLabel("videos"))
    }

    @Test
    fun shortsTabMapsToShorts() {
        assertEquals("Shorts", ExtractorMediaProvider.channelTabLabel("shorts"))
    }

    @Test
    fun livestreamsAndStreamsTabsMapToLive() {
        assertEquals("Live", ExtractorMediaProvider.channelTabLabel("livestreams"))
        assertEquals("Live", ExtractorMediaProvider.channelTabLabel("streams"))
    }

    @Test
    fun playlistsTabMapsToPlaylists() {
        assertEquals("Playlists", ExtractorMediaProvider.channelTabLabel("playlists"))
    }

    @Test
    fun albumsAndReleasesTabsMapToAlbums() {
        assertEquals("Albums", ExtractorMediaProvider.channelTabLabel("albums"))
        assertEquals("Albums", ExtractorMediaProvider.channelTabLabel("releases"))
    }

    @Test
    fun unknownTabFallsBackToId() {
        assertEquals("community", ExtractorMediaProvider.channelTabLabel("community"))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals("Videos", ExtractorMediaProvider.channelTabLabel("VIDEOS"))
    }

    @Test
    fun coursesTabMapsToCourses() {
        assertEquals("Courses", ExtractorMediaProvider.channelTabLabel(ChannelTabs.COURSES))
    }
}
