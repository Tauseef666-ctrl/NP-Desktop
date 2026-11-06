/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The frosted glass panels used across the application.
 *
 * A glass panel is a translucent, lightly tinted surface with a soft vertical shine on top and a
 * thin bright border. It is painted over the themed background, so the dark theme keeps its
 * readability while bright content scrolls behind the panel.
 */
object Glass {

    /**
     * The translucent surface color of a glass panel; a high [alpha] is used for panels with
     * small text that must stay readable on top of scrolling content.
     */
    @Composable
    fun color(alpha: Float = 0.88f): Color =
        MaterialTheme.colorScheme.surface.copy(alpha = alpha)

    /**
     * The thin bright border stroke of a glass panel.
     */
    fun borderStroke(width: androidx.compose.ui.unit.Dp = 1.dp): BorderStroke =
        BorderStroke(width, border)

    /**
     * The thin bright border of a glass panel.
     */
    val border: Brush
        get() = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.08f)
            )
        )

    /**
     * The soft shine that is painted over the upper area of a glass panel.
     */
    val shine: Brush
        get() = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.05f),
                Color.Transparent
            )
        )
}

/**
 * Paints the modifier owner as a glass panel of the given [shape].
 */
@Composable
fun Modifier.glassPanel(shape: Shape, alpha: Float = 0.88f): Modifier =
    this.border(Glass.borderStroke(), shape).background(Glass.color(alpha), shape)
