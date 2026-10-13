/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.streaminfo

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
import net.newpipe.app.provider.StreamMetadata
import org.koin.core.annotation.KoinViewModel

/**
 * What the stream info surface can honestly show for the currently playing stream.
 */
sealed interface StreamMetadataState {
    /** No stream is playing (or nothing was requested). */
    data object Idle : StreamMetadataState

    /** Metadata is being fetched. */
    data object Loading : StreamMetadataState

    /** Metadata loaded successfully. */
    data class Success(val metadata: StreamMetadata) : StreamMetadataState

    /** The provider could not supply metadata (no provider / unsupported / error). */
    data class Unavailable(val reason: ProviderFailureReason) : StreamMetadataState
}

/**
 * Loads descriptive [StreamMetadata] for the currently playing stream through
 * [ProviderContentRepository]. The stream is identified by its [load] `mediaId` (page URL); a newer
 * request always wins over an older in-flight one.
 */
@KoinViewModel
class StreamMetadataViewModel(
    private val repository: ProviderContentRepository
) : ViewModel() {

    private val _state = MutableStateFlow<StreamMetadataState>(StreamMetadataState.Idle)

    /** The current metadata state. */
    val state: StateFlow<StreamMetadataState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var currentId: String? = null

    /**
     * Load metadata for the stream at [mediaId]. A blank/null id resets to [StreamMetadataState.Idle];
     * a repeated id whose metadata is already [StreamMetadataState.Success] (or is still loading) is a
     * no-op, otherwise the previous request is cancelled and a new one started.
     */
    fun load(mediaId: String?) {
        if (mediaId.isNullOrBlank()) {
            loadJob?.cancel()
            currentId = null
            _state.value = StreamMetadataState.Idle
            return
        }
        if (mediaId == currentId &&
            (_state.value is StreamMetadataState.Success || loadJob?.isActive == true)
        ) {
            return
        }
        loadJob?.cancel()
        currentId = mediaId
        _state.value = StreamMetadataState.Loading
        loadJob = viewModelScope.launch {
            val result = repository.streamMetadata(mediaId)
            if (currentId != mediaId) return@launch
            _state.value = when (result) {
                is ProviderResult.Success -> StreamMetadataState.Success(result.value)
                is ProviderResult.Failure -> StreamMetadataState.Unavailable(result.reason)
            }
        }
    }

    /** Re-load metadata for the current stream after a failure. */
    fun retry() {
        val mediaId = currentId ?: return
        loadJob?.cancel()
        load(mediaId)
    }
}
