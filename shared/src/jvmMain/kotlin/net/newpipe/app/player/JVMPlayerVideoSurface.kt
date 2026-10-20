/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import net.newpipe.app.platform.JVMPlayerEngine
import java.awt.Canvas

/**
 * Embeds the native libVLC video surface into the Compose window. Only meaningful when [engine] is
 * the JVM VLCJ engine; any other engine renders nothing here.
 *
 * [SwingPanel] paints its own `background` over the component it hosts, so it is set to black —
 * otherwise the panel's white default shows through whenever no frames arrive yet. The canvas is
 * registered with the engine for as long as this composable is in composition: libVLC can only
 * draw into a displayable component, so ownership is handed over when the host appears and taken
 * back when it leaves.
 */
@Composable
actual fun PlayerVideoSurface(
    engine: PlayerEngine,
    modifier: Modifier,
    surfacePriority: Int
) {
    val jvmEngine = engine as? JVMPlayerEngine
        ?: return
    val canvas = remember {
        Canvas().apply {
            background = java.awt.Color.BLACK
        }
    }
    SwingPanel(
        background = Color.Black,
        factory = { canvas },
        modifier = modifier
    )
    DisposableEffect(canvas, jvmEngine, surfacePriority) {
        jvmEngine.attachVideoSurface(canvas, surfacePriority)
        onDispose {
            jvmEngine.detachVideoSurface(canvas)
        }
    }
}
