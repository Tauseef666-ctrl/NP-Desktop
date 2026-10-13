/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.channels

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
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderPlaybackSession
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.ProviderResult
import org.koin.core.annotation.KoinViewModel

/**
 * What the channel screen can honestly show. [Idle] means no channel was selected, [Failure] carries
 * a machine-readable [ProviderFailureReason] so the UI can map it to localized text.
 */
sealed interface ChannelsState {
    /** No channel is selected; there is nothing to load. */
    data object Idle : ChannelsState

    /** A channel is being loaded. */
    data object Loading : ChannelsState

    /**
     * The channel content loaded successfully. [nextPageToken] continues the list (null means
     * exhausted) and [loadingMore] is true while a follow-up page request is in flight.
     */
    data class Success(
        val items: List<ProviderContentItem>,
        val nextPageToken: String? = null,
        val loadingMore: Boolean = false
    ) : ChannelsState

    /** Loading failed for [reason]. */
    data class Failure(val reason: ProviderFailureReason) : ChannelsState
}

/**
 * Drives the channel screen. The channel to show comes from [ChannelNavigation] (the app keeps its
 * navigation state in singletons rather than route arguments), so this view model reads the current
 * selection and loads its content through [ProviderContentRepository].
 */
@KoinViewModel
class ChannelsViewModel(
    private val repository: ProviderContentRepository,
    private val channelNav: ChannelNavigation,
    private val mediaPlayback: MediaPlayback
) : ViewModel() {

    /** The channel selected for display, or null when none was chosen. */
    val selection: StateFlow<ChannelNavigation.Selection?> = channelNav.selected

    private val _state = MutableStateFlow<ChannelsState>(ChannelsState.Idle)

    /** The current screen state. */
    val state: StateFlow<ChannelsState> = _state.asStateFlow()

    private val playbackSession = ProviderPlaybackSession(mediaPlayback, viewModelScope)

    /** The state of the most recent play request, for inline progress/error and navigation. */
    val playbackState: StateFlow<ProviderPlaybackState> = playbackSession.state

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        load()
    }

    /**
     * Load the currently selected channel. With no selection the state stays [ChannelsState.Idle];
     * otherwise the content is requested and the state becomes [ChannelsState.Success] or
     * [ChannelsState.Failure]. A load already in flight for the same selection is left alone so a
     * rapid double-trigger cannot race.
     */
    fun load() {
        val selected = channelNav.selected.value
        if (selected == null) {
            loadJob?.cancel()
            _state.value = ChannelsState.Idle
            return
        }
        if (loadJob?.isActive == true) return
        startLoad(selected.url)
    }

    /**
     * Retry after a failure (or when the selection changed). Any in-flight load is cancelled first so
     * the retry really re-queries the provider instead of being skipped.
     */
    fun retry() {
        val selected = channelNav.selected.value
        if (selected == null) {
            loadJob?.cancel()
            _state.value = ChannelsState.Idle
            return
        }
        loadJob?.cancel()
        startLoad(selected.url)
    }

    private fun startLoad(url: String) {
        _state.value = ChannelsState.Loading
        loadJob = viewModelScope.launch {
            _state.value = when (val result = repository.channel(url)) {
                is ProviderResult.Success -> ChannelsState.Success(
                    items = result.value.items,
                    nextPageToken = result.value.nextPageToken,
                    loadingMore = false
                )

                is ProviderResult.Failure -> ChannelsState.Failure(result.reason)
            }
        }
    }

    /**
     * Request the next page of the loaded channel. Only runs while the state is
     * [ChannelsState.Success] with a non-blank [ChannelsState.Success.nextPageToken], a current
     * selection and no load-more request already in flight, so overlapping requests cannot
     * interleave. On success the new items are appended and the token is refreshed; on failure the
     * already-loaded items are kept, only [ChannelsState.Success.loadingMore] is cleared and the
     * token is kept so the user can try again.
     */
    fun loadMore() {
        val current = _state.value
        if (current !is ChannelsState.Success) return
        val pageToken = current.nextPageToken
        if (pageToken.isNullOrBlank()) return
        if (current.loadingMore) return
        if (loadMoreJob?.isActive == true) return
        val url = channelNav.selected.value?.url ?: return

        _state.value = current.copy(loadingMore = true)
        loadMoreJob = viewModelScope.launch {
            when (val result = repository.channel(url, pageToken)) {
                is ProviderResult.Success -> {
                    val latest = _state.value as? ChannelsState.Success ?: return@launch
                    _state.value = latest.copy(
                        items = latest.items + result.value.items,
                        nextPageToken = result.value.nextPageToken,
                        loadingMore = false
                    )
                }

                is ProviderResult.Failure -> {
                    val latest = _state.value as? ChannelsState.Success ?: return@launch
                    // Honest outcome: the pages already shown stay available, only the load-more
                    // state is released so the user can either scroll the list or try again.
                    _state.value = latest.copy(loadingMore = false)
                }
            }
        }
    }

    /** Resolve and start [item] through the shared playback session. */
    fun play(item: ProviderContentItem) = playbackSession.play(item)

    /** Return playback state to idle after the UI handled a launched/failed request. */
    fun acknowledgePlayback() = playbackSession.acknowledge()
}
