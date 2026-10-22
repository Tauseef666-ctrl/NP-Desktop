/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The playback controller of the iOS application.
 *
 * The iOS application is not able to play media yet, therefore this controller only reports that
 * playback is not available. See `docs/ARCHITECTURE.md` section 4.1.
 */
class IosPlaybackController : PlaybackController {

    private val _state = MutableStateFlow(
        PlaybackState(error = "Playback is not available on this platform")
    )
    override val state = _state.asStateFlow()

    override fun play(request: PlaybackRequest) = Unit

    override fun resume() = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun setVolume(volume: Int) = Unit

    override fun setMuted(muted: Boolean) = Unit

    override fun setSpeed(speed: Float) = Unit

    override fun release() = Unit
}

/**
 * Creates the playback controller of the iOS application.
 */
actual fun createPlaybackController(): PlaybackController = IosPlaybackController()
