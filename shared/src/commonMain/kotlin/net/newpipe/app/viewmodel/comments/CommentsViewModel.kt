/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.provider.CommentItem
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderResult
import org.koin.core.annotation.KoinViewModel

/**
 * What the comments surface can honestly show for the currently playing stream.
 */
sealed interface CommentsUiState {
    /** No stream is playing (or nothing was requested). */
    data object Idle : CommentsUiState

    /** Comments are being fetched. */
    data object Loading : CommentsUiState

    /** Comments loaded successfully. */
    data class Success(val comments: List<CommentItem>) : CommentsUiState

    /** The provider could not supply comments. */
    data class Unavailable(val reason: ProviderFailureReason) : CommentsUiState
}

/**
 * Loads top-level comments for the currently playing stream through [ProviderContentRepository].
 */
@KoinViewModel
class CommentsViewModel(
    private val repository: ProviderContentRepository
) : ViewModel() {

    private val _state = MutableStateFlow<CommentsUiState>(CommentsUiState.Idle)

    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var currentId: String? = null

    fun load(mediaId: String?) {
        if (mediaId.isNullOrBlank()) {
            loadJob?.cancel()
            currentId = null
            _state.value = CommentsUiState.Idle
            return
        }
        if (mediaId == currentId && (_state.value is CommentsUiState.Success || loadJob?.isActive == true)) {
            return
        }
        loadJob?.cancel()
        currentId = mediaId
        _state.value = CommentsUiState.Loading
        loadJob = viewModelScope.launch {
            val result = repository.comments(mediaId)
            if (currentId != mediaId) return@launch
            _state.value = when (result) {
                is ProviderResult.Success -> CommentsUiState.Success(result.value)
                is ProviderResult.Failure -> CommentsUiState.Unavailable(result.reason)
            }
        }
    }

    fun retry() {
        val mediaId = currentId ?: return
        loadJob?.cancel()
        load(mediaId)
    }
}
