/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionOpsTest {

    @Test
    fun normalizeTrimsSurroundingWhitespace() {
        assertEquals(
            "https://youtube.com/@Foo",
            normalizeChannelUrl("  https://youtube.com/@Foo  ")
        )
    }

    @Test
    fun normalizeRejectsBlankAndWhitespace() {
        assertNull(normalizeChannelUrl(""))
        assertNull(normalizeChannelUrl("   "))
    }

    @Test
    fun normalizeStripsTrailingSlash() {
        assertEquals(
            "https://youtube.com/@Foo",
            normalizeChannelUrl("https://youtube.com/@Foo/")
        )
    }

    @Test
    fun normalizeLowercasesSchemeAndHostButKeepsPathCase() {
        assertEquals(
            "https://youtube.com/@Foo",
            normalizeChannelUrl("HTTPS://YouTube.COM/@Foo")
        )
    }

    @Test
    fun normalizeRejectsNonHttpScheme() {
        assertNull(normalizeChannelUrl("ftp://youtube.com/@Foo"))
        assertNull(normalizeChannelUrl("file:///tmp/@Foo"))
    }

    @Test
    fun normalizeRejectsMissingScheme() {
        assertNull(normalizeChannelUrl("youtube.com/@Foo"))
        assertNull(normalizeChannelUrl("/@Foo"))
    }

    @Test
    fun normalizeRejectsBlankHost() {
        assertNull(normalizeChannelUrl("https:///path"))
    }

    @Test
    fun normalizeKeepsHostOnlyUrl() {
        assertEquals("https://example.com", normalizeChannelUrl("https://example.com/"))
    }

    @Test
    fun subscriptionIdLowercasesNormalizedUrl() {
        assertEquals(
            "https://youtube.com/@foo",
            subscriptionIdFor("https://youtube.com/@Foo")
        )
    }

    @Test
    fun subscriptionIdIsDeterministicAcrossEquivalentUrls() {
        val a = subscriptionIdFor("https://youtube.com/@Foo")
        val b = subscriptionIdFor("  HTTPS://YouTube.com/@Foo/  ")
        assertEquals(a, b)
    }

    @Test
    fun subscriptionIdIsNullForInvalidUrl() {
        assertNull(subscriptionIdFor("not a url"))
        assertNull(subscriptionIdFor(""))
    }

    @Test
    fun likelyChannelUrlAcceptsDomainWithPath() {
        assertTrue(isLikelyChannelUrl("https://youtube.com/@Foo"))
        assertTrue(isLikelyChannelUrl("https://www.youtube.com/channel/abc123"))
    }

    @Test
    fun likelyChannelUrlRejectsBlankAndGarbage() {
        assertFalse(isLikelyChannelUrl(""))
        assertFalse(isLikelyChannelUrl("   "))
        assertFalse(isLikelyChannelUrl("not a url"))
        assertFalse(isLikelyChannelUrl("youtube.com/@Foo"))
    }

    @Test
    fun likelyChannelUrlRejectsBareHostAndLocalHost() {
        assertFalse(isLikelyChannelUrl("https://example.com"))
        assertFalse(isLikelyChannelUrl("https://localhost/@Foo"))
    }
}
