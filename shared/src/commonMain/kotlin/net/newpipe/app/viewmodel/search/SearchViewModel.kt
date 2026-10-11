/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.search

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.search.SearchHistoryStore
import net.newpipe.app.search.matchSuggestions
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the Search screen: exposes the persisted recent-query history and derives live suggestions
 * from it. Recording/clearing delegates to the shared [SearchHistoryStore].
 */
@KoinViewModel
class SearchViewModel(
    private val history: SearchHistoryStore
) : ViewModel() {

    /** Newest-first recent search queries; emits whenever a query is recorded or cleared. */
    val historyEntries: StateFlow<List<String>> = history.entries

    /** Record [query] as the most recent search (blank input is ignored). */
    fun record(query: String) = history.record(query)

    /** Forget the entire search history. */
    fun clearHistory() = history.clear()

    /** Live suggestions for [query] derived from the persisted history. */
    fun suggestions(query: String): List<String> = matchSuggestions(query, history.entries.value)
}
