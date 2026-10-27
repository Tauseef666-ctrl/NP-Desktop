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
    val qualityLabel: String? = null
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
    val qualityLabel: String? = null
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
