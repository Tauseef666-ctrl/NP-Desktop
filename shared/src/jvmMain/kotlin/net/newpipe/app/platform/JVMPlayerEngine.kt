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

    /** Registered video-surface hosts, oldest first; the owner is picked from these. */
    private val videoSurfaceHosts = mutableListOf<SurfaceHost>()
    private val surfaceListeners =
        mutableMapOf<java.awt.Component, java.awt.event.HierarchyListener>()

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
            bindRegisteredSurface()
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
     * Register an AWT component as a video-surface host. Safe to call before the player exists;
     * the surface is attached when the player is created. Used by the desktop video-surface hosts
     * (main player screen and mini-player window).
     *
     * VLCJ's `videoSurface().set(...)` only *stores* the surface — `libvlc_media_player_set_hwnd`
     * is applied from `onBeforePlay()`, which throws `IllegalStateException` while the component
     * is not displayable. Hosts usually compose after playback already started, so the surface is
     * attached here as soon as the component is displayable, and again from a hierarchy listener
     * when it later joins a displayable window (the only reliable moment for an AWT `Canvas`).
     *
     * Ownership: several hosts may register. The displayable host with the lowest [priority]
     * owns the video — the main player ([SURFACE_PRIORITY_MAIN]) wins whenever it is on screen,
     * and the mini player ([SURFACE_PRIORITY_MINI]) takes over only while it is not. Within the
     * same priority the most recently attached host wins, so re-opening a screen restores it.
     */
    fun attachVideoSurface(
        component: java.awt.Component,
        priority: Int = SURFACE_PRIORITY_MAIN
    ) {
        videoSurfaceHosts.removeAll { it.component === component }
        videoSurfaceHosts.add(SurfaceHost(component, priority))
        if (!surfaceListeners.containsKey(component)) {
            val listener = java.awt.event.HierarchyListener { event ->
                if (event.changeFlags and SURFACE_CHANGE_MASK != 0L && component.isDisplayable) {
                    bindRegisteredSurface()
                }
            }
            surfaceListeners[component] = listener
            component.addHierarchyListener(listener)
        }
        ensurePlayer()
        bindRegisteredSurface()
    }

    /**
     * Unregister [component] as a video-surface host (every host when [component] is null) and
     * hand the video back to the next remaining host, if any.
     */
    fun detachVideoSurface(component: java.awt.Component? = null) {
        val removed: List<java.awt.Component> = if (component == null) {
            videoSurfaceHosts.map { it.component }
        } else {
            videoSurfaceHosts.removeAll { it.component === component }
            listOf(component)
        }
        for (host in removed) {
            surfaceListeners.remove(host)?.let { host.removeHierarchyListener(it) }
        }
        if (component == null) {
            videoSurfaceHosts.clear()
        }
        bindRegisteredSurface()
    }

    private fun bindRegisteredSurface() {
        val mediaPlayer = player ?: return
        val currentFactory = factory ?: return
        // Most recently registered first, so ties inside one priority go to the freshest host.
        val host = videoSurfaceHosts
            .asReversed()
            .filter { it.component.isDisplayable }
            .minByOrNull { it.priority }
            ?.component
        bindSurfaceTo(mediaPlayer, currentFactory, host)
    }

    private fun bindSurfaceTo(
        mediaPlayer: EmbeddedMediaPlayer,
        currentFactory: MediaPlayerFactory,
        host: java.awt.Component?
    ) {
        if (host == null || !host.isDisplayable) {
            try {
                mediaPlayer.videoSurface().set(null)
            } catch (_: Throwable) {
                // Nothing to detach yet; playback is unaffected.
            }
            return
        }
        bindSurface(mediaPlayer, currentFactory, host, attach = true)
    }

    private fun bindSurface(
        mediaPlayer: EmbeddedMediaPlayer,
        factory: MediaPlayerFactory,
        component: java.awt.Component,
        attach: Boolean
    ) {
        try {
            mediaPlayer.videoSurface().set(factory.videoSurfaces().newVideoSurface(component))
            if (attach && component.isDisplayable) {
                mediaPlayer.videoSurface().attachVideoSurface()
            }
        } catch (t: Throwable) {
            // A missing surface must not crash playback; audio still plays. The hierarchy
            // listener retries as soon as the component is displayable, and prepare() re-binds.
            System.err.println("[NewPipe+] video surface not attached yet: ${t.message}")
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
            // VLCJ applies the stored surface from onBeforePlay() and throws when the host
            // component is not displayable yet, which would fail the whole play call. Clear it
            // so playback starts regardless; the hierarchy listener attaches the surface the
            // moment the host window is displayable.
            if (videoSurfaceHosts.none { it.component.isDisplayable }) {
                mediaPlayer.videoSurface().set(null)
            }
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
        detachVideoSurface(null)
        player?.release()
        factory?.release()
        player = null
        factory = null
    }

    /** A registered video-surface host: the component libVLC may draw into, and its priority. */
    private class SurfaceHost(
        val component: java.awt.Component,
        val priority: Int
    )

    companion object {
        const val UNAVAILABLE_MESSAGE =
            "libVLC was not found. Install VLC or point VLC_HOME at it to enable desktop playback."
        private const val MAX_VLC_VOLUME = 100

        /** The main player screen owns the video whenever it is on screen. */
        const val SURFACE_PRIORITY_MAIN = 0

        /** The mini player owns the video only while the main player screen is not shown. */
        const val SURFACE_PRIORITY_MINI = 1

        /** Hierarchy transitions that change whether a surface component can own an HWND. */
        private val SURFACE_CHANGE_MASK =
            (java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED or
                java.awt.event.HierarchyEvent.SHOWING_CHANGED).toLong()
    }
}
