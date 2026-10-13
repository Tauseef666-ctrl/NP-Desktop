/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.trending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.provider.ChannelNavigation
import net.newpipe.app.provider.MediaPlayback
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentKind
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderPlaybackSession
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.ProviderResult
import org.koin.core.annotation.KoinViewModel

/** What the trending screen can honestly show right now. */
sealed interface TrendingState {
    /** A ranked page is being requested from the provider. */
    data object Loading : TrendingState

    /**
     * The provider delivered a ranked page. [nextPageToken] continues the list (null means
     * exhausted) and [loadingMore] is true while a follow-up page request is in flight.
     */
    data class Success(
        val items: List<ProviderContentItem>,
        val nextPageToken: String? = null,
        val loadingMore: Boolean = false
    ) : TrendingState

    /** Loading failed with a machine-readable [reason] for localized, honest UI text. */
    data class Failure(val reason: ProviderFailureReason) : TrendingState
}

/**
 * Drives the Trending screen. The provider's ranked page is requested once on construction and
 * exposed as [state]; retrying after a failure re-queries the provider. Channel navigation state
 * lives in the app-level [ChannelNavigation] singleton, so opening a channel remembers the
 * selection here and the screen just navigates away.
 */
@KoinViewModel
class TrendingViewModel(
    private val repository: ProviderContentRepository,
    private val channelNav: ChannelNavigation,
    private val mediaPlayback: MediaPlayback
) : ViewModel() {

    private val _state = MutableStateFlow<TrendingState>(TrendingState.Loading)

    /** The current trending screen state. */
    val state: StateFlow<TrendingState> = _state.asStateFlow()

    private val playbackSession = ProviderPlaybackSession(mediaPlayback, viewModelScope)

    /** The state of the most recent play request, for inline progress/error and navigation. */
    val playbackState: StateFlow<ProviderPlaybackState> = playbackSession.state

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        load()
    }

    /**
     * Request the provider's trending page. Sets [TrendingState.Loading] first and ends in
     * [TrendingState.Success] or [TrendingState.Failure]. A load already in flight is ignored so a
     * rapid double-trigger cannot race.
     */
    fun load() {
        if (loadJob?.isActive == true) return
        _state.value = TrendingState.Loading
        loadJob = viewModelScope.launch {
            _state.value = when (val result = repository.trending()) {
                is ProviderResult.Success -> TrendingState.Success(
                    items = result.value.items,
                    nextPageToken = result.value.nextPageToken,
                    loadingMore = false
                )

                is ProviderResult.Failure -> TrendingState.Failure(result.reason)
            }
        }
    }

    /** Retry the load after a failure; re-queries the provider. */
    fun retry() = load()

    /**
     * Request the next page of the ranked listing. Only runs while the state is
     * [TrendingState.Success] with a non-blank [TrendingState.Success.nextPageToken] and no load-more
     * request already in flight, so overlapping requests cannot interleave. On success the new items
     * are appended and the token is refreshed; on failure the already-loaded items are kept, only
     * [TrendingState.Success.loadingMore] is cleared and the token is kept so the user can try again.
     */
    fun loadMore() {
        val current = _state.value
        if (current !is TrendingState.Success) return
        val pageToken = current.nextPageToken
        if (pageToken.isNullOrBlank()) return
        if (current.loadingMore) return
        if (loadMoreJob?.isActive == true) return

        _state.value = current.copy(loadingMore = true)
        loadMoreJob = viewModelScope.launch {
            when (val result = repository.trending(pageToken)) {
                is ProviderResult.Success -> {
                    val latest = _state.value as? TrendingState.Success ?: return@launch
                    _state.value = latest.copy(
                        items = latest.items + result.value.items,
                        nextPageToken = result.value.nextPageToken,
                        loadingMore = false
                    )
                }

                is ProviderResult.Failure -> {
                    val latest = _state.value as? TrendingState.Success ?: return@launch
                    // Honest outcome: the pages already shown stay available, only the load-more
                    // state is released so the user can either scroll the list or try again.
                    _state.value = latest.copy(loadingMore = false)
                }
            }
        }
    }

    /**
     * Remember the channel behind [item] for the channel screen: its own URL when [item] is a
     * [ProviderContentKind.CHANNEL], otherwise the uploader's URL when known. Returns true when a
     * navigable channel was selected (streams without an uploader URL select nothing).
     */
    fun selectChannel(item: ProviderContentItem): Boolean {
        val target = when {
            item.kind == ProviderContentKind.CHANNEL -> item.url to item.title
            item.uploaderUrl != null -> item.uploaderUrl to (item.uploader ?: item.title)
            else -> return false
        }
        return if (target.first.isBlank()) {
            false
        } else {
            channelNav.select(target.first, target.second)
            true
        }
    }

    /** Resolve and start [item] through the shared playback session. */
    fun play(item: ProviderContentItem) = playbackSession.play(item)

    /** Return playback state to idle after the UI handled a launched/failed request. */
    fun acknowledgePlayback() = playbackSession.acknowledge()
}