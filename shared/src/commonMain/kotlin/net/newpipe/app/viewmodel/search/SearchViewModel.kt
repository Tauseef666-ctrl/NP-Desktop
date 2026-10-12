/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.provider.ChannelNavigation
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderResult
import net.newpipe.app.search.SearchHistoryStore
import net.newpipe.app.search.matchSuggestions
import org.koin.core.annotation.KoinViewModel

/**
 * UI state for the provider-backed search results area.
 *
 * [Idle] means no search has been run yet (or the last results were cleared), so the screen shows
 * recent history/suggestions instead of a result list. [Loading], [Success] and [Failure] describe
 * an in-flight or finished search.
 */
sealed interface SearchResultsState {
    data object Idle : SearchResultsState
    data object Loading : SearchResultsState
    data class Success(val items: List<ProviderContentItem>) : SearchResultsState
    data class Failure(val reason: ProviderFailureReason) : SearchResultsState
}

/**
 * Drives the Search screen: exposes the persisted recent-query history, derives live suggestions
 * from it, and runs provider-backed searches through [ProviderContentRepository].
 *
 * Recording/clearing delegates to the shared [SearchHistoryStore].
 *
 * Overlapping searches are resolved "latest wins": the query of the most recent [search] call is
 * remembered in [activeQuery], and a result whose query is no longer the active one is discarded
 * instead of being published. This keeps a slow earlier request from clobbering a newer one.
 */
@KoinViewModel
class SearchViewModel(
    private val history: SearchHistoryStore,
    private val repository: ProviderContentRepository,
    private val channelNavigation: ChannelNavigation
) : ViewModel() {

    /** Newest-first recent search queries; emits whenever a query is recorded or cleared. */
    val historyEntries: StateFlow<List<String>> = history.entries

    private val _results = MutableStateFlow<SearchResultsState>(SearchResultsState.Idle)

    /** Provider-backed results for the most recently submitted query. */
    val results: StateFlow<SearchResultsState> = _results.asStateFlow()

    private var activeQuery: String? = null

    /** Record [query] as the most recent search (blank input is ignored). */
    fun record(query: String) = history.record(query)

    /** Forget the entire search history. */
    fun clearHistory() = history.clear()

    /** Live suggestions for [query] derived from the persisted history. */
    fun suggestions(query: String): List<String> = matchSuggestions(query, history.entries.value)

    /**
     * Run a provider-backed search for [query]. Blank input is ignored and recurrence is recorded.
     * Publishes [SearchResultsState.Loading] immediately, then maps the provider outcome to
     * [SearchResultsState.Success] or [SearchResultsState.Failure]. A result is only published while
     * its query is still the [activeQuery], so the newest search always wins.
     */
    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        history.record(trimmed)
        activeQuery = trimmed
        _results.value = SearchResultsState.Loading
        viewModelScope.launch {
            val result = repository.search(trimmed)
            if (activeQuery != trimmed) return@launch
            _results.value = when (result) {
                is ProviderResult.Success -> SearchResultsState.Success(result.value.items)
                is ProviderResult.Failure -> SearchResultsState.Failure(result.reason)
            }
        }
    }

    /** Remember the channel to open when the user taps a search result. */
    fun selectChannel(url: String, name: String) = channelNavigation.select(url, name)

    /** Reset the results area back to [SearchResultsState.Idle] and abandon any in-flight search. */
    fun clearResults() {
        activeQuery = null
        _results.value = SearchResultsState.Idle
    }
}
