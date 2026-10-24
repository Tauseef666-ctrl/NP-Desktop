/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import net.newpipe.app.service.PlaybackController

/**
 * Shows the video output of the player.
 *
 * The desktop implementation embeds the native video surface of libVLC into the Compose layout.
 * Platforms without a video surface implementation show an empty black area.
 *
 * @param controller the controller the video output belongs to
 * @param contentScale how the picture is fitted into the player area
 * @param zoom an additional zoom factor that is applied on top of [contentScale]
 */
@Composable
expect fun VideoSurface(
    controller: PlaybackController,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    zoom: Float = 1f
)
