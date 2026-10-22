/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.Component

/**
 * Provides the native video output of a player, so that a user interface can embed it.
 */
interface VideoSurfaceProvider {

    /**
     * The component that renders the video, or `null` when the player has no video output.
     */
    val videoComponent: Component?
}

/**
 * Plays media with libVLC, using the VLCJ bindings.
 *
 * NewPipe usually extracts a video stream without audio plus a separate audio only stream. Both
 * are handed to libVLC as a media with an additional audio slave, so that the audio is played
 * even though the video stream itself carries no audio.
 */
class VlcPlaybackController : PlaybackController, VideoSurfaceProvider {

    private val component: EmbeddedMediaPlayerComponent? = createComponent()

    private val player: MediaPlayer? = component?.mediaPlayer()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(PlaybackState())
    override val state = _state.asStateFlow()

    private var ticker: Job? = null

    override val videoComponent: Component?
        get() = component?.videoSurfaceComponent()

    init {
        val player = player
        if (player == null) {
            _state.update { it.copy(error = "libVLC is not available") }
        } else {
            player.events().addMediaPlayerEventListener(
                object : MediaPlayerEventAdapter() {
                    override fun playing(mediaPlayer: MediaPlayer) {
                        _state.update { it.copy(playing = true, loading = false) }
                    }

                    override fun paused(mediaPlayer: MediaPlayer) {
                        _state.update { it.copy(playing = false) }
                    }

                    override fun stopped(mediaPlayer: MediaPlayer) {
                        _state.update { it.copy(playing = false, positionMs = 0) }
                    }

                    override fun buffering(mediaPlayer: MediaPlayer, cache: Float) {
                        _state.update { it.copy(loading = cache < 100f) }
                    }

                    override fun finished(mediaPlayer: MediaPlayer) {
                        _state.update { it.copy(playing = false, positionMs = it.durationMs) }
                    }

                    override fun error(mediaPlayer: MediaPlayer) {
                        _state.update { it.copy(playing = false, loading = false, error = "Playback failed") }
                    }

                    override fun lengthChanged(mediaPlayer: MediaPlayer, length: Long) {
                        if (length > 0) {
                            _state.update { it.copy(durationMs = length) }
                        }
                    }
                }
            )
        }
    }

    override fun play(request: PlaybackRequest) {
        val player = player
        if (player == null) {
            _state.update { it.copy(error = "libVLC is not available") }
            return
        }

        ticker?.cancel()
        _state.value = PlaybackState(title = request.title, loading = true)

        // All calls into libVLC are queued on the player, because calling back into libVLC from
        // its own event thread is not allowed.
        player.submit {
            try {
                player.controls().stop()
                player.media().reset()

                if (!player.media().prepare(request.videoUrl)) {
                    _state.update {
                        it.copy(loading = false, error = "The video stream could not be opened")
                    }
                    return@submit
                }

                val audioUrl = request.audioUrl
                if (audioUrl != null &&
                    !player.media().addSlave(MediaSlaveType.AUDIO, audioUrl, true)
                ) {
                    Logger.w(messageString = "The audio stream could not be attached")
                }

                player.controls().play()
            } catch (exception: Throwable) {
                Logger.e(messageString = "The playback could not be started", throwable = exception)
                _state.update {
                    it.copy(
                        loading = false,
                        error = exception.message ?: "The playback could not be started"
                    )
                }
            }
        }

        startTicker()
    }

    override fun resume() {
        val player = player ?: return
        player.submit { player.controls().setPause(false) }
    }

    override fun pause() {
        val player = player ?: return
        player.submit { player.controls().setPause(true) }
    }

    override fun stop() {
        val player = player ?: return
        ticker?.cancel()
        player.submit {
            player.controls().stop()
            player.media().reset()
        }
        _state.update { it.copy(playing = false, positionMs = 0) }
    }

    override fun seekTo(positionMs: Long) {
        val player = player ?: return
        player.submit { player.controls().setTime(positionMs) }
        _state.update { it.copy(positionMs = positionMs) }
    }

    override fun setVolume(volume: Int) {
        val player = player ?: return
        val clamped = volume.coerceIn(0, 100)
        player.submit { player.audio().setVolume(clamped) }
        _state.update { it.copy(volume = clamped) }
    }

    override fun setMuted(muted: Boolean) {
        val player = player ?: return
        player.submit { player.audio().setMute(muted) }
        _state.update { it.copy(muted = muted) }
    }

    override fun setSpeed(speed: Float) {
        val player = player ?: return
        val clamped = speed.coerceIn(0.25f, 4.0f)
        player.submit { player.controls().setRate(clamped) }
        _state.update { it.copy(speed = clamped) }
    }

    override fun release() {
        ticker?.cancel()
        scope.coroutineContext[Job]?.cancel()
        val component = component ?: return
        component.release()
    }

    /**
     * Creates the embedded player, or returns `null` when libVLC is not available.
     */
    private fun createComponent(): EmbeddedMediaPlayerComponent? = try {
        VlcNativeLocator.locate()
        EmbeddedMediaPlayerComponent(*LIBRARY_ARGUMENTS)
    } catch (exception: Throwable) {
        Logger.e(messageString = "libVLC could not be initialised", throwable = exception)
        null
    }

    /**
     * Keeps the position and the duration of the player up to date.
     */
    private fun startTicker() {
        val player = player ?: return
        val status = player.status()

        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val time = status.time()
                val length = status.length()

                _state.update {
                    it.copy(
                        positionMs = if (time > 0) time else it.positionMs,
                        durationMs = if (length > 0) length else it.durationMs
                    )
                }

                delay(TICK_INTERVAL_MS)
            }
        }
    }

    companion object {
        /**
         * The arguments libVLC is started with.
         */
        private val LIBRARY_ARGUMENTS = arrayOf(
            "--no-video-title-show",
            "--no-media-library",
            "--no-stats"
        )

        /**
         * The interval the playback position is refreshed with.
         */
        private const val TICK_INTERVAL_MS = 250L
    }
}

/**
 * Creates the libVLC based playback controller of the desktop application.
 */
actual fun createPlaybackController(): PlaybackController = VlcPlaybackController()
