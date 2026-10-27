/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity

/**
 * One registered clickable area of the secondary click bridge.
 */
internal class SecondaryArea(val onSecondaryClick: () -> Unit) {

    /** The window the area was last verified against, for cross-window filtering. */
    @Volatile
    var ownerWindow: java.awt.Window? = null

    /** The bounds of the area in Compose window coordinates. */
    @Volatile
    var bounds: Rect = Rect.Zero

    /** The Compose density scale of the window; Compose units -> physical pixels. */
    @Volatile
    var scale: Float = 1f

    /** Whether the pointer is currently over the area, tracked by the Compose hover state. */
    @Volatile
    var hovered: Boolean = false
}

/**
 * Bridges raw AWT secondary presses to the visible [SecondaryArea] under the cursor.
 *
 * This exists because the secondary button does not reach the Compose pointer pipeline in this
 * application; the press is delivered through the window and hit tested against the registered
 * areas, which mirrors what Compose would do with a normal pointer event.
 */
internal object SecondaryClickBridge {

    private val areas = mutableListOf<SecondaryArea>()

    private val listener = java.awt.event.AWTEventListener { event ->
        if (event !is java.awt.event.MouseEvent) {
            return@AWTEventListener
        }
        if (event.id != java.awt.event.MouseEvent.MOUSE_PRESSED) {
            return@AWTEventListener
        }
        if (event.button != java.awt.event.MouseEvent.BUTTON3) {
            return@AWTEventListener
        }
        val component = event.component ?: return@AWTEventListener
        val window = javax.swing.SwingUtilities.getWindowAncestor(component) ?: return@AWTEventListener

        // The click position relative to the origin of the compose content, in physical pixels:
        // the AWT position is taken in the space of the whole window, which includes the title
        // bar and the borders, so the window insets shift it back into the client area.
        val windowPoint = javax.swing.SwingUtilities.convertPoint(
            component,
            event.point,
            window
        )
        val insets = window.insets
        val physicalX = (windowPoint.x - insets.left).toFloat()
        val physicalY = (windowPoint.y - insets.top).toFloat()

        val candidates = synchronized(areas) { areas.toList() }
        val inWindow: (SecondaryArea) -> Boolean = { area ->
            area.ownerWindow == null || area.ownerWindow === window
        }

        // The hovered area always wins: the Compose hover state knows exactly which card the
        // pointer is over, even when the geometric bounds of a scrolling grid are stale for a
        // moment. The bounds are only used as a last resort.
        val target = candidates.lastOrNull { area -> area.hovered && inWindow(area) }
            ?: candidates.lastOrNull { area ->
                val scale = if (area.scale > 0f) area.scale else 1f
                inWindow(area) && area.bounds.contains(
                    androidx.compose.ui.geometry.Offset(
                        physicalX / scale,
                        physicalY / scale
                    )
                )
            }

        target?.apply {
            ownerWindow = window
            onSecondaryClick()
        }
    }

    /** Initializes the global listener, safe to call more than once. */
    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(
            listener,
            java.awt.AWTEvent.MOUSE_EVENT_MASK
        )
        installed = true
    }

    fun register(area: SecondaryArea) {
        install()
        synchronized(areas) { areas.add(area) }
    }

    fun unregister(area: SecondaryArea) {
        synchronized(areas) { areas.remove(area) }
    }
}

/**
 * The desktop implementation: registers the bounds of this modifier position and forwards raw
 * AWT secondary presses that land inside them to [onSecondary].
 */
@Composable
internal actual fun Modifier.secondaryClickBridge(onSecondary: () -> Unit): Modifier {
    val area = remember { SecondaryArea(onSecondary) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()

    androidx.compose.runtime.DisposableEffect(area, density) {
        area.scale = density.density
        SecondaryClickBridge.register(area)
        onDispose {
            SecondaryClickBridge.unregister(area)
        }
    }

    // Publish the hover state on every recomposition, so that the bridge can target the
    // hovered area instead of relying on geometric bounds alone.
    area.hovered = hovered

    return hoverable(hoverSource)
        .onGloballyPositioned { coordinates ->
            area.bounds = coordinates.boundsInWindow()
        }
}
