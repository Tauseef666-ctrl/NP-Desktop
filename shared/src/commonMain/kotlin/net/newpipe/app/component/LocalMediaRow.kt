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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.theme.spaceXXSmall
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.library_missing_file
import newpipe.shared.generated.resources.library_thumbnail_pending
import org.jetbrains.compose.resources.stringResource

private val LocalRowThumbShape = RoundedCornerShape(8.dp)

/**
 * A row for local-library media: small thumbnail, title/meta and an optional "now playing" tint.
 */
@Composable
fun LocalMediaRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    durationText: String? = null,
    sizeText: String? = null,
    thumbnail: Painter? = null,
    isPlaying: Boolean = false,
    isMissing: Boolean = false,
    showThumbnailPlaceholder: Boolean = false,
    onClick: () -> Unit = {}
) {
    val colors = plusColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spaceNormal, vertical = spaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(16f / 9f)
                .clip(LocalRowThumbShape)
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    painter = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (showThumbnailPlaceholder) {
                Text(
                    text = stringResource(Res.string.library_thumbnail_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(spaceNormal))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = when {
                    isMissing -> colors.textSecondary
                    isPlaying -> colors.accent
                    else -> colors.textPrimary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(subtitle, durationText, sizeText).joinToString(" • ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(spaceXXSmall))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isMissing) {
            Spacer(Modifier.width(spaceSmall))
            Text(
                text = stringResource(Res.string.library_missing_file),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        } else if (isPlaying) {
            Spacer(Modifier.width(spaceSmall))
            Text(
                text = "PLAYING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun LocalMediaRowPreview() {
    LocalMediaRow(
        title = "NewPipe+ demo.mp4",
        subtitle = "Movies",
        durationText = "12:34",
        sizeText = "29.6 MB",
        isPlaying = true
    )
}
