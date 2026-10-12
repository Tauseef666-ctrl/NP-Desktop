/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Small reusable helper a screen's view model composes to play provider items. It tracks the state of
 * the most recent [play] request so the UI can show progress, navigate when playback starts, or show
 * an honest error. Overlapping requests are collapsed: [play] is ignored while one is in flight.
 *
 * All work runs in the caller-supplied [scope] (normally `viewModelScope`), so it is cancelled with
 * the screen and never leaks.
 */
class ProviderPlaybackSession(
    private val playback: MediaPlayback,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<ProviderPlaybackState>(ProviderPlaybackState.Idle)

    /** The current playback-request state; collect from the UI. */
    val state: StateFlow<ProviderPlaybackState> = _state.asStateFlow()

    /** Resolve and start [item] in [scope]. Ignored while a request is already in flight. */
    fun play(item: ProviderContentItem) {
        if (_state.value == ProviderPlaybackState.Loading) return
        _state.value = ProviderPlaybackState.Loading
        scope.launch {
            _state.value = when (val result = playback.play(item)) {
                is ProviderResult.Success -> ProviderPlaybackState.Launched
                is ProviderResult.Failure -> ProviderPlaybackState.Failure(result.reason)
            }
        }
    }

    /** Return to [ProviderPlaybackState.Idle] after the UI has handled a launched/failed request. */
    fun acknowledge() {
        _state.value = ProviderPlaybackState.Idle
    }
}
