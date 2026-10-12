/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractorStreamResolveTest {

    @Test
    fun `live non blank hls returns hls url with isLive true and duration null`() {
        val result = ExtractorMediaProvider.pickResolvedMedia(
            isLive = true,
            hlsUrl = "https://example.com/stream.m3u8",
            durationSeconds = 3600L,
            progressiveVideo = listOf(ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/video.mp4", 1080)),
            audioStreams = listOf("https://example.com/audio.mp4")
        )
        assertEquals("https://example.com/stream.m3u8", result?.streamUrl)
        assertTrue(result?.isLive == true)
        assertEquals(null, result?.durationSeconds)
        assertFalse(result?.audioOnly == true)
    }

    @Test
    fun `live with blank hls picks highest progressive with duration null`() {
        val result = ExtractorMediaProvider.pickResolvedMedia(
            isLive = true,
            hlsUrl = "   ",
            durationSeconds = 1800L,
            progressiveVideo = listOf(
                ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/v480.mp4", 480),
                ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/v720.mp4", 720)
            ),
            audioStreams = listOf("https://example.com/audio.mp4")
        )
        assertEquals("https://example.com/v720.mp4", result?.streamUrl)
        assertTrue(result?.isLive == true)
        assertEquals(null, result?.durationSeconds)
        assertFalse(result?.audioOnly == true)
    }

    @Test
    fun `live with null hls picks highest progressive with duration null`() {
        val result = ExtractorMediaProvider.pickResolvedMedia(
            isLive = true,
            hlsUrl = null,
            durationSeconds = 1800L,
            progressiveVideo = listOf(
                ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/v480.mp4", 480),
                ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/v720.mp4", 720)
            ),
            audioStreams = listOf("https://example.com/audio.mp4")
        )
        assertEquals("https://example.com/v720.mp4", result?.streamUrl)
        assertTrue(result?.isLive == true)
        assertEquals(null, result?.durationSeconds)
        assertFalse(result?.audioOnly == true)
    }

    @Test
    fun `live no hls no video returns audio with audioOnly true and isLive true`() {
        val result = ExtractorMediaProvider.pickResolvedMedia(
            isLive = true,
            hlsUrl = null,
            durationSeconds = 0L,
            progressiveVideo = emptyList(),
            audioStreams = listOf("https://example.com/audio.mp4")
        )
        assertEquals("https://example.com/audio.mp4", result?.streamUrl)
        assertTrue(result?.isLive == true)
        assertTrue(result?.audioOnly == true)
        assertEquals(null, result?.durationSeconds)
    }

    @Test
    fun `non live picks highest height progressive audioOnly false duration passed through`() {
        val result = ExtractorMediaProvider.pickResolvedMedia(
            isLive = false,
            hlsUrl = "https://example.com/stream.m3u8",
            durationSeconds = 1234L,
            progressiveVideo = listOf(
                ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/v360.mp4", 360),
                ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/v1080.mp4", 1080),
                ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/v720.mp4", 720)
            ),
            audioStreams = listOf("https://example.com/audio.mp4")
        )
        assertEquals("https://example.com/v1080.mp4", result?.streamUrl)
        assertFalse(result?.isLive == true)
        assertFalse(result?.audioOnly == true)
        assertEquals(1234L, result?.durationSeconds)
    }

    @Test
    fun `blank progressive urls are ignored`() {
        val result = ExtractorMediaProvider.pickResolvedMedia(
            isLive = false,
            hlsUrl = null,
            durationSeconds = 60L,
            progressiveVideo = listOf(
                ExtractorMediaProvider.Companion.VideoCandidate("   ", 1080),
                ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/v720.mp4", 720)
            ),
            audioStreams = listOf("https://example.com/audio.mp4")
        )
        assertEquals("https://example.com/v720.mp4", result?.streamUrl)
    }

    @Test
    fun `non live with no progressive falls back to audio audioOnly true`() {
        val result = ExtractorMediaProvider.pickResolvedMedia(
            isLive = false,
            hlsUrl = null,
            durationSeconds = 300L,
            progressiveVideo = emptyList(),
            audioStreams = listOf("https://example.com/audio.mp4")
        )
        assertEquals("https://example.com/audio.mp4", result?.streamUrl)
        assertFalse(result?.isLive == true)
        assertTrue(result?.audioOnly == true)
        assertEquals(300L, result?.durationSeconds)
    }

    @Test
    fun `nothing available returns null`() {
        val result = ExtractorMediaProvider.pickResolvedMedia(
            isLive = false,
            hlsUrl = null,
            durationSeconds = 100L,
            progressiveVideo = listOf(ExtractorMediaProvider.Companion.VideoCandidate("   ", 1080)),
            audioStreams = listOf("   ")
        )
        assertNull(result)
    }

    @Test
    fun `non live zero duration returns duration null`() {
        val result = ExtractorMediaProvider.pickResolvedMedia(
            isLive = false,
            hlsUrl = null,
            durationSeconds = 0L,
            progressiveVideo = listOf(ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/v480.mp4", 480)),
            audioStreams = listOf("https://example.com/audio.mp4")
        )
        assertEquals("https://example.com/v480.mp4", result?.streamUrl)
        assertEquals(null, result?.durationSeconds)
    }

    @Test
    fun `non live negative duration returns duration null`() {
        val result = ExtractorMediaProvider.pickResolvedMedia(
            isLive = false,
            hlsUrl = null,
            durationSeconds = -5L,
            progressiveVideo = listOf(ExtractorMediaProvider.Companion.VideoCandidate("https://example.com/v480.mp4", 480)),
            audioStreams = listOf("https://example.com/audio.mp4")
        )
        assertEquals("https://example.com/v480.mp4", result?.streamUrl)
        assertEquals(null, result?.durationSeconds)
    }
}