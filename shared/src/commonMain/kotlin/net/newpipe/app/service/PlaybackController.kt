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
    val live: Boolean = false,
    /**
     * The playback runs without a video track (a downloaded audio file, for example): the
     * player shows the audio-only panel instead of an empty video surface.
     */
    val audioOnlyMedia: Boolean = false
)

/**
 * The result of a frame capture attempt.
 */
sealed interface FrameCapture {

    /**
     * The frame was saved to the given file path.
     */
    data class Saved(val path: String) : FrameCapture

    /**
     * There was no frame to capture: nothing has been played yet, or the media has no video.
     */
    data object NoFrame : FrameCapture

    /**
     * The capture failed.
     */
    data class Failed(val reason: String) : FrameCapture
}

/**
 * The engine statistics of the running playback, published a few times a second and shown by
 * the stats overlay.
 */
data class PlayerStatistics(
    /** The size of the picture that the decoder renders, `0` while unknown. */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** The frame rate of the video track in frames per second, `0` while unknown. */
    val frameRateFps: Float = 0f,
    /** The video frames the decoder has produced so far. */
    val decodedVideoFrames: Int = 0,
    /** The frames that were actually shown. */
    val displayedFrames: Int = 0,
    /** The frames that were late and never shown. */
    val lostPictures: Int = 0,
    /** The bitrate of the demuxed media, in kilobit per second. */
    val demuxBitrateKbps: Float = 0f,
    /** The bitrate of the input as it is read, in kilobit per second. */
    val inputBitrateKbps: Float = 0f,
    /** The media bytes the SABR relay has delivered for this playback, `0` for other sources. */
    val relayDeliveredBytes: Long = 0
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
     * The engine statistics of the running playback, refreshed a few times a second. Used by
     * the stats overlay; implementations without engine statistics publish empty values.
     */
    val statistics: StateFlow<PlayerStatistics>

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
     * Saves the newest video frame as a PNG picture and reports where it went. Implementations
     * without a video pipeline always report that there is no frame.
     */
    fun saveFrame(): FrameCapture = FrameCapture.NoFrame

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
