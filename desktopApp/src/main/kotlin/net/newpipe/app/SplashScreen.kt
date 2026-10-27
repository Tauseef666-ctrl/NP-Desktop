/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

internal val SplashDark = Color(0xFF0F0F0F)
internal val SplashRed = Color(0xFFFF0000)
internal val SplashWhite = Color.White

/**
 * Draws the NP Desktop logo into the canvas. The vector data matches `np_desktop_icon.svg`,
 * normalised to the `0..100` square and scaled to the size of the canvas.
 */
internal fun DrawScope.drawYtLogo() {
    val side = minOf(size.width, size.height)
    val scale = side / 100f
    val left = (size.width - side) / 2f
    val top = (size.height - side) / 2f

    translate(left, top) { drawLogo(scale) }
}

private fun DrawScope.drawLogo(scale: Float) {
    drawRect(SplashDark, size = Size(100f * scale, 100f * scale))

    val tPath = rememberTPath(scale)
    drawPath(tPath, SplashRed)

    val arrowPath = rememberArrowPath(scale)
    drawPath(arrowPath, SplashWhite)
}

private fun rememberTPath(scale: Float) = Path().apply {
    moveTo(32f * scale, 20f * scale)
    lineTo(68f * scale, 20f * scale)
    lineTo(68f * scale, 28f * scale)
    lineTo(54f * scale, 28f * scale)
    lineTo(54f * scale, 80f * scale)
    lineTo(46f * scale, 80f * scale)
    lineTo(46f * scale, 28f * scale)
    close()
}

private fun rememberArrowPath(scale: Float) = Path().apply {
    moveTo(24f * scale, 14f * scale)
    lineTo(24f * scale, 86f * scale)
    cubicTo(28f * scale, 83.5f * scale, 32f * scale, 81f * scale, 36f * scale, 78.5f * scale)
    lineTo(36f * scale, 36f * scale)
    cubicTo(45f * scale, 41.4f * scale, 54f * scale, 46.8f * scale, 63f * scale, 52.2f * scale)
    cubicTo(54f * scale, 57.6f * scale, 45f * scale, 63f * scale, 36f * scale, 68.4f * scale)
    lineTo(36f * scale, 75.6f * scale)
    cubicTo(52f * scale, 67f * scale, 67f * scale, 58.5f * scale, 82f * scale, 50f * scale)
    cubicTo(67f * scale, 41.5f * scale, 52f * scale, 33f * scale, 37f * scale, 24.5f * scale)
    cubicTo(33f * scale, 21f * scale, 28.5f * scale, 17.5f * scale, 24f * scale, 14f * scale)
    close()
}

/**
 * Loading screen that is shown while the player stack starts up.
 *
 * The NP Desktop logo is centred on a black background and pulses gently until the main
 * screen appears. The logo is drawn directly on a canvas, so no painter node is involved.
 */
@Composable
internal fun SplashScreen() {
    val alpha = rememberInfiniteTransition(label = "splash").animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashAlpha"
    )
    val logoAlpha by alpha

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer { this.alpha = logoAlpha }
        ) {
            drawYtLogo()
        }
    }
}
