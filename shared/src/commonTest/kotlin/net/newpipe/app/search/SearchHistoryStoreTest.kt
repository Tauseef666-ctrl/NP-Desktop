/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.search

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchHistoryStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun settings() = MapSettings()

    private fun store(settings: MapSettings = settings()) =
        SearchHistoryStore(settings, json)

    @Test
    fun pureAddTrimsAndPutsNewestFirst() {
        val result = SearchHistoryStore.pureAdd(listOf("old"), "  new  ", 15)
        assertEquals(listOf("new", "old"), result)
    }

    @Test
    fun pureAddIgnoresBlank() {
        val existing = listOf("old")
        assertEquals(existing, SearchHistoryStore.pureAdd(existing, "   ", 15))
        assertEquals(existing, SearchHistoryStore.pureAdd(existing, "", 15))
    }

    @Test
    fun pureAddDeduplicatesCaseInsensitivelyKeepingNewestCasing() {
        val result = SearchHistoryStore.pureAdd(listOf("Kotlin", "Jetpack"), "kotlin", 15)
        assertEquals(listOf("kotlin", "Jetpack"), result)
    }

    @Test
    fun pureAddCapsEntries() {
        val result = SearchHistoryStore.pureAdd(listOf("x", "y"), "z", cap = 2)
        assertEquals(listOf("z", "x"), result)
    }

    @Test
    fun recordPersistsAndReloadsFromNewInstance() {
        val backing = settings()
        store(backing).record("alpha")

        val reloaded = SearchHistoryStore(backing, json)
        assertEquals(listOf("alpha"), reloaded.entries.value)
    }

    @Test
    fun recordKeepsNewestFirstAndDeduplicates() {
        val s = store()
        s.record("Kotlin")
        s.record("Jetpack")
        s.record("kotlin")
        assertEquals(listOf("kotlin", "Jetpack"), s.entries.value)
    }

    @Test
    fun recordIgnoresBlank() {
        val s = store()
        s.record("   ")
        assertTrue(s.entries.value.isEmpty())
    }

    @Test
    fun clearEmptiesHistoryAndRemovesKey() {
        val backing = settings()
        val s = store(backing)
        s.record("alpha")
        s.clear()
        assertTrue(s.entries.value.isEmpty())
        assertNull(backing.getStringOrNull(SearchHistoryStore.KEY))
    }

    @Test
    fun corruptBlobLoadsAsEmpty() {
        val backing = settings()
        backing.putString(SearchHistoryStore.KEY, "{ this is not valid json")
        assertTrue(SearchHistoryStore(backing, json).entries.value.isEmpty())
    }

    @Test
    fun defaultStateHasNoEntries() {
        assertNull(settings().getStringOrNull(SearchHistoryStore.KEY))
        assertTrue(store(settings()).entries.value.isEmpty())
    }
}
