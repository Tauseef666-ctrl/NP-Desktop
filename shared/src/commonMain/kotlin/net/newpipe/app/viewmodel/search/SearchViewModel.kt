/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ServiceInfo
import net.newpipe.app.service.ContentException
import net.newpipe.app.service.ContentRepository
import net.newpipe.app.service.SearchHistoryRepository
import net.newpipe.app.service.SearchRequest
import net.newpipe.app.service.ServiceRegistry
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the search screen.
 *
 * Every search cancels the request that is still running, so that a slow response of an obsolete
 * query can never overwrite the results of a newer one.
 */
@KoinViewModel
class SearchViewModel(
    private val repository: ContentRepository,
    private val serviceRegistry: ServiceRegistry,
    private val searchHistory: SearchHistoryRepository
) : ViewModel() {

    private val _services = MutableStateFlow<List<ServiceInfo>>(emptyList())
    val services = _services.asStateFlow()

    private val _serviceId = MutableStateFlow(DEFAULT_SERVICE_ID)
    val serviceId = _serviceId.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _filter = MutableStateFlow<String?>(null)
    val filter = _filter.asStateFlow()

    private val _filters = MutableStateFlow<List<String>>(emptyList())
    val filters = _filters.asStateFlow()

    private val _items = MutableStateFlow<List<ContentItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    private var searchJob: Job? = null
    private var searchGeneration = 0
    private var nextPage = 0

    init {
        val services = serviceRegistry.services()
        _services.value = services
        if (services.none { it.id == _serviceId.value }) {
            services.firstOrNull()?.let { _serviceId.value = it.id }
        }
        refreshFilters()
    }

    /**
     * Changes the service the search runs on and repeats the current query.
     */
    fun selectService(id: Int) {
        if (_serviceId.value == id) return
        _serviceId.value = id
        refreshFilters()
        if (_query.value.isNotBlank()) search(_query.value)
    }

    /**
     * Changes the content filter and repeats the current query.
     */
    fun selectFilter(filter: String?) {
        if (_filter.value == filter) return
        _filter.value = filter
        if (_query.value.isNotBlank()) search(_query.value)
    }

    /**
     * Runs a new search and replaces the current results.
     *
     * When [debounce] is `true` the request is delayed briefly so that typing a query does not
     * start one request per keystroke. Submitting the query (for example with the Enter key) uses
     * `false` to search immediately.
     */
    fun search(query: String, debounce: Boolean = true) {
        _query.value = query
        searchJob?.cancel()
        val generation = ++searchGeneration

        if (query.isBlank()) {
            _items.value = emptyList()
            _error.value = null
            _loading.value = false
            _hasMore.value = false
            return
        }

        nextPage = 0
        _items.value = emptyList()
        _error.value = null
        _loading.value = true

        searchJob = viewModelScope.launch {
            try {
                if (debounce) delay(SEARCH_DEBOUNCE_MILLIS)
                val result = repository.search(
                    SearchRequest(
                        query = query,
                        serviceId = _serviceId.value,
                        filter = _filter.value,
                        page = 0
                    )
                )
                if (generation != searchGeneration) return@launch
                _items.value = result
                _hasMore.value = result.isNotEmpty()
                nextPage = 1
                if (!debounce && result.isNotEmpty()) {
                    searchHistory.record(query)
                }
            } catch (exception: CancellationException) {
                // A newer search replaced this one; it owns the state from here on.
                throw exception
            } catch (exception: Exception) {
                if (generation != searchGeneration) return@launch
                Logger.e(messageString = "Search failed", throwable = exception)
                _error.value = exception.message ?: "Search failed"
                _hasMore.value = false
            } finally {
                if (generation == searchGeneration) {
                    _loading.value = false
                }
            }
        }
    }

    /**
     * Loads the next page of the current search.
     */
    fun loadMore() {
        if (_loading.value || !_hasMore.value) return
        val query = _query.value
        if (query.isBlank()) return

        _loading.value = true
        searchJob = viewModelScope.launch {
            try {
                val result = repository.search(
                    SearchRequest(
                        query = query,
                        serviceId = _serviceId.value,
                        filter = _filter.value,
                        page = nextPage
                    )
                )
                if (result.isNotEmpty()) {
                    _items.value = _items.value + result
                    nextPage += 1
                } else {
                    _hasMore.value = false
                }
            } catch (exception: ContentException) {
                Logger.e(messageString = "Loading the next page failed", throwable = exception)
                _error.value = exception.message
                _hasMore.value = false
            } catch (exception: Exception) {
                Logger.e(messageString = "Loading the next page failed", throwable = exception)
                _error.value = exception.message
                _hasMore.value = false
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Reloads the list of content filters the selected service supports.
     */
    fun refreshFilters() {
        val filters = repository.availableSearchFilters(_serviceId.value)
        _filters.value = filters
        if (_filter.value != null && _filter.value !in filters) {
            _filter.value = null
        }
    }

    companion object {
        /** YouTube, the default service of NewPipe. */
        const val DEFAULT_SERVICE_ID = 0
        private const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
