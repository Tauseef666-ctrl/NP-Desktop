/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.provider.MediaPlayback
import net.newpipe.app.provider.PlaylistNavigation
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderPlaybackSession
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.ProviderResult
import org.koin.core.annotation.KoinViewModel

/** What the playlist screen can honestly show right now. */
sealed interface PlaylistState {

    /** No playlist is selected; there is nothing to load. */
    data object Idle : PlaylistState

    /** The selected playlist is being loaded. */
    data object Loading : PlaylistState

    /**
     * The playlist loaded successfully. [nextPageToken] continues the list (null means exhausted)
     * and [loadingMore] is true while a follow-up page request is in flight for the UI to disable the
     * load-more action.
     */
    data class Success(
        val items: List<ProviderContentItem>,
        val nextPageToken: String?,
        val loadingMore: Boolean
    ) : PlaylistState

    /** Loading failed for [reason]. */
    data class Failure(val reason: ProviderFailureReason) : PlaylistState
}

/**
 * Drives the provider playlist screen. The playlist to show comes from [PlaylistNavigation] (the app
 * keeps navigation state in singletons rather than route arguments), so this view model reads the
 * current selection and loads its content through [ProviderContentRepository], appending follow-up
 * pages via [loadMore] when the provider supplies a next-page token.
 */
@KoinViewModel
class PlaylistViewModel(
    private val repository: ProviderContentRepository,
    private val playlistNav: PlaylistNavigation,
    private val mediaPlayback: MediaPlayback
) : ViewModel() {

    /** The playlist selected for display, or null when none was chosen yet. */
    val selection: StateFlow<PlaylistNavigation.Selection?> = playlistNav.selected

    private val _state = MutableStateFlow<PlaylistState>(PlaylistState.Idle)

    /** The current playlist screen state. */
    val state: StateFlow<PlaylistState> = _state.asStateFlow()

    private val playbackSession = ProviderPlaybackSession(mediaPlayback, viewModelScope)

    /** The state of the most recent play request, for inline progress/error and navigation. */
    val playbackState: StateFlow<ProviderPlaybackState> = playbackSession.state

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        load()
    }

    /**
     * Load the currently selected playlist. With no selection the state stays [PlaylistState.Idle];
     * otherwise the content is requested and the state becomes [PlaylistState.Success] or
     * [PlaylistState.Failure]. A load already in flight is left alone so a rapid double-trigger
     * cannot race.
     */
    fun load() {
        val selected = playlistNav.selected.value
        if (selected == null) {
            loadJob?.cancel()
            _state.value = PlaylistState.Idle
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
        val selected = playlistNav.selected.value
        if (selected == null) {
            loadJob?.cancel()
            _state.value = PlaylistState.Idle
            return
        }
        loadJob?.cancel()
        startLoad(selected.url)
    }

    /**
     * Request the next page of the loaded playlist. Only runs while the state is
     * [PlaylistState.Success] with a non-blank [PlaylistState.Success.nextPageToken] and no load-more
     * request already in flight, so overlapping requests cannot interleave. On success the new items
     * are appended and the token is refreshed; on failure the already-loaded items are kept (the list
     * the user sees is not lost), only [PlaylistState.Success.loadingMore] is cleared and the token is
     * kept so the user can try again.
     */
    fun loadMore() {
        val current = _state.value
        if (current !is PlaylistState.Success) return
        val pageToken = current.nextPageToken
        if (pageToken.isNullOrBlank()) return
        if (current.loadingMore) return
        if (loadMoreJob?.isActive == true) return
        val url = playlistNav.selected.value?.url ?: return

        _state.value = current.copy(loadingMore = true)
        loadMoreJob = viewModelScope.launch {
            when (val result = repository.playlist(url, pageToken)) {
                is ProviderResult.Success -> {
                    val latest = _state.value as? PlaylistState.Success ?: return@launch
                    _state.value = latest.copy(
                        items = latest.items + result.value.items,
                        nextPageToken = result.value.nextPageToken,
                        loadingMore = false
                    )
                }

                is ProviderResult.Failure -> {
                    val latest = _state.value as? PlaylistState.Success ?: return@launch
                    // Honest outcome: the pages already shown stay available, only the load-more
                    // state is released so the user can either scroll the list or try again.
                    _state.value = latest.copy(loadingMore = false)
                }
            }
        }
    }

    private fun startLoad(url: String) {
        _state.value = PlaylistState.Loading
        loadJob = viewModelScope.launch {
            _state.value = when (val result = repository.playlist(url)) {
                is ProviderResult.Success -> PlaylistState.Success(
                    items = result.value.items,
                    nextPageToken = result.value.nextPageToken,
                    loadingMore = false
                )

                is ProviderResult.Failure -> PlaylistState.Failure(result.reason)
            }
        }
    }

    /** Resolve and start [item] through the shared playback session. */
    fun play(item: ProviderContentItem) = playbackSession.play(item)

    /** Return playback state to idle after the UI handled a launched/failed request. */
    fun acknowledgePlayback() = playbackSession.acknowledge()
}