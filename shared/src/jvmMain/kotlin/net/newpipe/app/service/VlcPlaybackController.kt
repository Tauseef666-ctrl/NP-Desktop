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

    // The libVLC native libraries are heavy to initialise (several seconds), so the factory and
    // the media player are created on demand with the first playback instead of blocking the
    // application start. Reading the delegates through the getters below initialises them; the
    // playback controls intentionally do so, while `release()` only tears down what exists.
    private val factoryDelegate = lazy { createFactory() }

    private val componentDelegate = lazy {
        factoryDelegate.value?.let { createComponent(it) }?.also { created ->
            attachEventListeners(created.mediaPlayer())
        }
    }

    private val factory: MediaPlayerFactory? get() = factoryDelegate.value

    private val component: CallbackMediaPlayerComponent? get() = componentDelegate.value

    private val player: MediaPlayer? get() = component?.mediaPlayer()

    private val _frame = MutableStateFlow<VideoFrame?>(null)
    override val frame = _frame.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(PlaybackState())
    override val state = _state.asStateFlow()

    private var ticker: Job? = null

    /**
     * The size of the picture that libVLC was last negotiated to render. These values are only
     * touched from the libVLC format callbacks, and only read from its render callback, which run
     * sequentially on the video output thread.
     */
    @Volatile
    private var negotiatedFormatWidth: Int = initialFormat.first

    @Volatile
    private var negotiatedFormatHeight: Int = initialFormat.second

    /**
     * Subscribes the player state to the libVLC events; runs once when the player is created.
     */
    private fun attachEventListeners(player: MediaPlayer) {
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
            speed = previous.speed,
            serviceId = request.serviceId,
            webUrl = request.webUrl,
            qualityLabel = request.qualityLabel
        )

        // All calls into libVLC are queued on the player, because calling back into libVLC from
        // its own event thread is not allowed.
        player.submit {
            try {
                player.controls().stop()
                player.media().reset()

                // Resume support: the media option makes libVLC skip to the position the user
                // left the stream at, before the first frame is shown.
                val options = mutableListOf<String>()
                if (request.startPositionMs > 5_000) {
                    options += ":start-time=${request.startPositionMs / 1_000}"
                }

                if (!player.media().prepare(request.videoUrl, *options.toTypedArray())) {
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
        if (componentDelegate.isInitialized()) {
            componentDelegate.value?.release()
        }
        if (factoryDelegate.isInitialized()) {
            factoryDelegate.value?.release()
        }
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
        val buffer = IntArray(FRAME_PIXELS)

        val renderCallback = object : RenderCallbackAdapter(buffer) {
            override fun onDisplay(mediaPlayer: MediaPlayer, rgbBuffer: IntArray) {
                // The render callback runs on a libVLC thread. A copied buffer is handed to the
                // user interface, so that libVLC can reuse its own buffer immediately.
                val width = negotiatedFormatWidth
                val height = negotiatedFormatHeight
                val validPixels = width * height
                if (width > 0 && height > 0 && rgbBuffer.size >= validPixels) {
                    _frame.value = VideoFrame(
                        width,
                        height,
                        rgbBuffer.copyOf(validPixels)
                    )
                }
            }
        }

        val bufferFormatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                val format = frameFormatFor(sourceWidth, sourceHeight)
                negotiatedFormatWidth = format.first
                negotiatedFormatHeight = format.second
                return RV32BufferFormat(format.first, format.second)
            }

            override fun newFormatSize(
                width: Int,
                height: Int,
                visibleWidth: Int,
                visibleHeight: Int
            ) {
                // libVLC renegotiated the video output size; keep the copy size in sync so that
                // the frames handed to the user interface keep their real aspect ratio.
                val format = frameFormatFor(width, height)
                negotiatedFormatWidth = format.first
                negotiatedFormatHeight = format.second
            }

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
         * The pixel budget the decoded video frames are scaled into.
         *
         * The buffer keeps a fixed size of [FRAME_PIXELS] entries; every pixel format negotiated
         * with libVLC fits into it. The frames are copied once per frame for the user interface,
         * so the budgets balance image quality against the amount of memory moved per second.
         */
        private const val FRAME_PIXELS = 1280 * 720

        /**
         * Rounds a dimension to an even number, because chroma conversion works with pairs.
         */
        private fun even(value: Int): Int = if (value and 1 == 1) value - 1 else value

        /**
         * Chooses the dimensions of the frame buffer for a video of the given size.
         *
         * The aspect ratio of the source is preserved, so portrait, square and landscape videos
         * are scaled without distortion. The picture is never scaled up beyond its original
         * resolution and never exceeds the pixel budget of the frame buffer.
         */
        fun frameFormatFor(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
            val width = sourceWidth.coerceAtLeast(1)
            val height = sourceHeight.coerceAtLeast(1)
            val budget = FRAME_PIXELS.toDouble()

            var scale = minOf(
                budget / (width.toLong() * height.toLong()),
                1.0
            )
            var targetWidth = even((width * scale).toInt().coerceAtLeast(2))
            var targetHeight = even((height * scale).toInt().coerceAtLeast(2))

            // Rounding to even dimensions can push the result slightly over the budget; scale
            // down once more if that happens.
            if (targetWidth.toLong() * targetHeight.toLong() > FRAME_PIXELS) {
                val corrective = budget / (targetWidth.toLong() * targetHeight.toLong())
                targetWidth = even((targetWidth * corrective).toInt().coerceAtLeast(2))
                targetHeight = even((targetHeight * corrective).toInt().coerceAtLeast(2))
            }

            return targetWidth to targetHeight
        }

        /**
         * Initialises the frame dimensions so the copy loop never reads unvalidated values.
         */
        private val initialFormat = frameFormatFor(1280, 720)
    }
}

/**
 * Creates the libVLC based playback controller of the desktop application.
 */
actual fun createPlaybackController(): PlaybackController = VlcPlaybackController()
