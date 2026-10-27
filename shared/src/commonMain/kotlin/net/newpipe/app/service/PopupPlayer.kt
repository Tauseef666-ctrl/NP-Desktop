/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.model.MediaStream

/**
 * The request to show the current video in a small always-on-top popup window.
 *
 * The popup is a separate desktop window, so the request lives outside of the composition. The
 * popup shares the playback controller with the main window: closing the popup stops the playback.
 */
object PopupPlayer {

    data class State(
        val title: String,
        val thumbnailUrl: String?,
        val videoStreams: List<MediaStream>,
        val selectedVideo: MediaStream?,
        val canPlay: Boolean,
        val onPlay: () -> Unit,
        val onSelectVideo: (MediaStream) -> Unit
    )

    private val _state = MutableStateFlow<State?>(null)
    val state = _state.asStateFlow()

    private var lastState: State? = null

    /**
     * Opens the popup with the given state.
     */
    fun open(state: State) {
        lastState = state
        _state.value = state
    }

    /**
     * Closes the popup.
     */
    fun close() {
        _state.value = null
    }

    /**
     * Opens the popup with the last state, or closes it when it is already open.
     */
    fun toggle() {
        if (_state.value != null) {
            close()
        } else {
            lastState?.let { open(it) }
        }
    }
}
