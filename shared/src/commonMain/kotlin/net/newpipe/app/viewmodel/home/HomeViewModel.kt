/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.ServiceInfo
import net.newpipe.app.service.ContentRepository
import net.newpipe.app.service.SearchRequest
import net.newpipe.app.service.ServiceKiosk
import net.newpipe.app.service.ServiceRegistry
import net.newpipe.app.service.SearchHistoryRepository
import net.newpipe.app.service.WatchHistoryRepository
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the home screen, which shows the predefined content lists ("kiosks") of a service.
 *
 * Loading a kiosk cancels the request that is still running, so that a slow response of an
 * obsolete kiosk can never replace the content of the one the user selected last.
 */
@KoinViewModel
class HomeViewModel(
    private val repository: ContentRepository,
    private val serviceRegistry: ServiceRegistry,
    private val watchHistory: WatchHistoryRepository,
    private val searchHistory: SearchHistoryRepository
) : ViewModel() {

    private val _services = MutableStateFlow<List<ServiceInfo>>(emptyList())
    val services = _services.asStateFlow()

    private val _serviceId = MutableStateFlow(DEFAULT_SERVICE_ID)
    val serviceId = _serviceId.asStateFlow()

    private val _kiosks = MutableStateFlow<List<ServiceKiosk>>(emptyList())
    val kiosks = _kiosks.asStateFlow()

    private val _selectedKiosk = MutableStateFlow<String?>(null)
    val selectedKiosk = _selectedKiosk.asStateFlow()

    private val _items = MutableStateFlow<List<ContentItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _feed = MutableStateFlow<List<ContentItem>>(emptyList())
    val feed = _feed.asStateFlow()

    private val _feedLoading = MutableStateFlow(false)
    val feedLoading = _feedLoading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()

    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore = _canLoadMore.asStateFlow()

    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var kioskPage = 0
    private var feedJob: Job? = null
    private var feedPool: List<ContentItem> = emptyList()
    private var feedChannelUrl: String? = null
    private var feedChannelItems: List<ContentItem> = emptyList()
    private var feedSearchKey: String? = null
    private var feedSearchItems: List<ContentItem> = emptyList()

    init {
        val services = serviceRegistry.services()
        _services.value = services
        if (services.none { it.id == _serviceId.value }) {
            services.firstOrNull()?.let { _serviceId.value = it.id }
        }
        refreshKiosks()

        viewModelScope.launch {
            watchHistory.items.collect { rebuildFeed() }
        }
        viewModelScope.launch {
            searchHistory.queries.collect {
                if (_items.value.isNotEmpty()) rebuildFeed()
            }
        }
    }

    /**
     * Changes the service and loads the default kiosk of the new service.
     */
    fun selectService(id: Int) {
        if (_serviceId.value == id) return
        _serviceId.value = id
        _items.value = emptyList()
        _feed.value = emptyList()
        feedPool = emptyList()
        feedChannelUrl = null
        feedChannelItems = emptyList()
        feedSearchKey = null
        feedSearchItems = emptyList()
        kioskPage = 0
        _canLoadMore.value = true
        refreshKiosks()
    }

    /**
     * Loads the given kiosk.
     */
    fun selectKiosk(kioskId: String) {
        if (_selectedKiosk.value == kioskId) return
        _selectedKiosk.value = kioskId
        load(kioskId)
    }

    /**
     * Reloads the selected kiosk.
     */
    fun reload() {
        val kioskId = _selectedKiosk.value ?: return
        load(kioskId)
    }

    private fun refreshKiosks() {
        val kiosks = repository.kiosks(_serviceId.value)
        _kiosks.value = kiosks
        if (kiosks.isEmpty()) {
            _selectedKiosk.value = null
            _items.value = emptyList()
            _error.value = "This service does not provide any content lists"
            _loading.value = false
            return
        }
        loadWithFallback(kiosks)
    }

    private fun load(kioskId: String) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        _items.value = emptyList()
        _error.value = null
        _loading.value = true
        kioskPage = 0
        _canLoadMore.value = true

        loadJob = viewModelScope.launch {
            try {
                val items = repository.kiosk(_serviceId.value, kioskId)
                if (generation != loadGeneration) return@launch
                Logger.i(messageString = "Loaded ${items.size} items from kiosk '$kioskId'")
                _items.value = items
                kioskPage = 1
                if (items.isEmpty()) _canLoadMore.value = false
                rebuildFeed()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (generation != loadGeneration) return@launch
                Logger.e(messageString = "Loading the kiosk failed", throwable = exception)
                _error.value = exception.message ?: "The content could not be loaded"
            } finally {
                if (generation == loadGeneration) {
                    _loading.value = false
                }
            }
        }
    }

    /**
     * Appends the next page of the selected kiosk to the feed.
     *
     * The home grid calls this when the user scrolls close to the end, so the feed keeps growing
     * without a "load more" button.
     */
    fun loadMore() {
        if (_loading.value || _loadingMore.value || !_canLoadMore.value) return
        val kioskId = _selectedKiosk.value ?: return

        _loadingMore.value = true
        Logger.i(messageString = "Loading kiosk page $kioskPage of $kioskId")
        viewModelScope.launch {
            try {
                val items = repository.kioskPage(_serviceId.value, kioskId, kioskPage)
                if (items.isEmpty()) {
                    _canLoadMore.value = false
                } else {
                    kioskPage += 1
                    val known = _items.value.map { it.url }.toSet()
                    val fresh = items.filterNot { it.url in known }
                    _items.value = _items.value + fresh
                    appendToFeed(fresh)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Logger.w(
                    messageString = "Loading more kiosk content failed",
                    throwable = exception
                )
                _canLoadMore.value = false
            } finally {
                _loadingMore.value = false
            }
        }
    }

    /**
     * Loads the first kiosk that can actually be fetched.
     *
     * Services change the shape of their kiosk pages from time to time, and the extraction engine
     * cannot always follow every change at once. Rather than showing an error on the home screen
     * when the preferred list is temporarily broken, the next available list is shown. A service
     * whose lists all fail still reports the last error to the user.
     */
    private fun loadWithFallback(kiosks: List<ServiceKiosk>) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        _items.value = emptyList()
        _error.value = null
        _loading.value = true

        loadJob = viewModelScope.launch {
            var lastError: Throwable? = null
            var loaded: ServiceKiosk? = null

            for (kiosk in kiosks.take(MAX_KIOSK_ATTEMPTS)) {
                try {
                    val items = repository.kiosk(_serviceId.value, kiosk.id)
                    if (generation != loadGeneration) return@launch
                    Logger.i(messageString = "Loaded ${items.size} items from kiosk '${kiosk.id}'")
                    loaded = kiosk
                    _items.value = items
                    rebuildFeed()
                    lastError = null
                    break
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    if (generation != loadGeneration) return@launch
                    Logger.w(
                        messageString = "Kiosk '${kiosk.id}' could not be loaded",
                        throwable = exception
                    )
                    lastError = exception
                }
            }

            if (generation != loadGeneration) return@launch

            val kiosk = loaded
            if (kiosk != null) {
                _selectedKiosk.value = kiosk.id
                kioskPage = 1
                _canLoadMore.value = _items.value.isNotEmpty()
            } else {
                _error.value = lastError?.message ?: "The content could not be loaded"
            }
            _loading.value = false
        }
    }

    /**
     * Replaces the feed with a new random selection.
     *
     * The recently watched streams stay at the front, the recommendations are reshuffled.
     */
    fun shuffleFeed() {
        val watched = watchHistory.items.value
        _feed.value = (watched.take(RECENT_WATCHED) + feedPool.shuffled())
            .distinctBy { it.url }
            .take(FEED_LIMIT)
    }

    /**
     * Rebuilds the personalised feed from the history, the recorded searches and the content of
     * the selected service.
     *
     * The most recent uploads of the channel the user watched last and the results of their latest
     * searches are mixed with the current kiosk, so the feed follows what the user watches and
     * looks for. The network requests only run again when the channel or the search terms change.
     */
    private fun rebuildFeed() {
        val kioskItems = _items.value
        val watched = watchHistory.items.value
        val channelUrl = watched.firstOrNull { !it.uploaderUrl.isNullOrBlank() }?.uploaderUrl
        val terms = searchHistory.queries.value.take(SEARCH_QUERY_LIMIT)
        val searchKey = terms.joinToString("|")

        if (kioskItems.isEmpty() && watched.isEmpty()) {
            _feed.value = emptyList()
            return
        }

        if (channelUrl == feedChannelUrl && searchKey == feedSearchKey) {
            publishFeed()
            return
        }

        feedJob?.cancel()
        feedChannelUrl = channelUrl
        feedSearchKey = searchKey
        _feedLoading.value = true

        feedJob = viewModelScope.launch {
            val channelItems = if (channelUrl != null) {
                try {
                    repository.channel(_serviceId.value, channelUrl).items
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Logger.w(
                        messageString = "The feed channel could not be loaded",
                        throwable = exception
                    )
                    emptyList()
                }
            } else {
                emptyList()
            }

            val searchItems = terms.flatMap { term ->
                try {
                    repository.search(
                        SearchRequest(query = term, serviceId = _serviceId.value)
                    ).take(SEARCH_RESULTS_PER_TERM)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Logger.w(
                        messageString = "The feed search '$term' could not be loaded",
                        throwable = exception
                    )
                    emptyList()
                }
            }

            feedChannelItems = channelItems
            feedSearchItems = searchItems
            publishFeed()
            _feedLoading.value = false
        }
    }

    private fun publishFeed() {
        val watched = watchHistory.items.value
        val watchedUrls = watched.map { it.url }.toSet()

        feedPool = (feedChannelItems + feedSearchItems + _items.value)
            .filter { it.type == ContentType.STREAM && it.url !in watchedUrls }
            .distinctBy { it.url }

        _feed.value = (watched.take(RECENT_WATCHED) + feedPool.shuffled())
            .distinctBy { it.url }
            .take(FEED_LIMIT)
    }

    /**
     * Adds the content of a newly loaded kiosk page to the feed without reshuffling the entries
     * that are already on screen.
     */
    private fun appendToFeed(items: List<ContentItem>) {
        val known = (_feed.value.map { it.url } + watchHistory.items.value.map { it.url }).toSet()
        val fresh = items
            .filter { it.type == ContentType.STREAM && it.url !in known }
            .distinctBy { it.url }
        if (fresh.isEmpty()) return

        feedPool = (feedPool + fresh).distinctBy { it.url }
        _feed.value = (_feed.value + fresh.shuffled()).take(FEED_LIMIT)
    }

    companion object {
        /** YouTube, the default service of NewPipe. */
        const val DEFAULT_SERVICE_ID = 0

        /** How many content lists are tried before the home screen reports an error. */
        private const val MAX_KIOSK_ATTEMPTS = 3

        /** How many recommendations the feed can grow to while the user scrolls. */
        private const val FEED_LIMIT = 300

        /** How many recently watched streams are pinned to the front of the feed. */
        private const val RECENT_WATCHED = 4

        /** How many recent search terms personalise the feed. */
        private const val SEARCH_QUERY_LIMIT = 3

        /** How many results of each search are mixed into the feed. */
        private const val SEARCH_RESULTS_PER_TERM = 8
    }
}
