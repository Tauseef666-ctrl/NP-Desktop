/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.newpipe.app.composable.ContentThumbnail
import net.newpipe.app.composable.VideoActionsMenuHost
import net.newpipe.app.model.ContentItem
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.service.WatchHistoryRepository
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_history
import newpipe.shared.generated.resources.ic_play_arrow
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * The watch history screen: the videos the user already watched, in recency order.
 */
@Composable
fun HistoryScreen(
    navigator: Navigator = koinInject(),
    history: WatchHistoryRepository = koinInject()
) {
    val items by history.items.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (items.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(Radius.lg)
                    ) {
                        Text(
                            text = items.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = Spacing.sm,
                                vertical = Spacing.xs
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = { history.clear() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Spacing.md,
                            vertical = Spacing.xs
                        )
                    ) {
                        Text("Clear all")
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item(key = "empty") {
                HistoryEmptyState()
            }
        } else {
            items(items, key = { it.url }) { item ->
                VideoActionsMenuHost(item = item) {
                    HistoryRow(
                        item = item,
                        onClick = {
                            navigator.navigateTo(Destination.Stream(item.serviceId, item.url))
                        }
                    )
                }
            }
        }
    }
}

/**
 * The empty state of the watch history.
 */
@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(Radius.lg)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(Spacing.lg)
                    .size(32.dp)
            )
        }
        Text(
            text = "No watch history yet",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Videos you watch show up here and drive the recommendations on home.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A single row of the watch history list.
 */
@Composable
private fun HistoryRow(
    item: ContentItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(180.dp)) {
                ContentThumbnail(item = item)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = buildString {
                        append(item.uploader ?: "Unknown")
                        item.viewCount?.let { append(" \u00B7 ") ; append(formatViews(it)) }
                        item.uploadDate?.takeIf { it.isNotBlank() }?.let { append(" \u00B7 ") ; append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onClick) {
                Icon(
                    painter = painterResource(Res.drawable.ic_play_arrow),
                    contentDescription = "Watch again",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun formatViews(count: Long): String {
    return if (count >= 1_000_000_000) {
        "%.1fB views".format(count / 1_000_000_000f)
    } else if (count >= 1_000_000) {
        "%.1fM views".format(count / 1_000_000f)
    } else if (count >= 1_000) {
        "%.1fK views".format(count / 1_000f)
    } else {
        "$count views"
    }
}
