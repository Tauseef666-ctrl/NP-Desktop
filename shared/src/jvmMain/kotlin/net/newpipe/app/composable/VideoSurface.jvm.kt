/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.VideoFrame
import net.newpipe.app.service.VideoFrameProvider
import kotlin.math.roundToInt

/**
 * Shows the video output of the player as a Compose image.
 *
 * libVLC delivers ready-to-draw frames through a render callback instead of a native AWT
 * component. A heavyweight AWT video surface is always drawn above the Compose canvas, which
 * used to cover the player controls and the related column. Drawing the frames with Compose
 * keeps the whole interface in one layer, so overlays, clipping and hit testing behave exactly
 * like the rest of the application.
 *
 * The current frame is read in the draw phase only: a new frame invalidates the redraw without
 * recomposing, so the composition stays out of the frame loop and the interface keeps its
 * animation smoothness while video plays.
 *
 * @param contentScale how the picture is fitted into the player area
 * @param zoom an additional zoom factor that is applied on top of [contentScale]
 */
@Composable
actual fun VideoSurface(
    controller: PlaybackController,
    modifier: Modifier,
    contentScale: ContentScale,
    zoom: Float
) {
    val provider = controller as? VideoFrameProvider
    // The shared fallback lets a consumer without a frame provider compile unchanged.
    val fallback = remember { mutableStateOf<VideoFrame?>(null) }
    val frameState: State<VideoFrame?> = provider?.frame?.collectAsState() ?: fallback

    // The frame is drawn directly on a canvas instead of through an `Image` painter, so that
    // no painter inset computation runs during the draw. Painters throw when their layout
    // size is zero or negative, which happens while the window is minimised or hidden.
    Canvas(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds()
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
            }
    ) {
        val image = frameState.value?.image ?: return@Canvas
        val imageWidth = image.width
        val imageHeight = image.height
        if (imageWidth <= 0 || imageHeight <= 0 || size.width <= 0f || size.height <= 0f) {
            return@Canvas
        }

        val widthScale = size.width / imageWidth
        val heightScale = size.height / imageHeight
        val metrics = when (contentScale) {
            ContentScale.FillBounds -> DrawMetrics(size.width, size.height, 0f, 0f)
            ContentScale.FillWidth -> DrawMetrics(size.width, imageHeight * widthScale, 0f, (size.height - imageHeight * widthScale) / 2f)
            ContentScale.FillHeight -> DrawMetrics(imageWidth * heightScale, size.height, (size.width - imageHeight * heightScale) / 2f, 0f)
            ContentScale.Crop, ContentScale.None -> DrawMetrics(size.width, size.height, 0f, 0f)
            else -> {
                val fitted = minOf(widthScale, heightScale)
                val fitWidth = imageWidth * fitted
                val fitHeight = imageHeight * fitted
                DrawMetrics(fitWidth, fitHeight, (size.width - fitWidth) / 2f, (size.height - fitHeight) / 2f)
            }
        }
        if (metrics.width <= 0f || metrics.height <= 0f) return@Canvas

        translate(metrics.dx, metrics.dy) {
            drawImage(
                image = image,
                dstSize = IntSize(metrics.width.roundToInt(), metrics.height.roundToInt()),
                dstOffset = IntOffset.Zero,
                srcSize = IntSize(imageWidth, imageHeight)
            )
        }
    }
}

private data class DrawMetrics(val width: Float, val height: Float, val dx: Float, val dy: Float)
