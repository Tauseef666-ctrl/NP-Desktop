/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.newpipe.app.model.MediaStream
import net.newpipe.app.model.StreamDetails
import net.newpipe.app.service.DownloadManager
import net.newpipe.app.service.DownloadRepository
import net.newpipe.app.service.MediaDownloader
import net.newpipe.app.model.ContentItem
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing

/**
 * The download options of a stream: the video qualities, an audio only download and the target
 * formats are chosen in a dialog before anything is downloaded.
 *
 * The dialog matches the frosted glass style of the other overlays.
 *
 * @param details the loaded stream details with the available streams
 * @param item the library entry the finished download is registered as
 * @param onStartCompleted invoked when the download has finished (or failed)
 * @param onDismiss invoked when the dialog is closed
 */
@Composable
fun DownloadDialog(
    details: StreamDetails,
    item: ContentItem,
    downloader: MediaDownloader,
    repository: DownloadRepository,
    onResult: (Result<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val muxedStreams = details.videoStreams
        .filterNot { it.isVideoOnly }
        .distinctBy { it.quality }
        .sortedByDescending { it.height ?: it.bitrate }

    val videoOnlyStreams = details.videoStreams
        .filter { it.isVideoOnly }
        .distinctBy { it.quality }
        .sortedByDescending { it.height ?: it.bitrate }

    val audioStreams = details.audioStreams
        .distinctBy { it.audioTrackName ?: it.quality }
        .sortedByDescending { it.bitrate }

    val supportsVideo = muxedStreams.isNotEmpty() || videoOnlyStreams.isNotEmpty()
    val supportsAudio = audioStreams.isNotEmpty()

    var audioOnly by remember { mutableStateOf(!supportsVideo && supportsAudio) }

    var muxedSelection by remember {
        mutableStateOf(muxedStreams.firstOrNull())
    }
    var videoOnlySelection by remember {
        mutableStateOf(videoOnlyStreams.firstOrNull())
    }
    var audioSelection by remember {
        mutableStateOf(audioStreams.firstOrNull())
    }
    var separateAudio by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 420.dp, max = 520.dp),
            shape = DOWNLOAD_SHAPE,
            color = net.newpipe.app.ui.Glass.color(0.92f),
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
            border = net.newpipe.app.ui.Glass.borderStroke()
        ) {
            Column(
                modifier = Modifier
                    .background(
                        net.newpipe.app.ui.Glass.shine
                    )
                    .padding(Spacing.lg)
            ) {
                Text(
                    text = "Download",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = details.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs)
                )

                if (supportsVideo && supportsAudio) {
                    Row(
                        modifier = Modifier
                            .padding(top = Spacing.lg)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        TypeChip(
                            label = "Video",
                            selected = !audioOnly,
                            onClick = { audioOnly = false },
                            modifier = Modifier.weight(1f)
                        )
                        TypeChip(
                            label = "Audio only",
                            selected = audioOnly,
                            onClick = { audioOnly = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(top = Spacing.md)
                        .heightIn(max = 320.dp)
                ) {
                    when {
                        audioOnly || !supportsVideo -> {
                            if (audioStreams.isEmpty()) {
                                InfoLine("No audio stream is available for this video")
                            } else {
                                QualityList(
                                    entries = audioStreams.map { stream ->
                                        QualityEntry(
                                            key = stream.url,
                                            title = audioLabel(stream),
                                            subtitle = audioSubtitle(stream)
                                        )
                                    },
                                    selectedKey = audioSelection?.url,
                                    onSelect = { selection ->
                                        audioSelection =
                                            audioStreams.firstOrNull { it.url == selection }
                                    }
                                )
                            }
                        }

                        muxedStreams.isNotEmpty() -> {
                            QualityList(
                                entries = muxedStreams.map { stream ->
                                    QualityEntry(
                                        key = stream.url,
                                        title = videoLabel(stream),
                                        subtitle = videoSubtitle(stream)
                                    )
                                },
                                selectedKey = muxedSelection?.url,
                                onSelect = { selection ->
                                    muxedSelection =
                                        muxedStreams.firstOrNull { it.url == selection }
                                }
                            )
                        }

                        else -> {
                            Column {
                                QualityList(
                                    entries = videoOnlyStreams.map { stream ->
                                        QualityEntry(
                                            key = stream.url,
                                            title = videoLabel(stream),
                                            subtitle = videoSubtitle(stream)
                                        )
                                    },
                                    selectedKey = videoOnlySelection?.url,
                                    onSelect = { selection ->
                                        videoOnlySelection =
                                            videoOnlyStreams.firstOrNull { it.url == selection }
                                    }
                                )

                                if (audioStreams.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = Spacing.xs)
                                    ) {
                                        Checkbox(
                                            checked = separateAudio,
                                            onCheckedChange = { separateAudio = it }
                                        )
                                        Text(
                                            text = "Include audio (separate file)",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    val start: (() -> Unit)? = when {
                        audioOnly || !supportsVideo -> audioSelection?.let { stream ->
                            { downloadAudioOnly(stream, item, downloader, repository, onResult) }
                        }

                        muxedStreams.isNotEmpty() -> muxedSelection?.let { stream ->
                            { downloadMuxed(stream, item, downloader, repository, onResult) }
                        }

                        else -> videoOnlySelection?.let { video ->
                            {
                                downloadSeparated(
                                    video = video,
                                    audio = if (separateAudio) audioSelection else null,
                                    audioStreams = audioStreams,
                                    item = item,
                                    downloader = downloader,
                                    repository = repository,
                                    onResult = onResult
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            start?.invoke()
                            onDismiss()
                        },
                        enabled = start != null
                    ) {
                        Text("Start download")
                    }
                }
            }
        }
    }
}

private data class QualityEntry(val key: String, val title: String, val subtitle: String)

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        )
    }
}

@Composable
private fun QualityList(
    entries: List<QualityEntry>,
    selectedKey: String?,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        entries.forEach { entry ->
            val selected = entry.key == selectedKey
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(entry.key) }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onSelect(entry.key) }
                )
                Column(modifier = Modifier.padding(start = Spacing.xs)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.md)
    )
}

private fun videoLabel(stream: MediaStream): String = stream.quality.ifEmpty { "Video" }

private fun videoSubtitle(stream: MediaStream): String = buildString {
    append(containerLabel(DownloadManager.videoContainerFor(stream)))
    stream.fps?.takeIf { it > 30 }?.let { append(" \u00B7 ${it}fps") }
    if (stream.isVideoOnly) {
        append(" \u00B7 without audio")
    }
}

private fun audioLabel(stream: MediaStream): String = when {
    stream.audioTrackName != null -> stream.audioTrackName
    stream.quality.isNotEmpty() -> stream.quality
    else -> "Audio"
}

private fun audioSubtitle(stream: MediaStream): String =
    "${containerLabel(DownloadManager.audioContainerFor(stream))} \u00B7 ${stream.bitrate / 1000} kbps"

private fun containerLabel(extension: String) = when (extension) {
    "m4a" -> "AAC (m4a)"
    else -> extension.uppercase()
}

private fun downloadMuxed(
    stream: MediaStream,
    item: ContentItem,
    downloader: MediaDownloader,
    repository: DownloadRepository,
    onResult: (Result<String>) -> Unit
) {
    DownloadManager.start(
        item = item,
        title = item.title,
        label = "${stream.quality} \u00B7 ${DownloadManager.videoContainerFor(stream)}",
        stream = stream,
        audio = null,
        downloader = downloader,
        repository = repository,
        onResult = onResult
    )
}

private fun downloadSeparated(
    video: MediaStream,
    audio: MediaStream?,
    audioStreams: List<MediaStream>,
    item: ContentItem,
    downloader: MediaDownloader,
    repository: DownloadRepository,
    onResult: (Result<String>) -> Unit
) {
    val audioStream = audio ?: audioStreams.maxByOrNull { it.bitrate }
    DownloadManager.start(
        item = item,
        title = item.title,
        label = buildList {
            add("${video.quality} \u00B7 ${DownloadManager.videoContainerFor(video)}")
            if (audioStream != null) {
                add("audio \u00B7 ${DownloadManager.audioContainerFor(audioStream)}")
            } else {
                add("without audio")
            }
        }.joinToString(" + "),
        stream = video,
        audio = audioStream,
        downloader = downloader,
        repository = repository,
        onResult = onResult
    )
}

private fun downloadAudioOnly(
    stream: MediaStream,
    item: ContentItem,
    downloader: MediaDownloader,
    repository: DownloadRepository,
    onResult: (Result<String>) -> Unit
) {
    DownloadManager.start(
        item = item,
        title = item.title,
        label = "${audioLabel(stream)} \u00B7 ${DownloadManager.audioContainerFor(stream)}",
        stream = null,
        audio = stream,
        downloader = downloader,
        repository = repository,
        onResult = onResult
    )
}

private val DOWNLOAD_SHAPE: Shape = RoundedCornerShape(Radius.xl)
