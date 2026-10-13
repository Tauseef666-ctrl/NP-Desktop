/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.captions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderResult
import net.newpipe.app.provider.SubtitleTrack
import org.koin.core.annotation.KoinViewModel

/**
 * What the captions surface can honestly show for the currently playing stream.
 */
sealed interface CaptionsState {
    /** No stream is playing (or nothing was requested). */
    data object Idle : CaptionsState

    /** Caption tracks are being fetched. */
    data object Loading : CaptionsState

    /** The stream was read; [tracks] may be empty when the stream simply has no captions. */
    data class Success(val tracks: List<SubtitleTrack>) : CaptionsState

    /** The provider could not supply captions (no provider / unsupported / error). */
    data class Unavailable(val reason: ProviderFailureReason) : CaptionsState
}

/**
 * Lists the available subtitle/caption tracks of the currently playing stream through
 * [ProviderContentRepository]. The stream is identified by its [load] `mediaId` (page URL); a newer
 * request always wins over an older in-flight one.
 */
@KoinViewModel
class CaptionsViewModel(
    private val repository: ProviderContentRepository
) : ViewModel() {

    private val _state = MutableStateFlow<CaptionsState>(CaptionsState.Idle)

    /** The current captions state. */
    val state: StateFlow<CaptionsState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var currentId: String? = null

    /**
     * Load the caption tracks for the stream at [mediaId]. A blank/null id resets to
     * [CaptionsState.Idle]; a repeated id whose tracks are already [CaptionsState.Success] (or is
     * still loading) is a no-op, otherwise the previous request is cancelled and a new one started.
     */
    fun load(mediaId: String?) {
        if (mediaId.isNullOrBlank()) {
            loadJob?.cancel()
            currentId = null
            _state.value = CaptionsState.Idle
            return
        }
        if (mediaId == currentId &&
            (_state.value is CaptionsState.Success || loadJob?.isActive == true)
        ) {
            return
        }
        loadJob?.cancel()
        currentId = mediaId
        _state.value = CaptionsState.Loading
        loadJob = viewModelScope.launch {
            val result = repository.subtitles(mediaId)
            if (currentId != mediaId) return@launch
            _state.value = when (result) {
                is ProviderResult.Success -> CaptionsState.Success(result.value)
                is ProviderResult.Failure -> CaptionsState.Unavailable(result.reason)
            }
        }
    }

    /** Re-load the caption tracks for the current stream after a failure. */
    fun retry() {
        val mediaId = currentId ?: return
        loadJob?.cancel()
        load(mediaId)
    }
}
