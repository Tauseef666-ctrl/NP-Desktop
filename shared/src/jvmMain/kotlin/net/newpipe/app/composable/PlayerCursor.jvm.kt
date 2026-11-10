/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

/**
 * A transparent 1x1 cursor that hides the mouse pointer. Built once and reused, because Compose
 * asks for the configured icon again on every pointer event.
 */
private val hiddenPointer: PointerIcon by lazy {
    val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
    val cursor = Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "NP hidden pointer")
    PointerIcon(cursor)
}

/**
 * Hides the mouse pointer over the player with an invisible custom cursor through the Compose
 * pointer icon machinery.
 *
 * The icon is applied via [pointerHoverIcon], which Compose resolves after every pointer event
 * and writes to the window itself, so it can never be overwritten by the default cursor that
 * Compose resets over the video the moment the pointer moves again.
 *
 * @param hidden whether the pointer should currently be invisible over this area
 */
actual fun Modifier.playerCursor(hidden: Boolean): Modifier = if (hidden) {
    // `overrideDescendants` makes the invisible icon win against any icon the controls or other
    // nested components configure while the pointer is hidden.
    pointerHoverIcon(hiddenPointer, overrideDescendants = true)
} else {
    this
}
