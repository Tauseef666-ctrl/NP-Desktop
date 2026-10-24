/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user interface mode of the player.
 *
 * While the player is immersive the shell hides its navigation rail, so that the video and the
 * player controls can use the whole window.
 */
class PlayerUiState {

    private val _fullscreen = MutableStateFlow(false)
    val fullscreen = _fullscreen.asStateFlow()

    fun setFullscreen(value: Boolean) { _fullscreen.value = value }
    fun toggleFullscreen() { _fullscreen.value = !_fullscreen.value }


    private val _immersive = MutableStateFlow(false)

    /**
     * Whether the player currently uses the whole window.
     */
    val immersive = _immersive.asStateFlow()

    /**
     * Shows or hides the immersive player mode.
     */
    fun setImmersive(value: Boolean) {
        _immersive.value = value
    }

    /**
     * Switches between the normal and the immersive player mode.
     */
    fun toggleImmersive() {
        _immersive.value = !_immersive.value
    }

    private val _audioOnly = MutableStateFlow(false)

    /**
     * Whether the player currently plays audio only, without video output.
     */
    val audioOnly = _audioOnly.asStateFlow()

    /**
     * Shows or hides the audio-only mode.
     */
    fun setAudioOnly(value: Boolean) {
        _audioOnly.value = value
    }

    /**
     * Switches between the normal and the audio-only player mode.
     */
    fun toggleAudioOnly() {
        _audioOnly.value = !_audioOnly.value
    }
}
