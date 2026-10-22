/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.PlusTooltip
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.component.StatusChip
import net.newpipe.app.component.StatusKind
import net.newpipe.app.image.rememberThumbnailPainter
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlayerQueueState
import net.newpipe.app.player.RepeatMode
import net.newpipe.app.player.isAvailable
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.theme.spaceXSmall
import net.newpipe.app.util.formatDuration
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.player_queue_current
import newpipe.shared.generated.resources.player_queue_empty_message
import newpipe.shared.generated.resources.player_queue_empty_title
import newpipe.shared.generated.resources.player_queue_move_down
import newpipe.shared.generated.resources.player_queue_move_up
import newpipe.shared.generated.resources.player_queue_play_now
import newpipe.shared.generated.resources.player_queue_remove
import newpipe.shared.generated.resources.player_queue_restore_playlist
import newpipe.shared.generated.resources.player_queue_restored
import newpipe.shared.generated.resources.player_queue_save_playlist
import newpipe.shared.generated.resources.player_queue_saved
import newpipe.shared.generated.resources.player_queue_skipped_unavailable
import newpipe.shared.generated.resources.player_queue_skip_unavailable
import newpipe.shared.generated.resources.player_queue_title
import org.jetbrains.compose.resources.stringResource

/**
 * Smart-queue panel (F6): lists the live queue with per-item play/reorder/remove controls and
 * footer actions to save or restore the queue as a playlist.
 *
 * @param queue Current queue snapshot; the UI never mutates it directly.
 * @param canRestore Whether a saved playlist exists to restore into the queue.
 * @param onPlayNow Start the item at the given index immediately.
 * @param onMoveUp Move the item at the given index one position earlier.
 * @param onMoveDown Move the item at the given index one position later.
 * @param onRemove Drop the item at the given index from the queue.
 * @param onSavePlaylist Persist the queue as a playlist; returns true when something was saved.
 * @param onRestorePlaylist Replace the queue with a saved playlist; returns true when restored.
 * @param onSkipUnavailable Drop unplayable queue items; returns true when at least one was dropped.
 */
@Composable
fun PlayerQueuePanel(
    queue: PlayerQueueState,
    modifier: Modifier = Modifier,
    canRestore: Boolean = false,
    onPlayNow: suspend (Int) -> Unit = {},
    onMoveUp: (Int) -> Unit = {},
    onMoveDown: (Int) -> Unit = {},
    onRemove: (Int) -> Unit = {},
    onSavePlaylist: suspend () -> Boolean = { false },
    onRestorePlaylist: suspend () -> Boolean = { false },
    onSkipUnavailable: suspend () -> Boolean = { false },
    onToggleShuffle: () -> Unit = {},
    onRepeatMode: (RepeatMode) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    val title = stringResource(Res.string.player_queue_title)
    val saveLabel = stringResource(Res.string.player_queue_save_playlist)
    val restoreLabel = stringResource(Res.string.player_queue_restore_playlist)
    val savedLabel = stringResource(Res.string.player_queue_saved)
    val restoredLabel = stringResource(Res.string.player_queue_restored)
    val skippedLabel = stringResource(Res.string.player_queue_skipped_unavailable)
    val hasUnavailable = queue.items.any { !it.isAvailable() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spaceLarge, vertical = spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spaceSmall)
        ) {
            Text(
                text = "Up next (${queue.items.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = plusColors().textPrimary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onRepeatMode(nextRepeatMode(queue.repeatMode)) }) {
                Text("Repeat: ${repeatModeLabel(queue.repeatMode)}")
            }
            TextButton(onClick = onToggleShuffle) {
                Text(if (queue.isShuffled) "Shuffle: on" else "Shuffle: off")
            }
        }
        if (queue.isEmpty) {
            EmptyState(
                title = stringResource(Res.string.player_queue_empty_title),
                message = stringResource(Res.string.player_queue_empty_message)
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spaceLarge)) {
                queue.items.forEachIndexed { index, item ->
                    QueueItemRow(
                        position = index + 1,
                        item = item,
                        isCurrent = index == queue.currentIndex,
                        canMoveUp = index > 0,
                        canMoveDown = index < queue.items.lastIndex,
                        onPlayNow = { scope.launch { onPlayNow(index) } },
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onRemove = { onRemove(index) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(spaceLarge),
                horizontalArrangement = Arrangement.spacedBy(spaceSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { scope.launch { if (onSavePlaylist()) status = savedLabel } },
                    enabled = true
                ) { Text(saveLabel) }
                TextButton(
                    onClick = { scope.launch { if (onRestorePlaylist()) status = restoredLabel } },
                    enabled = canRestore
                ) { Text(restoreLabel) }
                TextButton(
                    onClick = { scope.launch { if (onSkipUnavailable()) status = skippedLabel } },
                    enabled = hasUnavailable
                ) { Text(stringResource(Res.string.player_queue_skip_unavailable)) }
            }
            if (status != null) {
                Text(
                    text = status!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = plusColors().accent,
                    modifier = Modifier.padding(horizontal = spaceLarge, vertical = spaceXSmall)
                )
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    position: Int,
    item: MediaItem,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlayNow: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = plusColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) colors.surfaceElevated else Color.Transparent)
            .clickable(onClick = onPlayNow)
            .padding(vertical = spaceSmall, horizontal = spaceXSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = position.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrent) colors.accent else colors.textMuted,
            modifier = Modifier.width(28.dp)
        )
        val thumbnail = rememberThumbnailPainter(item.thumbnailUrl)
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    painter = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.width(spaceSmall))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) colors.textPrimary else colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = itemSubtitle(item)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isCurrent) {
            StatusChip(
                text = stringResource(Res.string.player_queue_current),
                kind = StatusKind.LIVE
            )
            Spacer(Modifier.width(spaceSmall))
        }
        QueueIconButton(
            glyph = "\u2191",
            description = stringResource(Res.string.player_queue_move_up),
            enabled = canMoveUp,
            onClick = onMoveUp
        )
        QueueIconButton(
            glyph = "\u2193",
            description = stringResource(Res.string.player_queue_move_down),
            enabled = canMoveDown,
            onClick = onMoveDown
        )
        QueueIconButton(
            glyph = "\u2715",
            description = stringResource(Res.string.player_queue_remove),
            enabled = true,
            onClick = onRemove
        )
    }
}

@Composable
private fun QueueIconButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    PlusTooltip(text = description) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = description }
        ) {
            Text(text = glyph, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun itemSubtitle(item: MediaItem): String = when {
    item.isLive -> "Live"
    item.durationMs > 0L -> formatDuration(item.durationMs / 1000L)
    item.url.isNotBlank() -> item.url
    else -> ""
}

private fun nextRepeatMode(mode: RepeatMode): RepeatMode = when (mode) {
    RepeatMode.OFF -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.OFF
}

private fun repeatModeLabel(mode: RepeatMode): String = when (mode) {
    RepeatMode.OFF -> "Off"
    RepeatMode.ALL -> "All"
    RepeatMode.ONE -> "One"
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlayerQueuePanelPreview() {
    PlayerQueuePanel(
        queue = PlayerQueueState(
            items = listOf(
                MediaItem(id = "1", title = "A private, polished media experience", durationMs = 213_000L),
                MediaItem(id = "2", title = "Next up in the queue", durationMs = 180_000L),
                MediaItem(id = "3", title = "Live radio stream", isLive = true)
            ),
            currentIndex = 0
        ),
        canRestore = true
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlayerQueuePanelEmptyPreview() {
    PlayerQueuePanel(queue = PlayerQueueState())
}
