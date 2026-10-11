/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.subscription

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun settings() = MapSettings()

    private fun store(settings: MapSettings = settings()) = SubscriptionStore(settings, json)

    private fun subscription(id: String, url: String = id) =
        Subscription(id = id, name = id, url = url)

    @Test
    fun defaultStateHasNoEntries() {
        assertNull(settings().getStringOrNull(SubscriptionStore.KEY))
        assertTrue(store().entries.value.isEmpty())
    }

    @Test
    fun addPersistsAndReloadsFromNewInstance() {
        val backing = settings()
        store(backing).add(subscription("a", "https://youtube.com/@a"))

        val reloaded = SubscriptionStore(backing, json)
        assertEquals(listOf("a"), reloaded.entries.value.map { it.id })
        assertEquals("https://youtube.com/@a", reloaded.entries.value.first().url)
    }

    @Test
    fun addPrependsNewest() {
        val s = store()
        s.add(subscription("a"))
        s.add(subscription("b"))
        assertEquals(listOf("b", "a"), s.entries.value.map { it.id })
    }

    @Test
    fun addDeduplicatesById() {
        val s = store()
        s.add(subscription("a"))
        s.add(subscription("a"))
        assertEquals(listOf("a"), s.entries.value.map { it.id })
    }

    @Test
    fun addStampsAddedAtFromNowEpochMs() {
        val s = store()
        s.add(subscription("a"), nowEpochMs = 1234L)
        assertEquals(1234L, s.entries.value.first().addedAtEpochMs)
    }

    @Test
    fun addBlankIdIsIgnored() {
        val s = store()
        s.add(subscription(""))
        assertTrue(s.entries.value.isEmpty())
    }

    @Test
    fun pureAddDuplicateIdIsNoOp() {
        val existing = listOf(subscription("a"))
        val result = SubscriptionStore.pureAdd(existing, subscription("a"), 99L, 500)
        assertEquals(existing, result)
    }

    @Test
    fun pureAddPrependsAndCaps() {
        val existing = listOf(subscription("a"), subscription("b"))
        val result = SubscriptionStore.pureAdd(existing, subscription("c"), 0L, cap = 2)
        assertEquals(listOf("c", "a"), result.map { it.id })
    }

    @Test
    fun pureAddBlankIdReturnsInput() {
        val existing = listOf(subscription("a"))
        assertEquals(existing, SubscriptionStore.pureAdd(existing, subscription(""), 0L, 500))
    }

    @Test
    fun removeDropsOnlyTargeted() {
        val backing = settings()
        val s = store(backing)
        s.add(subscription("a"))
        s.add(subscription("b"))
        s.add(subscription("c"))

        s.remove("b")

        assertEquals(listOf("c", "a"), s.entries.value.map { it.id })
        assertEquals(listOf("c", "a"), SubscriptionStore(backing, json).entries.value.map { it.id })
    }

    @Test
    fun removeUnknownIdIsNoOp() {
        val s = store()
        s.add(subscription("a"))
        s.remove("missing")
        assertEquals(listOf("a"), s.entries.value.map { it.id })
    }

    @Test
    fun containsReflectsStoredIds() {
        val s = store()
        s.add(subscription("a"))
        assertTrue(s.contains("a"))
        assertTrue(!s.contains("b"))
    }

    @Test
    fun clearEmptiesAndRemovesKey() {
        val backing = settings()
        val s = store(backing)
        s.add(subscription("a"))
        s.clear()
        assertTrue(s.entries.value.isEmpty())
        assertNull(backing.getStringOrNull(SubscriptionStore.KEY))
    }

    @Test
    fun corruptBlobLoadsAsEmpty() {
        val backing = settings()
        backing.putString(SubscriptionStore.KEY, "{ this is not valid json")
        assertTrue(SubscriptionStore(backing, json).entries.value.isEmpty())
    }
}
