/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import kotlinx.coroutines.flow.StateFlow

/**
 * One decoded video frame.
 *
 * The pixels are stored as 32 bit ARGB values, which is the buffer format libVLC delivers. The
 * array is immutable from the point of view of the consumer: every frame is a new instance.
 */
class VideoFrame(
    val width: Int,
    val height: Int,
    val pixels: IntArray
)

/**
 * Provides the decoded video frames of a player to the user interface.
 */
interface VideoFrameProvider {

    /**
     * The most recent frame, or `null` while nothing is being displayed.
     */
    val frame: StateFlow<VideoFrame?>
}
