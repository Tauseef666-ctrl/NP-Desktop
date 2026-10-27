/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import net.newpipe.app.service.PlaybackController

/**
 * Android does not provide a shared video surface yet, so the area stays black.
 */
@Composable
actual fun VideoSurface(
    controller: PlaybackController,
    modifier: Modifier,
    contentScale: ContentScale,
    zoom: Float
) {
    Box(modifier = modifier.background(Color.Black))
}
