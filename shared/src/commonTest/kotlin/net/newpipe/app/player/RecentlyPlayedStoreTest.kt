/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecentlyPlayedStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun settings() = MapSettings()

    private fun store(settings: MapSettings = settings()) =
        RecentlyPlayedStore(settings, json)

    private fun media(id: String, title: String = "Title $id", url: String = "file://$id") =
        MediaItem(id = id, title = title, url = url, durationMs = 120_000L)

    @Test
    fun recordAddsEntry() {
        val s = store()
        s.record(media("a"), nowEpochMs = 1_000L)
        assertEquals(1, s.entries.value.size)
        val entry = s.entries.value.first()
        assertEquals("a", entry.id)
        assertEquals("Title a", entry.title)
        assertEquals("file://a", entry.url)
        assertEquals(120_000L, entry.durationMs)
        assertEquals(1_000L, entry.playedAtEpochMs)
    }

    @Test
    fun recordKeepsNewestFirst() {
        val s = store()
        s.record(media("a"), nowEpochMs = 1_000L)
        s.record(media("b"), nowEpochMs = 2_000L)
        assertEquals(listOf("b", "a"), s.entries.value.map { it.id })
    }

    @Test
    fun recordingExistingIdMovesItToFrontWithoutDuplicating() {
        val s = store()
        s.record(media("a"), nowEpochMs = 1_000L)
        s.record(media("b"), nowEpochMs = 2_000L)
        s.record(media("a"), nowEpochMs = 3_000L)
        assertEquals(listOf("a", "b"), s.entries.value.map { it.id })
        assertEquals(2, s.entries.value.size)
    }

    @Test
    fun recordingExistingIdUpdatesFields() {
        val s = store()
        s.record(media("a", title = "Old", url = "file://old"), nowEpochMs = 1_000L)
        s.record(media("a", title = "New", url = "file://new"), nowEpochMs = 2_000L)
        assertEquals(1, s.entries.value.size)
        assertEquals("New", s.entries.value.first().title)
        assertEquals("file://new", s.entries.value.first().url)
        assertEquals(2_000L, s.entries.value.first().playedAtEpochMs)
    }

    @Test
    fun blankIdsAreIgnored() {
        val s = store()
        s.record(media("   "), nowEpochMs = 1_000L)
        assertTrue(s.entries.value.isEmpty())
    }

    @Test
    fun trimsToFiftyNewest() {
        val s = store()
        repeat(55) { index ->
            s.record(media("id-$index"), nowEpochMs = index.toLong())
        }
        assertEquals(RecentlyPlayedStore.MAX_ENTRIES, s.entries.value.size)
        assertEquals("id-54", s.entries.value.first().id)
        assertEquals("id-5", s.entries.value.last().id)
        assertTrue(s.entries.value.none { it.id in listOf("id-0", "id-1", "id-2", "id-3", "id-4") })
    }

    @Test
    fun clearRemovesHistory() {
        val s = store()
        s.record(media("a"))
        s.clear()
        assertTrue(s.entries.value.isEmpty())
    }

    @Test
    fun entriesSurviveANewStoreInstance() {
        val backing = settings()
        store(backing).record(media("a", title = "Persisted"), nowEpochMs = 42L)
        val reloaded = RecentlyPlayedStore(backing, json)
        assertEquals(1, reloaded.entries.value.size)
        assertEquals("Persisted", reloaded.entries.value.first().title)
        assertEquals(42L, reloaded.entries.value.first().playedAtEpochMs)
    }

    @Test
    fun corruptBlobLoadsAsEmpty() {
        val backing = settings()
        backing.putString(RecentlyPlayedStore.KEY, "{ this is not valid json")
        val s = RecentlyPlayedStore(backing, json)
        assertTrue(s.entries.value.isEmpty())
    }

    @Test
    fun blankBlobLoadsAsEmpty() {
        val backing = settings()
        backing.putString(RecentlyPlayedStore.KEY, "   ")
        val s = RecentlyPlayedStore(backing, json)
        assertTrue(s.entries.value.isEmpty())
    }

    @Test
    fun pureRecordDrivesTheOrderingRuleInIsolation() {
        val existing = listOf(RecentlyPlayedEntry(id = "a", playedAtEpochMs = 1L))
        val result = RecentlyPlayedStore.pureRecord(existing, media("a"), nowEpochMs = 9L, cap = 50)
        assertEquals(1, result.size)
        assertEquals(9L, result.first().playedAtEpochMs)

        val capped = RecentlyPlayedStore.pureRecord(
            items = listOf(
                RecentlyPlayedEntry("a", playedAtEpochMs = 1L),
                RecentlyPlayedEntry("b", playedAtEpochMs = 2L)
            ),
            item = media("c"),
            nowEpochMs = 3L,
            cap = 2
        )
        assertEquals(listOf("c", "a"), capped.map { it.id })
    }

    @Test
    fun pureRecordDoesNotMutateInput() {
        val existing = listOf(RecentlyPlayedEntry("a", playedAtEpochMs = 1L))
        RecentlyPlayedStore.pureRecord(existing, media("b"), nowEpochMs = 5L, cap = 50)
        assertEquals(1, existing.size)
        assertEquals("a", existing.first().id)
    }

    @Test
    fun defaultConstructorStateHasNoEntries() {
        assertNull(settings().getStringOrNull(RecentlyPlayedStore.KEY))
        assertTrue(store(settings()).entries.value.isEmpty())
    }
}
