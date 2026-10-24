/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Stores the search terms the user submitted, most recent first.
 *
 * The home feed uses the terms to mix in content related to what the user is looking for, so the
 * recommendations follow their interests instead of only their watch history.
 */
class SearchHistoryRepository(
    private val settings: Settings
) {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val _queries = MutableStateFlow(load())
    val queries = _queries.asStateFlow()

    /**
     * Records a submitted search term, moving it to the front of the list.
     */
    fun record(query: String) {
        val term = query.trim()
        if (term.length < MIN_QUERY_LENGTH) return
        if (_queries.value.firstOrNull() == term) return

        val updated = (listOf(term) + _queries.value.filterNot { it.equals(term, ignoreCase = true) })
            .take(MAX_ENTRIES)
        _queries.value = updated
        persist(updated)
    }

    /**
     * Removes all recorded search terms.
     */
    fun clear() {
        _queries.value = emptyList()
        settings.remove(KEY)
    }

    private fun load(): List<String> {
        val stored = settings.getStringOrNull(KEY) ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(String.serializer()), stored)
        } catch (exception: Exception) {
            Logger.w(
                messageString = "The search history could not be read",
                throwable = exception
            )
            emptyList()
        }
    }

    private fun persist(queries: List<String>) {
        try {
            settings.putString(KEY, json.encodeToString(ListSerializer(String.serializer()), queries))
        } catch (exception: Exception) {
            Logger.w(
                messageString = "The search history could not be saved",
                throwable = exception
            )
        }
    }

    companion object {
        private const val KEY = "search_history_v1"
        private const val MAX_ENTRIES = 12
        private const val MIN_QUERY_LENGTH = 2
    }
}
