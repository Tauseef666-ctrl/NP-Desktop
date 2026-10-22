/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import kotlinx.coroutines.flow.StateFlow

/** Lifecycle of the player, mirroring the states surfaced by the legacy player. */
enum class PlaybackStatus { IDLE, LOADING, READY, PLAYING, PAUSED, ENDED, ERROR }

/** Requested video quality. [height] is `0` for automatic selection. */
enum class Quality(val label: String, val height: Int) {
    AUTO("Auto", 0),
    Q144("144p", 144),
    Q240("240p", 240),
    Q360("360p", 360),
    Q480("480p", 480),
    Q720("720p", 720),
    Q1080("1080p", 1080),
    Q1440("1440p", 1440),
    Q2160("2160p", 2160)
}

/** Failure that prevented/ended playback. Never used as a stand-in for real playback. */
sealed interface PlaybackError {
    val message: String

    /** The media source could not be resolved or fetched. */
    data class Source(override val message: String) : PlaybackError

    /** The playback engine itself failed. */
    data class Engine(override val message: String) : PlaybackError

    /** The current platform has no playback engine available. */
    data class Unsupported(override val message: String) : PlaybackError
}

/**
 * Immutable snapshot of player state, emitted through [PlayerEngine.state].
 */
data class PlaybackState(
    val mediaId: String? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
    val quality: Quality = Quality.AUTO,
    val error: PlaybackError? = null
)

/**
 * Platform-independent playback engine. Real decoding lives in per-platform implementations
 * (VLCJ on desktop/Android-ExoPlayer later); the shared UI only ever talks to this interface.
 */
interface PlayerEngine {

    /** False when the current platform has no working engine; the UI must then show an error. */
    val isAvailable: Boolean

    /** Current state; collectors render controls from this. */
    val state: StateFlow<PlaybackState>

    /** Resolve/load an item, emitting [PlaybackStatus.LOADING] then READY/ERROR. */
    suspend fun prepare(item: MediaItem)

    fun play()
    fun pause()
    fun stop()

    fun togglePlayPause() {
        if (state.value.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun setMuted(muted: Boolean)
    fun setQuality(quality: Quality)

    /**
     * Enable or disable the video track of the current playback. Disabling keeps the audio playing
     * and stops the video decoding — what the Android app calls background play. Engines that
     * cannot switch tracks ignore the request.
     */
    fun setVideoEnabled(enabled: Boolean) {}

    /** Release all native resources. The engine must not be used afterwards. */
    fun release()
}
