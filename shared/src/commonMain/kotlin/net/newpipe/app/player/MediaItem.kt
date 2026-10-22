/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import kotlinx.serialization.Serializable

/**
 * A single playable entry in the queue or library, independent of any provider.
 *
 * @param id Stable identity used for resume positions and de-duplication.
 * @param url The resolved stream URL the engine plays.
 * @param fileExtension Container suffix of the stream (e.g. "mp4"), when the provider knew it.
 * @param audioUrl Separate audio track for a video-only stream (DASH), played as a libVLC slave.
 */
@Serializable
data class MediaItem(
    val id: String,
    val title: String,
    val url: String = "",
    val durationMs: Long = 0L,
    val isLive: Boolean = false,
    val audioOnly: Boolean = false,
    val thumbnailUrl: String? = null,
    val fileExtension: String? = null,
    val audioUrl: String? = null,
    /**
     * Where to start playback, set by the resume logic (F9). `0` means start from the beginning.
     * The engine seeks here once content is loaded.
     */
    val resumePositionMs: Long = 0L
)
