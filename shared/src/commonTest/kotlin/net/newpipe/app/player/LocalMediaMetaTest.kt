/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalMediaMetaTest {

    private fun file(
        width: Int? = null,
        height: Int? = null,
        artist: String? = null,
        album: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) = LocalMediaFile(
        id = "/m/f.mp4",
        path = "/m/f.mp4",
        name = "f.mp4",
        extension = "mp4",
        mimeType = "video/mp4",
        url = "file:///m/f.mp4",
        width = width,
        height = height,
        artist = artist,
        album = album,
        latitude = latitude,
        longitude = longitude
    )

    @Test
    fun formatResolutionFormatsValidDimensions() {
        assertEquals("1920×1080", formatResolution(1920, 1080))
    }

    @Test
    fun formatResolutionRejectsMissingOrNonPositiveDimensions() {
        assertNull(formatResolution(null, 1080))
        assertNull(formatResolution(1920, null))
        assertNull(formatResolution(0, 1080))
        assertNull(formatResolution(1920, -1))
    }

    @Test
    fun metaLineIsNullWhenNothingKnown() {
        assertNull(localMediaMetaLine(file()))
    }

    @Test
    fun metaLineShowsResolutionOnly() {
        assertEquals("1280×720", localMediaMetaLine(file(width = 1280, height = 720)))
    }

    @Test
    fun metaLineJoinsArtistAndAlbum() {
        assertEquals(
            "A – B",
            localMediaMetaLine(file(artist = "A", album = "B"))
        )
    }

    @Test
    fun metaLineShowsArtistAlone() {
        assertEquals("A", localMediaMetaLine(file(artist = "A")))
    }

    @Test
    fun metaLineShowsLocationOnlyWhenBothCoordinatesPresent() {
        assertNull(localMediaMetaLine(file(latitude = 51.5)))
        assertEquals(
            "📍 51.5, -0.12",
            localMediaMetaLine(file(latitude = 51.5, longitude = -0.12))
        )
    }

    @Test
    fun metaLineCombinesResolvedFields() {
        assertEquals(
            "1920×1080 • A – B • 📍 1.0, 2.0",
            localMediaMetaLine(
                file(width = 1920, height = 1080, artist = "A", album = "B", latitude = 1.0, longitude = 2.0)
            )
        )
    }
}
