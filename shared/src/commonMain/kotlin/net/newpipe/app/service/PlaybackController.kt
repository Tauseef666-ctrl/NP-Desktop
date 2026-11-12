/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import kotlinx.coroutines.flow.StateFlow

/**
 * Everything that is needed to start playing a stream.
 *
 * NewPipe usually extracts a video stream without audio plus a separate audio only stream, so both
 * are described separately. When [audioUrl] is `null` the video stream already contains the audio.
 */
data class PlaybackRequest(
    val title: String,
    val videoUrl: String,
    val audioUrl: String? = null,
    val thumbnailUrl: String? = null,
    val startPositionMs: Long = 0,
    val serviceId: Int = -1,
    val webUrl: String = "",
    val qualityLabel: String? = null,
    val live: Boolean = false,
    /**
     * A muxed progressive stream (video with its audio in one direct url) that the player
     * switches to when the chosen stream cannot deliver any media at all — the SABR route
     * walled by YouTube, for example. It must be a direct url, never another stream of the
     * same delivery route.
     */
    val fallbackVideoUrl: String? = null,
    val fallbackAudioUrl: String? = null,
    val fallbackQualityLabel: String? = null
)

/**
 * The state of the player as it is shown in the user interface.
 */
data class PlaybackState(
    val title: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int = 100,
    val muted: Boolean = false,
    val speed: Float = 1.0f,
    val loading: Boolean = false,
    val error: String? = null,
    val serviceId: Int = -1,
    val webUrl: String = "",
    val qualityLabel: String? = null,
    val live: Boolean = false
)

/**
 * Controls the media playback.
 *
 * The implementation is platform specific: the desktop uses libVLC through VLCJ, the other
 * platforms currently do not support playback at all.
 */
interface PlaybackController {

    /**
     * The current state of the player.
     */
    val state: StateFlow<PlaybackState>

    /**
     * Starts playing the given request, replacing whatever is playing at the moment.
     */
    fun play(request: PlaybackRequest)

    /**
     * Whether the stream addressed by [url] is temporarily unavailable: its SABR route was
     * walled by YouTube moments ago, so picking it would only dead-load and fall back. The
     * player interface uses this to mark such qualities in the quality menu instead of
     * letting the user bounce off them. The state heals by itself: a fresh attempt is
     * allowed again shortly after the last walled connection.
     */
    fun isQualityTemporarilyUnavailable(url: String): Boolean = false

    /**
     * Resumes playback.
     */
    fun resume()

    /**
     * Pauses playback.
     */
    fun pause()

    /**
     * Stops playback and releases the media.
     */
    fun stop()

    /**
     * Stops playback, releases the media and clears the current title.
     *
     * The user settings, such as the volume and the speed, are kept.
     */
    fun clear()

    /**
     * Seeks to the given position in milliseconds.
     */
    fun seekTo(positionMs: Long)

    /**
     * Sets the volume, `0` to `100`.
     */
    fun setVolume(volume: Int)

    /**
     * Mutes or unmutes the playback.
     */
    fun setMuted(muted: Boolean)

    /**
     * Sets the playback speed, `1.0` being normal speed.
     */
    fun setSpeed(speed: Float)

    /**
     * Starts preparing the underlying player engine before the first playback, so that the
     * first video starts without an engine setup delay. Implementations may ignore this.
     */
    fun warmUp() {}

    /**
     * Releases the native resources that are held by the controller.
     *
     * The controller must not be used afterwards.
     */
    fun release()
}

/**
 * Creates the playback controller for the current platform.
 */
expect fun createPlaybackController(): PlaybackController
