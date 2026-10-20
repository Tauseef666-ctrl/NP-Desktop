/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Hosts the engine's video output inside the shared player frame.
 *
 * Desktop/JVM embeds the native libVLC surface (AWT component via the engine). Platforms without a
 * real video sink render an empty surface so layout stays stable — they never draw fake video.
 *
 * @param surfacePriority Which host owns the video when several are on screen. `0` is the main
 * player (wins whenever it is visible), `1` is the mini player (takes over only while the main
 * player is not). Ignored on platforms with no surface priority concept.
 */
@Composable
expect fun PlayerVideoSurface(
    engine: PlayerEngine,
    modifier: Modifier = Modifier,
    surfacePriority: Int = 0
)
