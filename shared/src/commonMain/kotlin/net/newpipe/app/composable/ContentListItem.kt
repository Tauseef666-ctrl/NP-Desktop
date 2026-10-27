/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.model.ContentItem
import net.newpipe.app.util.formatDuration
import net.newpipe.app.util.formatViewCount

/**
 * A single entry of a content list, for example a search result or a channel upload.
 *
 * @param item the content to show
 * @param modifier the modifier to be applied to the composable
 * @param onClick invoked when the user clicks the item
 */
@Composable
fun ContentListItem(
    item: ContentItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    VideoActionsMenuHost(item = item, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
        ContentThumbnail(
            item = item,
            modifier = Modifier.width(168.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = buildSubtitle(item)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        }
    }
}

/**
 * The thumbnail of a content item including the duration and live badges.
 */
@Composable
fun ContentThumbnail(
    item: ContentItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val thumbnailUrl = item.thumbnailUrl
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (item.liveNow) {
            ThumbnailBadge(
                text = "LIVE",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                color = Color(0xFFCC0000)
            )
        } else {
            item.durationSeconds?.let { duration ->
                ThumbnailBadge(
                    text = formatDuration(duration),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * A small badge drawn on top of a thumbnail.
 */
@Composable
private fun ThumbnailBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Black.copy(alpha = 0.7f)
) {
    Surface(
        modifier = modifier,
        color = color,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

/**
 * Builds the secondary line of a content item, for example `Uploader · 1.2M views`.
 */
private fun buildSubtitle(item: ContentItem): String {
    val parts = mutableListOf<String>()

    item.uploader?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    item.viewCount?.let { parts.add("${formatViewCount(it)} views") }
    item.uploadDate?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

    return parts.joinToString(" · ")
}
