/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * One decoded video frame.
 *
 * The frame carries a ready-to-draw [ImageBitmap]: the pixel conversion is done on the libVLC
 * render thread, so the user interface only swaps a reference and draws. Every frame is a new
 * instance, which lets a consumer compare frames by identity to detect real progress.
 */
class VideoFrame(
    val width: Int,
    val height: Int,
    val image: ImageBitmap
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
