/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The user interface mode of the player.
 *
 * While the player is immersive the shell hides its navigation rail, so that the video and the
 * player controls can use the whole window.
 */
class PlayerUiState {

    private val _fullscreen = MutableStateFlow(false)
    val fullscreen = _fullscreen.asStateFlow()

    /** The mark of the last fullscreen or app-fullscreen toggle, to swallow key auto-repeat. */
    private var lastFullscreenToggleMark: TimeSource.Monotonic.ValueTimeMark? = null

    /**
     * Ignores a fullscreen toggle that arrives right after the previous one. Holding F or F11
     * makes the operating system repeat the key many times per second; each repeat would flip
     * the placement back and forth and leave the window frame in a glitched intermediate state.
     */
    private fun togglesTooFast(): Boolean {
        val now = TimeSource.Monotonic.markNow()
        val tooFast = lastFullscreenToggleMark?.let { now - it < FULLSCREEN_TOGGLE_DEBOUNCE_MS } == true
        if (!tooFast) lastFullscreenToggleMark = now
        return tooFast
    }

    fun setFullscreen(value: Boolean) { _fullscreen.value = value }

    fun toggleFullscreen() {
        if (togglesTooFast()) return
        _fullscreen.value = !_fullscreen.value
    }


    private val _appFullscreen = MutableStateFlow(false)

    /**
     * Whether the whole app window is fullscreen (F11), independent of any playback.
     */
    val appFullscreen = _appFullscreen.asStateFlow()

    /**
     * Shows or hides the app window fullscreen mode (F11).
     */
    fun setAppFullscreen(value: Boolean) {
        _appFullscreen.value = value
    }

    /**
     * Switches between the windowed and the fullscreen app mode (F11).
     */
    fun toggleAppFullscreen() {
        if (togglesTooFast()) return
        _appFullscreen.value = !_appFullscreen.value
    }


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

    private val _hideCursor = MutableStateFlow(false)

    /**
     * Whether the mouse pointer should currently be hidden over the playing video. The desktop
     * fullscreen player hides the cursor together with the automatically hidden controls and
     * shows both again at the first real pointer movement.
     */
    val hideCursor = _hideCursor.asStateFlow()

    /**
     * Shows or hides the mouse pointer over the fullscreen player.
     */
    fun setHideCursor(value: Boolean) {
        _hideCursor.value = value
    }

    private companion object {
        /**
         * The minimum pause between two accepted fullscreen toggles: longer than the OS key
         * auto-repeat interval, but short enough to never get in the way of real presses.
         */
        private val FULLSCREEN_TOGGLE_DEBOUNCE_MS = 250.milliseconds
    }
}
