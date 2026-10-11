/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.search

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton

/** Serializable snapshot of the whole search-query history. */
@Serializable
data class SearchHistorySnapshot(
    val queries: List<String> = emptyList()
)

/**
 * Persists recent search queries (newest first, capped at [MAX_ENTRIES]) so the search screen can
 * offer "Recent searches". Mirrors [net.newpipe.app.player.RecentlyPlayedStore] storage
 * conventions: a blank or corrupt blob decodes to an empty history rather than throwing.
 *
 * [entries] is a hot [StateFlow] so the UI updates the instant a query is recorded or cleared.
 */
@Singleton
class SearchHistoryStore(
    private val settings: Settings,
    private val json: Json
) {

    private val key = KEY

    private val _entries = MutableStateFlow(loadPersisted())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    /**
     * Record [query] as the newest search: the text is trimmed, blank input is ignored, an existing
     * case-insensitive match is replaced and moved to the front (keeping the newest casing), and the
     * list is trimmed to [MAX_ENTRIES].
     */
    fun record(query: String) {
        val updated = pureAdd(_entries.value, query, MAX_ENTRIES)
        if (updated == _entries.value) return
        _entries.value = updated
        persist(updated)
    }

    /** Forget the entire search history (privacy/clear-history). */
    fun clear() {
        _entries.value = emptyList()
        settings.remove(key)
    }

    private fun loadPersisted(): List<String> {
        val raw = settings.getStringOrNull(key) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<SearchHistorySnapshot>(raw).queries
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun persist(queries: List<String>) {
        settings.putString(key, json.encodeToString(SearchHistorySnapshot(queries)))
    }

    companion object {
        const val KEY = "npp_search_history"

        /** Maximum number of remembered queries; older entries fall off the end. */
        const val MAX_ENTRIES = 15

        /**
         * Pure add used by [record]: trims [query], ignores blanks, de-duplicates case-insensitively
         * (the fresh query wins and moves to the front, keeping its newest casing) and trims to
         * [cap]. Extracted so the ordering rule is unit-testable.
         */
        fun pureAdd(items: List<String>, query: String, cap: Int): List<String> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return items
            val withoutDuplicate = items.filterNot { it.equals(trimmed, ignoreCase = true) }
            val limited = if (cap < 0) 0 else cap
            return (listOf(trimmed) + withoutDuplicate).take(limited)
        }
    }
}
