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
     * The request the current playback was started with; the error handler reuses it for the
     * automatic retry, because the libVLC error event carries no context of its own.
     */
    @Volatile
    private var activeRequest: PlaybackRequest? = null

    /**
     * Guards the automatic retry so a failing playback is restarted at most once; set again in
     * every [play] call.
     */
    @Volatile
    private var retriedOnError = false

    /**
     * The web url and timestamp of the last [play] call; identical calls within the window are
     * deduplicated, because recomposition and detail reloads can start the same video twice in
     * quick succession and the second start races the first one inside libVLC.
     */
    private var lastPlayUrl: String? = null
    private var lastPlayAt = 0L

    /**
     * SABR stream chaining: a SABR media generation served through the local relay carries
     * roughly a minute of media before YouTube's attestation wall ends it. When the player
     * reaches that end while the video is actually unfinished, the playback is transparently
     * reconnected at the last position — a fresh connection starts a fresh stream generation.
     * This counts the reconnects of the current playback.
     */
    @Volatile
    private var sabrChains = 0

    /**
     * Guards against double reconnects, because a stream generation end arrives as a
     * `finished` event followed by a `stopped` event, and the reconnect's own stop triggers
     * `stopped` as well.
     */
    @Volatile
    private var sabrChainScheduled = false

    /**
     * Set by the user facing [stop] and [clear] so their libVLC `stopped` event is not
     * mistaken for a SABR stream generation end.
     */
    @Volatile
    private var stoppedByUser = false

    /**
     * The position the last reconnect resumed from; a reconnect that makes no progress means
     * the media really is over, and chaining must not loop on it.
     */
    @Volatile
    private var lastChainPositionMs = -1L

    /**
     * The wall clock baseline for the playback position estimate: libVLC does not report a
     * time for every media (fragmented streams in particular), so the elapsed time since the
     * last `playing` event fills the gap.
     */
    @Volatile
    private var playingSinceAt = 0L

    @Volatile
    private var playingBaselineMs = 0L

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
                    // A fresh playback stint starts: the wall clock position estimate is
                    // rebased so that it stays meaningful across reconnects.
                    playingBaselineMs = _state.value.positionMs
                    playingSinceAt = System.currentTimeMillis()
                    sabrChainScheduled = false
                    _state.update { it.copy(playing = true, loading = false) }
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    _state.update { it.copy(playing = false) }
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    if (maybeChainSabr()) return
                    _state.update { it.copy(playing = false, positionMs = 0) }
                }

                override fun buffering(mediaPlayer: MediaPlayer, cache: Float) {
                    _state.update { it.copy(loading = cache < 100f) }
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    if (maybeChainSabr()) return
                    if (!_state.value.live) {
                        _state.update { it.copy(playing = false, positionMs = it.durationMs) }
                    }
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    Logger.e(
                        messageString = "libVLC reported an error event" +
                            " (video='${activeRequest?.title}' webUrl='${activeRequest?.webUrl}')"
                    )
                    // A SABR stream that dies mid-playback is reconnected at its position, not
                    // restarted from the beginning.
                    if (maybeChainSabr()) return
                    if (!retriedOnError) {
                        retriedOnError = true
                        val request = activeRequest
                        if (request != null) {
                            // The libVLC event thread must not be blocked here; the retry is
                            // scheduled on the controller scope and all libVLC work is queued
                            // through submit() again. A short delay lets the broken ES output
                            // of the failed attempt shut down before the retry prepares fresh.
                            scope.launch {
                                delay(RETRY_DELAY_MS)
                                retryPlayback(request)
                            }
                            return
                        }
                    }
                    _state.update { it.copy(playing = false, loading = false, error = "Playback failed") }
                    // A dead playback keeps its stale audio elementary stream otherwise, which
                    // can keep playing sound in the background; stop and reset kill it.
                    player.submit {
                        player.controls().stop()
                        player.media().reset()
                    }
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, length: Long) {
                    // Live streams report a bogus, constantly changing length (the buffered
                    // segment size); the duration is cleared so that the user interface
                    // hides the scrubber instead of showing a wrong position.
                    if (length > 0 && !_state.value.live) {
                        _state.update { it.copy(durationMs = length) }
                    }
                }
            }
        )
    }

    /**
     * Initialises the libVLC factory and media player in the background, so that the first
     * playback starts without the multi-second native setup delay.
     */
    override fun warmUp() {
        scope.launch(Dispatchers.Default) {
            runCatching {
                // Reading the getters initialises the lazy delegates exactly like a play()
                // call would; the heavy native work simply happens earlier and off the UI.
                component
            }.onFailure {
                Logger.w(messageString = "Warming up libVLC failed", throwable = it)
            }.onSuccess {
                Logger.i(messageString = "libVLC warmed up and ready for playback")
            }
        }
    }

    override fun play(request: PlaybackRequest) {
        val player = player
        if (player == null) {
            _state.update { it.copy(error = "libVLC is not available") }
            return
        }

        // Rapid duplicate starts of the same video race each other inside libVLC (two stop/
        // prepare/play sequences interleaved within a second); the second start is ignored.
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (request.webUrl == lastPlayUrl && now - lastPlayAt < PLAY_DEDUPE_MS) {
                Logger.i(messageString = "Duplicate start of '${request.title}' ignored")
                return
            }
            lastPlayUrl = request.webUrl
            lastPlayAt = now
        }

        ticker?.cancel()
        activeRequest = request
        retriedOnError = false
        // A new media also starts a new SABR chaining budget.
        sabrChains = 0
        sabrChainScheduled = false
        stoppedByUser = false
        lastChainPositionMs = -1L
        playingSinceAt = 0L
        playingBaselineMs = 0L
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
            qualityLabel = request.qualityLabel,
            live = request.live
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

    /**
     * Restarts the current playback once after libVLC reported an error: the player is stopped
     * and reset first so the failed attempt's elementary streams die before a fresh prepare
     * reattaches the audio slave and plays again.
     */
    private fun retryPlayback(request: PlaybackRequest) {
        val player = player ?: return
        Logger.i(messageString = "Retrying playback of '${request.title}' after an error")
        _frame.value = null
        _state.update { it.copy(playing = false, loading = true, error = null) }
        player.submit {
            try {
                player.controls().stop()
                player.media().reset()
                val options = mutableListOf<String>()
                if (request.startPositionMs > 5_000) {
                    options += ":start-time=${request.startPositionMs / 1_000}"
                }
                if (!player.media().prepare(request.videoUrl, *options.toTypedArray())) {
                    _state.update {
                        it.copy(playing = false, loading = false, error = "Playback failed")
                    }
                    return@submit
                }
                val audioUrl = request.audioUrl
                if (audioUrl != null) {
                    player.media().addSlave(MediaSlaveType.AUDIO, audioUrl, true)
                }
                player.audio().setVolume(_state.value.volume)
                player.audio().setMute(_state.value.muted)
                player.controls().setRate(_state.value.speed)
                player.controls().play()
            } catch (exception: Throwable) {
                Logger.e(
                    messageString = "The retry of '${request.title}' failed",
                    throwable = exception
                )
                _state.update {
                    it.copy(playing = false, loading = false, error = "Playback failed")
                }
            }
        }
    }

    /**
     * Decides whether a playback end was the attestation wall of a SABR stream generation and
     * schedules a transparent reconnect at the last position in that case. Called from the
     * libVLC event thread with the `finished`, `stopped` and `error` events.
     *
     * A genuine end of the media is detected when the position is at the known duration, or
     * when a previous reconnect already resumed from (roughly) the same position and could
     * therefore not make any progress.
     */
    private fun maybeChainSabr(): Boolean {
        if (stoppedByUser) {
            stoppedByUser = false
            return false
        }
        val request = activeRequest ?: return false
        if (!isSabrMedia(request.videoUrl)) return false
        if (sabrChainScheduled) return true
        if (request.live) return false

        val state = _state.value
        val position = estimatedPositionMs(state)
        val duration = state.durationMs
        if (position <= 0) return false
        if (duration > 0 && position >= duration - SABR_END_EPSILON_MS) return false
        if (position - lastChainPositionMs < SABR_PROGRESS_EPSILON_MS) {
            // The previous reconnect resumed at this position already: the media really is
            // over, chaining would loop on its last seconds forever.
            return false
        }
        if (sabrChains >= SABR_CHAIN_LIMIT) {
            Logger.w(messageString = "The SABR reconnect limit is reached; playback ends")
            return false
        }

        sabrChainScheduled = true
        sabrChains++
        lastChainPositionMs = position
        Logger.i(
            messageString = "SABR stream generation ended at ${position}ms;" +
                " reconnecting (chain ${sabrChains}/${SABR_CHAIN_LIMIT})"
        )
        scope.launch {
            delay(SABR_CHAIN_DELAY_MS)
            chainSabrPlayback(request, position)
        }
        return true
    }

    /**
     * Reconnects a SABR playback at [positionMs]: the player is stopped and reset so the
     * exhausted attempt's elementary streams die, then the same media is prepared fresh —
     * a fresh connection starts a fresh stream generation — and played from the position.
     */
    private fun chainSabrPlayback(request: PlaybackRequest, positionMs: Long) {
        val player = player ?: return
        Logger.i(messageString = "Reconnecting '${request.title}' at ${positionMs}ms")
        _state.update { it.copy(loading = true, error = null) }
        player.submit {
            try {
                player.controls().stop()
                player.media().reset()
                if (!player.media().prepare(
                        request.videoUrl,
                        ":start-time=${positionMs / 1_000}"
                    )
                ) {
                    sabrChainScheduled = false
                    _state.update {
                        it.copy(playing = false, loading = false, error = "Playback failed")
                    }
                    return@submit
                }
                val audioUrl = request.audioUrl
                if (audioUrl != null) {
                    player.media().addSlave(MediaSlaveType.AUDIO, audioUrl, true)
                }
                player.audio().setVolume(_state.value.volume)
                player.audio().setMute(_state.value.muted)
                player.controls().setRate(_state.value.speed)
                player.controls().play()
            } catch (exception: Throwable) {
                Logger.e(
                    messageString = "The SABR reconnect of '${request.title}' failed",
                    throwable = exception
                )
                sabrChainScheduled = false
                _state.update {
                    it.copy(playing = false, loading = false, error = "Playback failed")
                }
            }
        }
    }

    /**
     * The best known playback position: libVLC's own time when it reports one, otherwise the
     * wall clock estimate since the last `playing` event.
     */
    private fun estimatedPositionMs(state: PlaybackState): Long {
        val fromVlc = state.positionMs
        val speed = state.speed.coerceAtLeast(0.25f)
        val fromWallClock = if (state.playing && playingSinceAt > 0) {
            playingBaselineMs + (System.currentTimeMillis() - playingSinceAt) * speed.toLong()
        } else {
            playingBaselineMs
        }
        return maxOf(fromVlc, fromWallClock)
    }

    /**
     * Whether [url] is a stream served by the local SABR relay, which is the media whose
     * playback gets chained across stream generations.
     */
    private fun isSabrMedia(url: String?): Boolean =
        url != null && url.startsWith("http://127.0.0.1:") && url.contains("/sabr/")

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
        stoppedByUser = true
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
        stoppedByUser = true
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
                        durationMs = if (length > 0 && !it.live) length else if (it.live) 0 else it.durationMs
                    )
                }

                delay(TICK_INTERVAL_MS)
            }
        }
    }

    companion object {
        /**
         * The arguments libVLC is started with.
         *
         * `network-caching` is cut from the 5s default to 1.2s so the first frame appears much
         * earlier. Hardware accelerated decoding stays off on purpose: the frames are delivered
         * on CPU buffers for Compose rendering, and forcing GPU decode through that pipeline
         * can end in a black picture depending on the driver.
         */
        private val LIBRARY_ARGUMENTS = arrayOf(
            "--network-caching=1200",
            "--no-video-title-show",
            "--no-media-library",
            "--no-stats"
        )

        /**
         * The interval the playback position is refreshed with.
         */
        private const val TICK_INTERVAL_MS = 250L

        /**
         * Identical play() calls within this window are ignored, because two starts of the same
         * video within a second race each other inside libVLC.
         */
        private const val PLAY_DEDUPE_MS = 1_000L

        /**
         * The delay before the automatic retry after a libVLC error event.
         */
        private const val RETRY_DELAY_MS = 800L

        /**
         * How often a SABR playback may be reconnected across stream generations; each
         * reconnect is a fresh connection with a fresh pot-less media budget.
         */
        private const val SABR_CHAIN_LIMIT = 60

        /**
         * The delay before a SABR reconnect re-prepares the media, letting the broken
         * elementary streams of the ended generation shut down first.
         */
        private const val SABR_CHAIN_DELAY_MS = 400L

        /**
         * A position this close to the known duration is the real end of the media, not a
         * stream generation wall.
         */
        private const val SABR_END_EPSILON_MS = 1_500L

        /**
         * A reconnect that resumed this close to the previous reconnect's position made no
         * progress, so the media really is over.
         */
        private const val SABR_PROGRESS_EPSILON_MS = 1_000L

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
