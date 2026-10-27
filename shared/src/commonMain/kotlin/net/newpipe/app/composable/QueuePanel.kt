/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import net.newpipe.app.model.ContentItem
import net.newpipe.app.service.QueueRepository
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_close
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * The queue editor: the "up next" entries with reorder and removal, opened from the mini player
 * or wherever a queue overview is requested.
 *
 * @param queueRepository the queue of the session
 * @param onDismiss invoked when the panel is closed
 * @param onPlayNow invoked when an entry is activated; it leaves the queue and starts playing
 */
@Composable
fun QueuePanel(
    queueRepository: QueueRepository,
    onDismiss: () -> Unit,
    navigationDispatcher: (ContentItem) -> Unit
) {
    val queue by queueRepository.queue.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Play queue" + (if (queue.isEmpty()) "" else " (${queue.size})"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            if (queue.isEmpty()) {
                Text(
                    text = "The queue is empty. Use the context menu of a video to add it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(queue, key = { _, item -> item.url }) { index, item ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                item.uploader?.let { uploader ->
                                    Text(
                                        text = uploader,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                queueRepository.remove(item.url)
                                navigationDispatcher(item)
                                onDismiss()
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { queueRepository.moveUp(index) },
                                        enabled = index > 0
                                    ) {
                                        Text("\u2191", color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(
                                        onClick = { queueRepository.moveDown(index) },
                                        enabled = index < queue.lastIndex
                                    ) {
                                        Text("\u2193", color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(onClick = { queueRepository.remove(item.url) }) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_close),
                                            contentDescription = "Remove from queue",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (queue.isNotEmpty()) {
                TextButton(
                    onClick = { queueRepository.clear() },
                    content = { Text("Clear") }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                content = { Text("Close") }
            )
        }
    )
}
