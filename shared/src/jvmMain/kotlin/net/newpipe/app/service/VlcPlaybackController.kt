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
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.MediaPlayerSpecs
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

/**
 * Plays media with libVLC, using the VLCJ bindings.
 *
 * NewPipe usually extracts a video stream without audio plus a separate audio only stream. Both
 * are handed to libVLC as a media with an additional audio slave, so that the audio is played
 * even though the video stream itself carries no audio.
 *
 * The frames are delivered through a render callback instead of a native AWT video surface. This
 * keeps the video inside the Compose layer, so that player controls and the surrounding layout are
 * never covered by a heavyweight component.
 */
class VlcPlaybackController : PlaybackController, VideoFrameProvider {

    private val factory: MediaPlayerFactory? = createFactory()

    private val component: CallbackMediaPlayerComponent? = factory?.let { createComponent(it) }

    private val player: MediaPlayer? = component?.mediaPlayer()

    private val _frame = MutableStateFlow<VideoFrame?>(null)
    override val frame = _frame.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(PlaybackState())
    override val state = _state.asStateFlow()

    private var ticker: Job? = null

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
        Logger.i(messageString = "Starting playback of '${request.title}'")

        // The user settings of the player survive a change of the media.
        val previous = _state.value
        _frame.value = null
        _state.value = PlaybackState(
            title = request.title,
            loading = true,
            volume = previous.volume,
            muted = previous.muted,
            speed = previous.speed
        )

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

                player.audio().setVolume(previous.volume)
                player.audio().setMute(previous.muted)
                player.controls().setRate(previous.speed)
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
        _frame.value = null
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

    override fun clear() {
        val player = player ?: return
        ticker?.cancel()
        _frame.value = null
        val previous = _state.value
        _state.value = PlaybackState(
            volume = previous.volume,
            muted = previous.muted,
            speed = previous.speed
        )
        player.submit {
            player.controls().stop()
            player.media().reset()
        }
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
        _frame.value = null
        component?.release()
        factory?.release()
    }

    /**
     * Creates the media player factory, or returns `null` when libVLC is not available.
     */
    private fun createFactory(): MediaPlayerFactory? = try {
        VlcNativeLocator.locate()
        MediaPlayerFactory(*LIBRARY_ARGUMENTS)
    } catch (exception: Throwable) {
        Logger.e(messageString = "libVLC could not be initialised", throwable = exception)
        null
    }

    /**
     * Creates the player that delivers its frames through a render callback.
     */
    private fun createComponent(factory: MediaPlayerFactory): CallbackMediaPlayerComponent {
        val buffer = IntArray(FRAME_WIDTH * FRAME_HEIGHT)

        val renderCallback = object : RenderCallbackAdapter(buffer) {
            override fun onDisplay(mediaPlayer: MediaPlayer, rgbBuffer: IntArray) {
                // The render callback runs on a libVLC thread. A copied buffer is handed to the
                // user interface, so that libVLC can reuse its own buffer immediately.
                _frame.value = VideoFrame(FRAME_WIDTH, FRAME_HEIGHT, rgbBuffer.copyOf())
            }
        }

        val bufferFormatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat =
                RV32BufferFormat(FRAME_WIDTH, FRAME_HEIGHT)

            override fun newFormatSize(
                width: Int,
                height: Int,
                visibleWidth: Int,
                visibleHeight: Int
            ) = Unit

            override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
        }

        return MediaPlayerSpecs.callbackMediaPlayerSpec()
            .withFactory(factory)
            .withUnsupportedFullScreenStrategy()
            .withRenderCallback(renderCallback)
            .withBufferFormatCallback(bufferFormatCallback)
            .callbackMediaPlayer()
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

        /**
         * The size the video frames are decoded to.
         *
         * The frames are copied once per frame for the user interface, so the size is a balance
         * between image quality and the amount of memory that is moved per second.
         */
        private const val FRAME_WIDTH = 1280
        private const val FRAME_HEIGHT = 720
    }
}

/**
 * Creates the libVLC based playback controller of the desktop application.
 */
actual fun createPlaybackController(): PlaybackController = VlcPlaybackController()
