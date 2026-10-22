/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlaybackError
import net.newpipe.app.player.PlaybackState
import net.newpipe.app.player.PlaybackStatus
import net.newpipe.app.player.PlayerEngine
import net.newpipe.app.player.Quality
import org.koin.core.annotation.Singleton
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.State
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

/**
 * Desktop [PlayerEngine] backed by VLCJ/libVLC.
 *
 * If libVLC cannot be discovered the engine reports [isAvailable] `false` and surfaces an
 * [PlaybackError.Unsupported] — it never pretends to play. Windows desktop builds bundle VLC
 * (see [BundledVlc], extracted to the user's app data dir on first run), so a system install is
 * optional there; elsewhere install VLC (or set `VLC_HOME` / `jna.library.path`).
 *
 * Discovery itself runs on a background daemon thread started with the engine: the engine is
 * constructed during the first composition (before the first frame), and a synchronous library
 * search there would delay first paint. [isAvailable] waits for that result, so every consumer
 * still gets the true answer, just never before it is known.
 *
 * Video-surface embedding is wired separately; this engine owns control/state/URL loading.
 */
@Singleton(binds = [PlayerEngine::class])
class JVMPlayerEngine : PlayerEngine {

    private val discovery = CompletableFuture<Boolean>().also { future ->
        Thread(
            {
                var found = false
                try {
                    val bundled = BundledVlc.ensureExtracted()
                    if (bundled != null) {
                        System.err.println("[NewPipe+] trying bundled libVLC at $bundled")
                        found = NativeDiscovery(BundledVlcDiscoveryStrategy(bundled)).discover()
                        if (found) {
                            System.err.println("[NewPipe+] bundled libVLC discovered")
                        } else {
                            System.err.println("[NewPipe+] bundled libVLC failed to load; discarding it")
                            BundledVlc.discard(bundled)
                        }
                    }
                    if (!found) {
                        found = NativeDiscovery().discover()
                        System.err.println(
                            if (found) "[NewPipe+] system libVLC discovered"
                            else "[NewPipe+] no libVLC found (bundled or system)"
                        )
                    }
                } finally {
                    // Completes even if discovery dies with an Error, so no reader can hang.
                    future.complete(found)
                }
            },
            "vlcj-native-discovery"
        ).apply {
            isDaemon = true
            start()
        }
    }

    override val isAvailable: Boolean
        get() = try {
            discovery.get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

    private val _state = MutableStateFlow(PlaybackState(status = PlaybackStatus.IDLE))

    init {
        // Honest unknown-then-known: while discovery runs the engine looks idle; a failed
        // discovery flips to the Unsupported error the UI shows instead of pretending to play.
        // If discovery already finished, the callback runs right here in the constructor.
        discovery.whenComplete { found, _ ->
            if (found != true) {
                _state.value = PlaybackState(
                    status = PlaybackStatus.ERROR,
                    error = PlaybackError.Unsupported(UNAVAILABLE_MESSAGE)
                )
            }
        }
    }

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var factory: MediaPlayerFactory? = null
    private var player: EmbeddedMediaPlayer? = null

    /**
     * How many on-screen video surfaces currently consume frames (main player, mini player).
     * libVLC renders through a callback video surface into memory; Compose then draws the latest
     * frame itself, so no heavyweight AWT component can ever cover the player controls.
     */
    private var frameSinkRefs: Int = 0

    /** Latest decoded video frame, in `TYPE_INT_RGB`; null until the first frame arrives. */
    @Volatile
    private var latestFrame: java.awt.image.BufferedImage? = null

    /** Increments for every decoded frame, so surfaces can skip work while the picture is still. */
    @Volatile
    private var frameSequence: Long = 0

    /**
     * Strong references to the callback objects handed to libVLC. JNA only keeps weak references
     * to native callbacks, so without this the render callback is collected and libVLC logs
     * "callback object has been garbage collected" and fails to create the video output.
     */
    private val videoSurfaceHolders = mutableListOf<Any>()

    /** Scratch buffer reused across frames so decoding does not allocate per frame. */
    private var framePixels: IntArray = IntArray(0)

    /** Position to seek to once playback starts (the resume position; null = play from start). */
    private var pendingSeekMs: Long? = null

    /** True while the user wants the media playing. */
    @Volatile
    private var playRequested: Boolean = false

    /** The video track id captured when the video was switched off for background playback. */
    @Volatile
    private var disabledVideoTrack: Int = -1

    private val listener = object : MediaPlayerEventAdapter() {
        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            _state.value = _state.value.copy(positionMs = newTime)
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            _state.value = _state.value.copy(durationMs = newLength)
        }

        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            val duration = _state.value.durationMs
            _state.value = _state.value.copy(bufferedMs = (newCache * duration).toLong())
        }

        override fun playing(mediaPlayer: MediaPlayer) {
            pendingSeekMs?.let { seek ->
                if (seek > 0L) {
                    try {
                        mediaPlayer.controls().setTime(seek)
                    } catch (_: Throwable) {
                        // Seeking at this moment is not critical; playback continues from the start.
                    }
                }
                pendingSeekMs = null
            }
            _state.value = _state.value.copy(status = PlaybackStatus.PLAYING, isPlaying = true)
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            _state.value = _state.value.copy(status = PlaybackStatus.PAUSED, isPlaying = false)
        }

        override fun stopped(mediaPlayer: MediaPlayer) {
            pendingSeekMs = null
            _state.value = _state.value.copy(status = PlaybackStatus.READY, isPlaying = false)
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            pendingSeekMs = null
            _state.value = _state.value.copy(status = PlaybackStatus.ENDED, isPlaying = false)
        }

        override fun error(mediaPlayer: MediaPlayer) {
            pendingSeekMs = null
            System.err.println("[NewPipe+] libVLC playback error (state=${mediaPlayer.status().state()})")
            _state.value = _state.value.copy(
                status = PlaybackStatus.ERROR,
                isPlaying = false,
                error = PlaybackError.Engine("libVLC reported a playback error")
            )
        }
    }

    private fun ensurePlayer(): EmbeddedMediaPlayer? {
        if (!isAvailable) return null
        player?.let { return it }
        return try {
            // Optional runtime overrides for diagnostics: NEWPIPE_VLC_ARGS lets a build be tested
            // with different libVLC switches without recompiling (e.g. "--vout=directdraw").
            val factoryArgs = System.getenv("NEWPIPE_VLC_ARGS")
                .orEmpty()
                .split(' ')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            System.err.println("[NewPipe+] vlc factory args=$factoryArgs")
            val newFactory = MediaPlayerFactory(*factoryArgs.toTypedArray())
            val newPlayer = newFactory.mediaPlayers().newEmbeddedMediaPlayer()
            newPlayer.events().addMediaPlayerEventListener(listener)
            factory = newFactory
            player = newPlayer
            if (frameSinkRefs > 0) bindFrameSurface(newPlayer)
            newPlayer
        } catch (t: Throwable) {
            _state.value = _state.value.copy(
                status = PlaybackStatus.ERROR,
                error = PlaybackError.Engine(t.message ?: "Failed to start libVLC")
            )
            null
        }
    }

    /**
     * Start consuming decoded video frames for an on-screen surface. Frames are rendered by libVLC
     * into memory and drawn by Compose, so the player UI is always on top and interactive.
     * Multiple surfaces may consume the same frames (main player plus mini player).
     */
    fun acquireFrameSink() {
        frameSinkRefs++
        ensurePlayer()
        System.err.println("[NewPipe+] frame sink acquired refs=$frameSinkRefs")
        player?.let { bindFrameSurface(it) }
    }

    /** Stop consuming frames; when the last surface goes away libVLC stops decoding video. */
    fun releaseFrameSurface() {
        if (frameSinkRefs > 0) frameSinkRefs--
        System.err.println("[NewPipe+] frame sink released refs=$frameSinkRefs")
        if (frameSinkRefs > 0) return
        latestFrame = null
        try {
            player?.videoSurface()?.set(null)
        } catch (_: Throwable) {
            // Nothing bound; playback is unaffected.
        }
    }

    /** The most recent decoded frame, or null before the first one (or after release). */
    fun latestVideoFrame(): java.awt.image.BufferedImage? = latestFrame

    /** Number of frames decoded so far; changes when [latestVideoFrame] has new content. */
    fun videoFrameSequence(): Long = frameSequence

    /**
     * Bind a callback video surface: libVLC writes decoded frames into our buffer, we convert them
     * to a `BufferedImage` the Compose player draws. Unlike an AWT canvas this never covers the
     * Compose overlay, and it works regardless of when the player screen composes.
     */
    private fun bindFrameSurface(mediaPlayer: EmbeddedMediaPlayer) {
        val currentFactory = factory ?: return
        if (mediaPlayer.videoSurface() == null) return
        try {
            val formatCallback = object :
                uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter() {
                override fun getBufferFormat(
                    sourceWidth: Int,
                    sourceHeight: Int
                ): uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat {
                    // Decode straight into a capped-size buffer: 4K frames cost ~33 MB each and
                    // the player is never wider than the window anyway.
                    val scale = if (sourceWidth > MAX_FRAME_WIDTH) {
                        MAX_FRAME_WIDTH.toFloat() / sourceWidth
                    } else {
                        1f
                    }
                    val width = ((sourceWidth * scale).toInt() / 2) * 2
                    val height = ((sourceHeight * scale).toInt() / 2) * 2
                    return uk.co.caprica.vlcj.player.embedded.videosurface.callback.format
                        .RV32BufferFormat(width.coerceAtLeast(2), height.coerceAtLeast(2))
                }
            }
            val renderCallback = object :
                uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback {
                override fun lock(mediaPlayer: MediaPlayer) = Unit

                override fun unlock(mediaPlayer: MediaPlayer) = Unit

                override fun display(
                    mediaPlayer: MediaPlayer,
                    nativeBuffers: Array<java.nio.ByteBuffer>,
                    bufferFormat: uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat,
                    width: Int,
                    height: Int
                ) {
                    onFrame(nativeBuffers, bufferFormat)
                }
            }
            val surface = currentFactory.videoSurfaces()
                .newVideoSurface(formatCallback, renderCallback, true)
            mediaPlayer.videoSurface().set(surface)
            // set() only stores the surface; the native callbacks are installed by attach.
            mediaPlayer.videoSurface().attachVideoSurface()
            // Retain every callback ever handed to libVLC: a previous video output keeps its native
            // function pointers until it is torn down, and a collected callback kills that output.
            videoSurfaceHolders.add(formatCallback)
            videoSurfaceHolders.add(renderCallback)
            videoSurfaceHolders.add(surface)
            System.err.println("[NewPipe+] video surface: callback sink bound")
        } catch (t: Throwable) {
            System.err.println("[NewPipe+] video surface not bound: ${t.message}")
        }
    }

    /**
     * Convert one RV32 (BGRA) frame into [latestFrame]. Runs on a libVLC callback thread, so it
     * avoids allocation by reusing the pixel buffer and image while the video size is unchanged.
     */
    private fun onFrame(
        nativeBuffers: Array<java.nio.ByteBuffer>,
        bufferFormat: uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
    ) {
        if (frameSequence == 0L) {
            System.err.println(
                "[NewPipe+] first video frame: planes=${nativeBuffers.size} " +
                    "size=${bufferFormat.width}x${bufferFormat.height} " +
                    "chroma=${bufferFormat.chroma} pitches=${bufferFormat.pitches.joinToString()} " +
                    "engine=${hashCode()}"
            )
        }
        val source = nativeBuffers.firstOrNull() ?: return
        val width = bufferFormat.width
        val height = bufferFormat.height
        if (width <= 0 || height <= 0) return
        val count = width * height
        if (count > framePixels.size) framePixels = IntArray(count)
        try {
            source.order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(framePixels, 0, count)
        } catch (t: Throwable) {
            System.err.println("[NewPipe+] frame copy failed: ${t.message}")
            return
        }
        val image = java.awt.image.BufferedImage(
            width,
            height,
            java.awt.image.BufferedImage.TYPE_INT_RGB
        )
        // RV32 packs B,G,R,X into little-endian memory, which reads back as 0x00RRGGBB — exactly
        // TYPE_INT_RGB's layout, so the pixels can be copied as-is. A fresh image per frame keeps
        // the Compose bitmap from aliasing a raster that is rewritten by the next frame.
        image.setRGB(0, 0, width, height, framePixels, 0, width)
        latestFrame = image
        frameSequence++
    }

    override suspend fun prepare(item: MediaItem) {
        val mediaPlayer = ensurePlayer() ?: return
        pendingSeekMs = item.resumePositionMs.takeIf { it > 0L }
        _state.value = _state.value.copy(
            mediaId = item.id,
            status = PlaybackStatus.LOADING,
            positionMs = 0L,
            durationMs = item.durationMs,
            error = null
        )
        try {
            latestFrame = null
            bindFrameSurface(mediaPlayer)
            // YouTube 403s libVLC's default agent; a browser UA makes the direct stream URLs play.
            // Disable hardware decoding (DXVA2/D3D11) — libVLC 3.0.21 crashes with
            // STATUS_STACK_BUFFER_OVERRUN (0xC0000409) on Windows when it uses the GPU decoder,
            // which kills the whole JVM; software decoding is slower but does not crash.
            System.err.println(
                "[NewPipe+] prepare: host=${hostOf(item.url)} audioOnly=${item.audioOnly} " +
                    "slaveAudio=${item.audioUrl != null} frameSinks=$frameSinkRefs"
            )
            val options = buildList {
                add(HTTP_USER_AGENT_OPTION)
                add(SOFTWARE_DECODING_OPTION)
                // DASH video-only renditions carry no audio; libVLC plays the audio track as a slave
                // input so the picture and the sound come from the same media player instance.
                item.audioUrl?.takeIf { it.isNotBlank() }?.let { add(":input-slave=$it") }
                // HLS is a single input carrying picture and sound, but libVLC's adaptive module
                // starts on the lowest rendition and only ramps up. On video on demand the top
                // rendition is affordable, so pick it up front; live keeps the adaptive default.
                if (!item.isLive) add(ADAPTIVE_HIGHEST_OPTION)
                add(ADAPTIVE_MAX_HEIGHT_OPTION)
                System.getenv("NEWPIPE_VLC_VOUT")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(":vout=$it") }
            }
            mediaPlayer.media().play(item.url, *options.toTypedArray())
            playRequested = true
            probeVideoOutput(mediaPlayer)
        } catch (t: Throwable) {
            _state.value = _state.value.copy(
                status = PlaybackStatus.ERROR,
                error = PlaybackError.Source(t.message ?: "Failed to load media")
            )
        }
    }

    /**
     * Log whether libVLC actually produced a video output. A black picture with audible sound is
     * either a video-only stream without an audio supply or a failed video output; this probe tells
     * the two apart in the run log.
     */
    private fun probeVideoOutput(mediaPlayer: EmbeddedMediaPlayer) {
        Thread(
            {
                try {
                    Thread.sleep(5000)
                    val audio = mediaPlayer.audio()
                    System.err.println(
                        "[NewPipe+] video probe: status=${mediaPlayer.status().state()} " +
                            "tracks=${mediaPlayer.video().trackCount()} " +
                            "dimension=${mediaPlayer.video().videoDimension()} " +
                            "frames=$frameSequence audioTracks=${audio.trackCount()} " +
                            "volume=${audio.volume()} mute=${audio.isMute} " +
                            "audioTrack=${audio.track()}"
                    )
                } catch (t: Throwable) {
                    System.err.println("[NewPipe+] video probe failed: ${t.message}")
                }
            },
            "vlcj-video-probe"
        ).apply {
            isDaemon = true
            start()
        }
    }

    override fun play() {
        playRequested = true
        player?.controls()?.play()
    }

    override fun pause() {
        playRequested = false
        player?.controls()?.pause()
    }

    override fun stop() {
        playRequested = false
        player?.controls()?.stop()
    }

    override fun seekTo(positionMs: Long) {
        player?.controls()?.setTime(positionMs.coerceAtLeast(0L))
    }

    override fun setSpeed(speed: Float) {
        player?.controls()?.setRate(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        player?.audio()?.setVolume((clamped * MAX_VLC_VOLUME).toInt())
        _state.value = _state.value.copy(volume = clamped)
    }

    override fun setMuted(muted: Boolean) {
        player?.audio()?.setMute(muted)
        _state.value = _state.value.copy(isMuted = muted)
    }

    override fun setQuality(quality: Quality) {
        _state.value = _state.value.copy(quality = quality)
    }

    /**
     * Switch the video track off (background play) and back on. libVLC takes `-1` as "no video
     * track", so the audio keeps playing while nothing is decoded for the picture; the track id is
     * remembered so the video can be restored later.
     */
    override fun setVideoEnabled(enabled: Boolean) {
        val mediaPlayer = player ?: return
        val video = mediaPlayer.video() ?: return
        if (enabled) {
            val track = disabledVideoTrack
            disabledVideoTrack = -1
            if (track < 0) return
            try {
                video.setTrack(track)
                System.err.println("[NewPipe+] video track restored: $track")
            } catch (t: Throwable) {
                System.err.println("[NewPipe+] could not restore the video track: ${t.message}")
            }
            return
        }
        val track = video.track()
        if (track < 0) return
        try {
            video.setTrack(-1)
            disabledVideoTrack = track
            System.err.println("[NewPipe+] video track disabled for background playback: $track")
        } catch (t: Throwable) {
            System.err.println("[NewPipe+] could not disable the video track: ${t.message}")
        }
    }

    override fun release() {
        frameSinkRefs = 0
        latestFrame = null
        videoSurfaceHolders.clear()
        player?.release()
        factory?.release()
        player = null
        factory = null
    }

    companion object {
        const val UNAVAILABLE_MESSAGE =
            "libVLC was not found. Install VLC or point VLC_HOME at it to enable desktop playback."
        private const val MAX_VLC_VOLUME = 100

        /** Widest frame handed to the Compose surface; larger videos are decoded down to this. */
        private const val MAX_FRAME_WIDTH = 1280

        /** Browser UA for stream fetches — YouTube rejects libVLC's default "Lavf" agent. */
        private const val HTTP_USER_AGENT_OPTION =
            ":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        /**
         * Force libVLC to decode video on the CPU. Windows hardware decoders (DXVA2/D3D11)
         * crash libVLC 3.0.21 with a fail-fast (0xC0000409) on some streams, taking the whole
         * JVM with it; this is the reliable fix until we move to VLC 4.
         */
        private const val SOFTWARE_DECODING_OPTION = ":avcodec-hw=none"

        /**
         * Keep the HLS adaptive module on the highest rendition instead of starting at the lowest
         * one and ramping up by bandwidth estimate, which left short videos visibly blocky.
         */
        private const val ADAPTIVE_HIGHEST_OPTION = ":adaptive-logic=highest"

        /**
         * Cap the adaptive rendition height. The manifest offers up to 2160p, but decoding 4K in
         * software (hardware decoding crashes libVLC 3 on Windows) drops frames on ordinary CPUs.
         */
        private const val ADAPTIVE_MAX_HEIGHT_OPTION = ":adaptive-maxheight=1080"

        /** The host part of a URL, for log lines — never the full URL (it has auth tokens). */
        private fun hostOf(url: String): String = try {
            java.net.URI(url).host ?: "unknown"
        } catch (_: Exception) {
            "unparsable"
        }

        /** The main player screen owns the video whenever it is on screen. */
        const val SURFACE_PRIORITY_MAIN = 0

        /** The mini player owns the video only while the main player screen is not shown. */
        const val SURFACE_PRIORITY_MINI = 1
    }
}
