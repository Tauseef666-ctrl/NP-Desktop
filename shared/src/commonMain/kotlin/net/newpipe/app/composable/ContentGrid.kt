/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.model.ContentItem
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import net.newpipe.app.util.formatDuration
import net.newpipe.app.util.formatViewCount

/**
 * Shows content items in a grid whose column count follows the available width.
 *
 * @param items the items to show
 * @param onOpenItem invoked when an item is activated
 * @param modifier the modifier to apply to the grid
 * @param header optional content shown above the items, spanning the full width
 */
@Composable
fun ContentGrid(
    items: List<ContentItem>,
    onOpenItem: (ContentItem) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                header()
            }
        }

        items(items, key = { it.url }) { item ->
            ContentCard(
                item = item,
                onClick = { onOpenItem(item) }
            )
        }
    }
}

/**
 * A single grid entry: a thumbnail with the title and the metadata below it.
 */
@Composable
fun ContentCard(
    item: ContentItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column {
            val thumbnailUrl = item.thumbnailUrl
            if (thumbnailUrl != null) {
                Box {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        contentScale = ContentScale.Crop
                    )

                    val duration = item.durationSeconds
                    if (duration != null && duration > 0 && !item.liveNow) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Spacing.sm),
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(Radius.sm)
                        ) {
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.xs,
                                    vertical = 2.dp
                                )
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val metadata = buildContentMetadata(item)
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }
        }
    }
}

/**
 * Builds the metadata line shown below the title of a grid entry.
 */
fun buildContentMetadata(item: ContentItem): String {
    val parts = mutableListOf<String>()
    item.uploader?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    item.viewCount?.let { parts.add("${formatViewCount(it)} views") }
    if (item.liveNow) parts.add("LIVE")
    return parts.joinToString(" \u2022 ")
}
