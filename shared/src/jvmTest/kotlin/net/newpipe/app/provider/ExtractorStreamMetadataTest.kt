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

class ExtractorStreamMetadataTest {

    @Test
    fun `happy path maps every value through unchanged`() {
        val result = ExtractorMediaProvider.pureStreamMetadata(
            title = "A Stream",
            uploaderName = "Uploader",
            uploaderUrl = "https://example.com/channel",
            uploaderVerified = true,
            subscriberCount = 1234L,
            viewCount = 5678L,
            likeCount = 910L,
            textualUploadDate = "2026-01-02",
            durationSeconds = 3600L,
            thumbnailUrl = "https://example.com/thumb.jpg",
            description = "A description",
            category = "Music",
            tags = listOf("a", "b"),
            isLive = true
        )
        assertEquals(
            StreamMetadata(
                title = "A Stream",
                uploaderName = "Uploader",
                uploaderUrl = "https://example.com/channel",
                uploaderVerified = true,
                subscriberCount = 1234L,
                viewCount = 5678L,
                likeCount = 910L,
                uploadDate = "2026-01-02",
                durationSeconds = 3600L,
                thumbnailUrl = "https://example.com/thumb.jpg",
                description = "A description",
                category = "Music",
                tags = listOf("a", "b"),
                isLive = true
            ),
            result
        )
    }

    @Test
    fun `zero counts and zero duration become null`() {
        val result = ExtractorMediaProvider.pureStreamMetadata(
            title = "A Stream",
            uploaderName = "Uploader",
            uploaderUrl = "https://example.com/channel",
            uploaderVerified = false,
            subscriberCount = 0L,
            viewCount = 0L,
            likeCount = 0L,
            textualUploadDate = "2026-01-02",
            durationSeconds = 0L,
            thumbnailUrl = "https://example.com/thumb.jpg",
            description = "A description",
            category = "Music",
            tags = emptyList(),
            isLive = false
        )
        assertNull(result.subscriberCount)
        assertNull(result.viewCount)
        assertNull(result.likeCount)
        assertNull(result.durationSeconds)
    }

    @Test
    fun `blank strings become null`() {
        val result = ExtractorMediaProvider.pureStreamMetadata(
            title = "A Stream",
            uploaderName = "   ",
            uploaderUrl = "",
            uploaderVerified = false,
            subscriberCount = 1L,
            viewCount = 1L,
            likeCount = 1L,
            textualUploadDate = "   ",
            durationSeconds = 60L,
            thumbnailUrl = "   ",
            description = "",
            category = "   ",
            tags = emptyList(),
            isLive = false
        )
        assertNull(result.uploaderName)
        assertNull(result.uploaderUrl)
        assertNull(result.uploadDate)
        assertNull(result.thumbnailUrl)
        assertNull(result.description)
        assertNull(result.category)
    }

    @Test
    fun `tags drop blank entries`() {
        val result = ExtractorMediaProvider.pureStreamMetadata(
            title = "A Stream",
            uploaderName = "Uploader",
            uploaderUrl = "https://example.com/channel",
            uploaderVerified = false,
            subscriberCount = 1L,
            viewCount = 1L,
            likeCount = 1L,
            textualUploadDate = "2026-01-02",
            durationSeconds = 60L,
            thumbnailUrl = "https://example.com/thumb.jpg",
            description = "A description",
            category = "Music",
            tags = listOf("a", "", "b"),
            isLive = false
        )
        assertEquals(listOf("a", "b"), result.tags)
    }

    @Test
    fun `isLive and uploaderVerified are carried through`() {
        val live = ExtractorMediaProvider.pureStreamMetadata(
            title = "A Stream",
            uploaderName = "Uploader",
            uploaderUrl = "https://example.com/channel",
            uploaderVerified = true,
            subscriberCount = 1L,
            viewCount = 1L,
            likeCount = 1L,
            textualUploadDate = "2026-01-02",
            durationSeconds = 60L,
            thumbnailUrl = "https://example.com/thumb.jpg",
            description = "A description",
            category = "Music",
            tags = emptyList(),
            isLive = true
        )
        assertTrue(live.isLive)
        assertTrue(live.uploaderVerified)

        val notLive = ExtractorMediaProvider.pureStreamMetadata(
            title = "A Stream",
            uploaderName = "Uploader",
            uploaderUrl = "https://example.com/channel",
            uploaderVerified = false,
            subscriberCount = 1L,
            viewCount = 1L,
            likeCount = 1L,
            textualUploadDate = "2026-01-02",
            durationSeconds = 60L,
            thumbnailUrl = "https://example.com/thumb.jpg",
            description = "A description",
            category = "Music",
            tags = emptyList(),
            isLive = false
        )
        assertFalse(notLive.isLive)
        assertFalse(notLive.uploaderVerified)
    }
}
