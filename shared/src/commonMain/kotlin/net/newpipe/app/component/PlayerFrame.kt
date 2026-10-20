/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceNormal

/**
 * Player scaffold: a 16:9 surface with a video slot and an overlay controls slot, plus an
 * optional settings panel that lives *inside* the same frame (player and its settings stay
 * together).
 *
 * The overlay mirrors the official Android player ([R.layout.player]): a scrim at the top and
 * bottom of the video (the `playerTopShadow` / `playbackControlsShadow` views) with the controls
 * themselves filling the whole surface so they can pin metadata to the top, transport to the
 * center and the seek bar to the bottom — the same three-zone layout NewPipe uses on phones.
 *
 * @param video Rendered below the controls overlay (the engine's surface).
 * @param controls Full-surface overlay; arrange top/center/bottom yourself.
 * @param settings Shown below the surface when [settingsVisible] is true.
 * @param showControls Hide controls without stopping the engine (e.g. idle).
 */
@Composable
fun PlayerFrame(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    settingsVisible: Boolean = false,
    showControls: Boolean = true,
    video: @Composable BoxScope.() -> Unit = {},
    controls: @Composable BoxScope.() -> Unit = {},
    settings: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = plusColors()
    Column(modifier = modifier.background(colors.background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            video()
            if (showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to colors.overlayScrim,
                                0.18f to Color.Transparent,
                                0.72f to Color.Transparent,
                                1.0f to colors.overlayScrim
                            )
                        )
                ) {
                    controls()
                }
            }
        }
        if (title != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(spaceNormal)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (settingsVisible && settings != null) {
            Column(modifier = Modifier.fillMaxWidth()) { settings() }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlayerFramePreview() {
    PlayerFrame(
        title = "NewPipe+ — a private, polished media experience",
        subtitle = "NewPipe • 1.2M views",
        video = {
            Text(text = "VIDEO", color = Color.White)
        },
        controls = {
            Text(text = "▶  ────────  🔊", color = Color.White)
        }
    )
}
