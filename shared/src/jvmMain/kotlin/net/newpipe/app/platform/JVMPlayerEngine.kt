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
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

/**
 * Desktop [PlayerEngine] backed by VLCJ/libVLC.
 *
 * If libVLC cannot be discovered the engine reports [isAvailable] `false` and surfaces an
 * [PlaybackError.Unsupported] — it never pretends to play. Install VLC (or set `VLC_HOME` /
 * `jna.library.path`) to enable playback.
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
                    found = NativeDiscovery().discover()
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
    private var videoSurfaceComponent: java.awt.Component? = null

    /** Position to seek to once playback starts (the resume position; null = play from start). */
    private var pendingSeekMs: Long? = null

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
            val newFactory = MediaPlayerFactory()
            val newPlayer = newFactory.mediaPlayers().newEmbeddedMediaPlayer()
            newPlayer.events().addMediaPlayerEventListener(listener)
            factory = newFactory
            player = newPlayer
            videoSurfaceComponent?.let { bindSurface(newPlayer, newFactory, it) }
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
     * Bind an AWT component as the video surface. Safe to call before the player exists; the
     * surface is attached when the player is created. Used by the desktop video-surface host.
     */
    fun attachVideoSurface(component: java.awt.Component) {
        videoSurfaceComponent = component
        val currentPlayer = ensurePlayer() ?: return
        val currentFactory = factory ?: return
        bindSurface(currentPlayer, currentFactory, component)
    }

    fun detachVideoSurface() {
        videoSurfaceComponent = null
    }

    private fun bindSurface(
        mediaPlayer: EmbeddedMediaPlayer,
        factory: MediaPlayerFactory,
        component: java.awt.Component
    ) {
        try {
            mediaPlayer.videoSurface().set(factory.videoSurfaces().newVideoSurface(component))
        } catch (_: Throwable) {
            // A missing surface must not crash playback; audio still plays.
        }
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
            mediaPlayer.media().play(item.url)
        } catch (t: Throwable) {
            _state.value = _state.value.copy(
                status = PlaybackStatus.ERROR,
                error = PlaybackError.Source(t.message ?: "Failed to load media")
            )
        }
    }

    override fun play() {
        player?.controls()?.play()
    }

    override fun pause() {
        player?.controls()?.pause()
    }

    override fun stop() {
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

    override fun release() {
        player?.release()
        factory?.release()
        player = null
        factory = null
    }

    companion object {
        const val UNAVAILABLE_MESSAGE =
            "libVLC was not found. Install VLC or point VLC_HOME at it to enable desktop playback."
        private const val MAX_VLC_VOLUME = 100
    }
}
