/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.newpipe.app.composable.ContentThumbnail
import net.newpipe.app.composable.VideoPlayer
import net.newpipe.app.model.DownloadedVideo
import net.newpipe.app.platform.openInFileBrowser
import net.newpipe.app.service.DownloadLocation
import net.newpipe.app.service.DownloadManager
import net.newpipe.app.service.DownloadRepository
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PlaybackRequest
import net.newpipe.app.ui.Glass
import net.newpipe.app.ui.PlayerUiState
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_close
import newpipe.shared.generated.resources.ic_cloud_download
import newpipe.shared.generated.resources.ic_play_arrow
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * The downloads section: the videos that were saved to the local storage.
 *
 * The player at the top is the same player as on the watch page, so a downloaded file is played
 * with the same controls, the same quality handling and the same keyboard shortcuts.
 */
@Composable
fun DownloadsScreen(
    repository: DownloadRepository = koinInject(),
    controller: PlaybackController = koinInject(),
    playerUiState: PlayerUiState = koinInject()
) {
    val downloads by repository.downloads.collectAsState()
    val audioOnly by playerUiState.audioOnly.collectAsState()
    val activeDownloads by DownloadManager.active.collectAsState()
    var selected by remember { mutableStateOf<DownloadedVideo?>(null) }
    var sortBy by remember { mutableStateOf(DownloadSort.RECENT) }
    var sortExpanded by remember { mutableStateOf(false) }
    // Active downloads are keyed by the video url, because the download entry id only exists
    // (as a file path) after the download finishes; the running entry carries the item url.
    val activeByUrl = activeDownloads.associateBy { it.itemUrl }.filterKeys { it.isNotBlank() }
    val sortedDownloads = remember(downloads, sortBy) { sortDownloads(downloads, sortBy) }

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
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (downloads.isNotEmpty()) {
                    Surface(
                        color = Glass.color(0.6f),
                        border = Glass.borderStroke(),
                        shape = RoundedCornerShape(Radius.lg)
                    ) {
                        Text(
                            text = downloads.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = Spacing.sm,
                                vertical = Spacing.xs
                            )
                        )
                    }
                }
            }
        }

        item(key = "toolbar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ExposedDropdownMenuBox(
                    expanded = sortExpanded,
                    onExpandedChange = { sortExpanded = it },
                    modifier = Modifier.width(180.dp)
                ) {
                    OutlinedTextField(
                        value = sortBy.label,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Sort") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sortExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        DownloadSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sortBy = option
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { openInFileBrowser(DownloadLocation.resolve()) }) {
                    Text("Open folder")
                }
            }
        }

        item(key = "legacy-hint") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Glass.color(0.55f),
                border = Glass.borderStroke(),
                shape = RoundedCornerShape(Radius.lg)
            ) {
                Text(
                    text = "Downloads made in earlier versions may be video-only; re-download them for video with audio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md)
                )
            }
        }

        selected?.let { download ->
            item(key = "player") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        color = MaterialTheme.colorScheme.scrim,
                        shape = RoundedCornerShape(Radius.lg)
                    ) {
                        VideoPlayer(
                            controller = controller,
                            thumbnailUrl = download.item.thumbnailUrl,
                            canPlay = true,
                            onPlay = { playDownloaded(download, controller) },
                            modifier = Modifier.fillMaxSize(),
                            onClose = {
                                controller.clear()
                                selected = null
                            },
                            audioOnly = audioOnly
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = download.item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(download.item.uploader ?: "Unknown")
                                    append(" \u00B7 ")
                                    append(download.quality)
                                    download.audioFilePath?.let { append(" \u00B7 audio file") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { playDownloaded(download, controller) }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_play_arrow),
                                contentDescription = "Play download"
                            )
                        }

                        IconButton(
                            onClick = {
                                controller.clear()
                                selected = null
                            }
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_close),
                                contentDescription = "Close player"
                            )
                        }
                    }
                }
            }
        }

        activeDownloads.forEach { active ->
            item(key = "active-${active.id}") {
                ActiveDownloadRow(active)
            }
        }

        if (downloads.isEmpty() && activeDownloads.isEmpty()) {
            item(key = "empty") {
                DownloadsEmptyState()
            }
        } else if (downloads.isEmpty() && activeDownloads.isNotEmpty()) {
            item(key = "active-only-hint") {
                Text(
                    text = "Downloads will appear here when they finish.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.sm)
                )
            }
        } else {
            items(sortedDownloads, key = { it.id }) { download ->
                DownloadRow(
                    download = download,
                    selected = download.id == selected?.id,
                    active = activeByUrl[download.item.url],
                    onOpen = {
                        selected = download
                        playDownloaded(download, controller)
                    },
                    onDelete = { repository.remove(download.id) }
                )
            }
        }
    }
}

/**
 * A download that is currently running: title, progress bar and the live statement of the
 * transferred bytes, the network speed and the estimated time to completion.
 */
@Composable
private fun ActiveDownloadRow(active: DownloadManager.Active) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = "${active.title} \u00B7 ${active.label}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = { active.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = downloadStatus(active),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Builds the live status line of a download: transferred bytes, network speed and the
 * estimated time to completion, or the merging notice while ffmpeg combines the files.
 */
private fun downloadStatus(active: DownloadManager.Active): String {
    if (active.merging) {
        return "Merging video and audio\u2026"
    }
    val details = mutableListOf<String>()
    if (active.totalBytes > 0L) {
        details.add("${formatBytes(active.downloadedBytes)} of ${formatBytes(active.totalBytes)}")
    }
    if (active.speedBytesPerSec > 0L) {
        details.add("${formatBytes(active.speedBytesPerSec)}/s")
    }
    active.etaSeconds?.let { eta ->
        if (eta > 0L) details.add("${formatDurationSeconds(eta)} left")
    }
    return if (details.isEmpty()) "Starting\u2026" else details.joinToString(" \u00B7 ")
}

private fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> "${(bytes / gb * 10).toInt() / 10.0} GB"
        bytes >= mb -> "${(bytes / mb * 10).toInt() / 10.0} MB"
        bytes >= kb -> "${(bytes / kb * 10).toInt() / 10.0} KB"
        else -> "$bytes B"
    }
}

private fun formatDurationSeconds(seconds: Long): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

/**
 * The empty state of the downloads section.
 */
@Composable
private fun DownloadsEmptyState() {
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
                painter = painterResource(Res.drawable.ic_cloud_download),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(Spacing.lg)
                    .size(32.dp)
            )
        }
        Text(
            text = "No downloads yet",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Use the Download action on a watch page to save a video for offline playback.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A single row of the downloads list.
 */
@Composable
private fun DownloadRow(
    download: DownloadedVideo,
    selected: Boolean,
    active: DownloadManager.Active?,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(180.dp)) {
                ContentThumbnail(item = download.item)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = download.item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${download.item.uploader ?: "Unknown"} \u00B7 ${download.quality}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = when (download.audioFilePath) {
                        null -> download.filePath
                        else -> "${fileExtension(download.filePath)} + ${fileExtension(download.audioFilePath)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (active != null) {
                    LinearProgressIndicator(
                        progress = { active.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = downloadStatus(active),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            IconButton(onClick = onOpen) {
                Icon(
                    painter = painterResource(Res.drawable.ic_play_arrow),
                    contentDescription = "Play download",
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = "Delete download",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Plays the downloaded file with the shared player.
 */
private fun playDownloaded(
    download: DownloadedVideo,
    controller: PlaybackController
) {
    val fileUrl = "file:///" + download.filePath.replace('\\', '/')
    val audioUrl = download.audioFilePath?.let { path ->
        "file:///" + path.replace('\\', '/')
    }
    controller.play(
        PlaybackRequest(
            title = download.item.title,
            videoUrl = fileUrl,
            audioUrl = audioUrl,
            thumbnailUrl = download.item.thumbnailUrl,
            qualityLabel = download.quality
        )
    )
}

private fun fileExtension(path: String?): String {
    return path?.substringAfterLast('.')?.uppercase() ?: ""
}

private enum class DownloadSort(val label: String) {
    RECENT("Recent"),
    TITLE("Title"),
    QUALITY("Quality")
}

private fun sortDownloads(
    downloads: List<DownloadedVideo>,
    sort: DownloadSort
): List<DownloadedVideo> = when (sort) {
    DownloadSort.RECENT -> downloads
    DownloadSort.TITLE -> downloads.sortedBy { it.item.title.lowercase() }
    DownloadSort.QUALITY -> downloads.sortedByDescending { it.quality }
}
