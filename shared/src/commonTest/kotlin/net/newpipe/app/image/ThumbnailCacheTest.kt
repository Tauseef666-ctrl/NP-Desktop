/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.image

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThumbnailCacheTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun putThenGetReturnsSameBytes() {
        val cache = ThumbnailCache()

        cache.put("https://img/a", bytes(1, 2, 3))

        assertContentEquals(bytes(1, 2, 3), cache.get("https://img/a"))
        assertEquals(1, cache.size)
        assertEquals(3L, cache.bytes)
    }

    @Test
    fun missingKeyReturnsNull() {
        val cache = ThumbnailCache()

        assertNull(cache.get("https://img/none"))
    }

    @Test
    fun blankKeyAndEmptyPayloadAreIgnored() {
        val cache = ThumbnailCache()

        cache.put("   ", bytes(1))
        cache.put("https://img/a", ByteArray(0))

        assertEquals(0, cache.size)
        assertEquals(0L, cache.bytes)
    }

    @Test
    fun putReplacesExistingEntryAndAdjustsByteCount() {
        val cache = ThumbnailCache()

        cache.put("https://img/a", bytes(1, 2, 3))
        cache.put("https://img/a", bytes(9))

        assertEquals(1, cache.size)
        assertEquals(1L, cache.bytes)
        assertContentEquals(bytes(9), cache.get("https://img/a"))
    }

    @Test
    fun entryCountLimitEvictsLeastRecentlyUsed() {
        val cache = ThumbnailCache(maxEntries = 2)

        cache.put("a", bytes(1))
        cache.put("b", bytes(2))
        cache.put("c", bytes(3))

        assertFalse(cache.contains("a"))
        assertTrue(cache.contains("b"))
        assertTrue(cache.contains("c"))
        assertEquals(2, cache.size)
    }

    @Test
    fun getRefreshesRecencySoItIsNotEvictedFirst() {
        val cache = ThumbnailCache(maxEntries = 2)

        cache.put("a", bytes(1))
        cache.put("b", bytes(2))
        cache.get("a")

        cache.put("c", bytes(3))

        assertTrue(cache.contains("a"))
        assertFalse(cache.contains("b"))
        assertTrue(cache.contains("c"))
    }

    @Test
    fun byteBudgetEvictsUntilFits() {
        val cache = ThumbnailCache(maxBytes = 5)

        cache.put("a", bytes(1, 2, 3))
        cache.put("b", bytes(4, 5, 6))

        assertEquals(1, cache.size)
        assertFalse(cache.contains("a"))
        assertTrue(cache.contains("b"))
        assertEquals(3L, cache.bytes)
    }

    @Test
    fun oversizedEntryEvictsEverythingIncludingItself() {
        val cache = ThumbnailCache(maxBytes = 2)

        cache.put("a", bytes(1))
        cache.put("big", bytes(1, 2, 3, 4))

        assertEquals(0, cache.size)
        assertEquals(0L, cache.bytes)
    }

    @Test
    fun clearRemovesEverything() {
        val cache = ThumbnailCache()
        cache.put("a", bytes(1))
        cache.put("b", bytes(2))

        cache.clear()

        assertEquals(0, cache.size)
        assertEquals(0L, cache.bytes)
        assertNull(cache.get("a"))
    }
}
