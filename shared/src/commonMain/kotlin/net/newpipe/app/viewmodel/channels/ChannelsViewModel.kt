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
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
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

    /** The channel content loaded successfully. */
    data class Success(val items: List<ProviderContentItem>) : ChannelsState

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
    private val channelNav: ChannelNavigation
) : ViewModel() {

    /** The channel selected for display, or null when none was chosen. */
    val selection: StateFlow<ChannelNavigation.Selection?> = channelNav.selected

    private val _state = MutableStateFlow<ChannelsState>(ChannelsState.Idle)

    /** The current screen state. */
    val state: StateFlow<ChannelsState> = _state.asStateFlow()

    private var loadJob: Job? = null

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
                is ProviderResult.Success -> ChannelsState.Success(result.value.items)
                is ProviderResult.Failure -> ChannelsState.Failure(result.reason)
            }
        }
    }
}
