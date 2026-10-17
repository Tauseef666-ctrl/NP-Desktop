/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.newpipe.app.image.rememberThumbnailPainter
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.theme.spaceXSmall
import net.newpipe.app.theme.spaceXXSmall

private val ThumbnailShape = RoundedCornerShape(10.dp)
private val BadgeShape = RoundedCornerShape(4.dp)
private val DefaultCardWidth = 240.dp

/**
 * A 16:9 media thumbnail with a title and optional metadata line, used in rails and grids.
 *
 * @param title Video title (max two lines).
 * @param thumbnail Optional artwork; falls back to an [surfaceElevated] placeholder.
 * @param thumbnailUrl Remote artwork URL, loaded asynchronously (and cached) when [thumbnail] is
 *   not supplied; ignored otherwise.
 * @param subtitle Optional metadata line (channel, views, date).
 * @param durationText Optional duration badge text, e.g. `12:34`.
 * @param isLive When true, shows a red `LIVE` badge instead of the duration.
 * @param onClick Invoked when the card is activated.
 */
@Composable
fun VideoCard(
    title: String,
    modifier: Modifier = Modifier,
    thumbnail: Painter? = null,
    thumbnailUrl: String? = null,
    subtitle: String? = null,
    durationText: String? = null,
    isLive: Boolean = false,
    cardWidth: Dp = DefaultCardWidth,
    onClick: () -> Unit = {}
) {
    val colors = plusColors()
    val artwork = thumbnail ?: rememberThumbnailPainter(thumbnailUrl)
    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(ThumbnailShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(ThumbnailShape)
                .background(colors.surfaceElevated)
        ) {
            if (artwork != null) {
                Image(
                    painter = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            when {
                isLive -> Badge(
                    text = "LIVE",
                    background = colors.accent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(spaceSmall)
                )

                durationText != null -> Badge(
                    text = durationText,
                    background = colors.overlayScrim,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(spaceSmall)
                )
            }
        }
        Spacer(Modifier.height(spaceSmall))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Spacer(Modifier.height(spaceXXSmall))
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

@Composable
private fun Badge(text: String, background: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = modifier
            .clip(BadgeShape)
            .background(background)
            .padding(horizontal = spaceXSmall, vertical = spaceXXSmall)
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun VideoCardPreview() {
    VideoCard(
        title = "NewPipe+ — a private, polished media experience",
        subtitle = "NewPipe • 1.2M views • 3 days ago",
        durationText = "12:34"
    )
}
