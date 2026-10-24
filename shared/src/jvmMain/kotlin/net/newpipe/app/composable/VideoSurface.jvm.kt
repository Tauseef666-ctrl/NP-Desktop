/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.VideoFrame
import net.newpipe.app.service.VideoFrameProvider
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shows the video output of the player as a Compose image.
 *
 * libVLC delivers raw frames through a render callback instead of a native AWT component. A
 * heavyweight AWT video surface is always drawn above the Compose canvas, which used to cover the
 * player controls and the related column. Drawing the frames with Compose keeps the whole interface
 * in one layer, so overlays, clipping and hit testing behave exactly like the rest of the
 * application.
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
    val fallback = remember { mutableStateOf<VideoFrame?>(null) }
    val frame by provider?.frame?.collectAsState() ?: fallback

    val bitmap = remember(frame) { frame?.toImageBitmap() }

    Box(modifier = modifier.background(Color.Black).clipToBounds()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                    }
            )
        }
    }
}

/**
 * Converts the ARGB pixels of a libVLC frame into an [ImageBitmap].
 *
 * libVLC stores the pixels as `0x00RRGGBB` integers. Skia expects the raw bytes of a
 * [ColorType.BGRA_8888] image, so the int array has to be written in little endian byte order;
 * writing it in the default big endian order swaps the red and blue channels and gives the picture
 * a strong blue tint.
 */
private fun VideoFrame.toImageBitmap(): ImageBitmap {
    val info = ImageInfo(
        width = width,
        height = height,
        colorType = ColorType.BGRA_8888,
        alphaType = ColorAlphaType.OPAQUE
    )

    val bytes = ByteArray(pixels.size * 4)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels)

    return org.jetbrains.skia.Image
        .makeRaster(info, bytes, width * 4)
        .toComposeImageBitmap()
}
