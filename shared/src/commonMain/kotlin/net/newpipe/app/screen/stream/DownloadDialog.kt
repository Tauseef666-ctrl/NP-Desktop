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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import net.newpipe.app.service.DownloadLocation
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
    // One video quality list: muxed streams already contain the audio, video only streams are
    // merged with the best audio file after the download, so every option is a single file
    // with sound. A muxed and an adaptive stream of the same quality appear as two entries,
    // because they differ in what they contain.
    val videoStreams = details.videoStreams
        .distinctBy { it.quality to it.isVideoOnly }
        .sortedByDescending { it.height ?: it.bitrate }

    val audioStreams = details.audioStreams
        .distinctBy { it.audioTrackName ?: it.quality }
        .sortedByDescending { it.bitrate }

    val supportsVideo = videoStreams.isNotEmpty()
    val supportsAudio = audioStreams.isNotEmpty()

    var audioOnly by remember { mutableStateOf(!supportsVideo && supportsAudio) }

    var videoSelection by remember {
        mutableStateOf(videoStreams.firstOrNull())
    }
    var audioSelection by remember {
        mutableStateOf(audioStreams.firstOrNull())
    }
    // The container of a merged download: `null` lets the codecs choose, otherwise the user
    // picked mp4 or mkv explicitly.
    var mergeContainer by remember { mutableStateOf<String?>(null) }

    val dialogScope = rememberCoroutineScope()
    var location by remember { mutableStateOf(DownloadLocation.resolve()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 420.dp, max = 520.dp),
            shape = DOWNLOAD_SHAPE,
            color = net.newpipe.app.ui.Glass.color(0.99f),
            // The glass color is translucent, so contentColorFor returns unspecified and the
            // texts fall back to raw black: pin the content color explicitly
            contentColor = MaterialTheme.colorScheme.onSurface,
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
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
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when {
                        audioOnly || !supportsVideo -> {
                            if (audioStreams.isEmpty()) {
                                InfoLine("No audio stream is available for this video")
                            } else {
                                QualityList(
                                    title = "Audio quality",
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

                        else -> {
                            QualityList(
                                title = "Video quality",
                                entries = videoStreams.map { stream ->
                                    QualityEntry(
                                        key = stream.url,
                                        title = videoLabel(stream),
                                        subtitle = videoSubtitle(stream, supportsAudio)
                                    )
                                },
                                selectedKey = videoSelection?.url,
                                onSelect = { selection ->
                                    videoSelection =
                                        videoStreams.firstOrNull { it.url == selection }
                                }
                            )

                            val video = videoSelection
                            if (video != null && video.isVideoOnly && supportsAudio) {
                                ContainerChoice(
                                    selected = mergeContainer,
                                    onSelect = { mergeContainer = it }
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save to",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (DownloadLocation.pickerSupported) {
                        TextButton(onClick = {
                            dialogScope.launch {
                                if (DownloadLocation.choose()) {
                                    location = DownloadLocation.resolve()
                                }
                            }
                        }) {
                            Text(
                                text = "Change\u2026",
                                color = MaterialTheme.colorScheme.onSurface
                            )
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
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val start: (() -> Unit)? = when {
                        audioOnly || !supportsVideo -> audioSelection?.let { stream ->
                            { downloadAudioOnly(stream, item, downloader, repository, onResult) }
                        }

                        else -> videoSelection?.let { video ->
                            {
                                // The best audio track is merged into the video; a muxed
                                // stream already contains its own audio
                                val audio = if (video.isVideoOnly && supportsAudio) {
                                    audioStreams.maxByOrNull { it.bitrate }
                                } else {
                                    null
                                }
                                downloadVideo(
                                    video = video,
                                    audio = audio,
                                    container = if (audio != null) mergeContainer else null,
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
                    }                }
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
    title: String,
    entries: List<QualityEntry>,
    selectedKey: String?,
    onSelect: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                        color = MaterialTheme.colorScheme.onSurface,
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

/**
 * The container choice for a download that merges a video only stream with a separate audio
 * file: automatic keeps the safe codec-derived container, mp4 and mkv force one explicitly.
 */
@Composable
private fun ContainerChoice(
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Column(
        modifier = Modifier.padding(top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = "Container of the merged file",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf(
                null to "Automatic",
                "mp4" to "MP4",
                "mkv" to "MKV"
            ).forEach { (value, label) ->
                TypeChip(
                    label = label,
                    selected = selected == value,
                    onClick = { onSelect(value) }
                )
            }
        }

        Text(
            text = "MP4 keeps the original quality only for H.264 video with AAC audio; " +
                "MKV works with every codec combination.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun videoLabel(stream: MediaStream): String = stream.quality.ifEmpty { "Video" }

private fun videoSubtitle(stream: MediaStream, audioAvailable: Boolean): String = buildString {
    append(containerLabel(DownloadManager.videoContainerFor(stream)))
    stream.fps?.takeIf { it > 30 }?.let { append(" \u00B7 ${it}fps") }
    append(
        when {
            !stream.isVideoOnly -> " \u00B7 with audio"
            audioAvailable -> " \u00B7 video only, merged with best audio"
            else -> " \u00B7 without audio"
        }
    )
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

private fun downloadVideo(
    video: MediaStream,
    audio: MediaStream?,
    container: String?,
    item: ContentItem,
    downloader: MediaDownloader,
    repository: DownloadRepository,
    onResult: (Result<String>) -> Unit
) {
    val containerExtension = container
        ?: audio?.let { DownloadManager.mergedContainerFor(video, it) }
        ?: DownloadManager.videoContainerFor(video)
    val label = when {
        audio != null -> "${video.quality} \u00B7 $containerExtension"
        else -> "${video.quality} \u00B7 ${DownloadManager.videoContainerFor(video)}"
    }
    DownloadManager.start(
        item = item,
        title = item.title,
        label = label,
        stream = video,
        audio = audio,
        downloader = downloader,
        repository = repository,
        container = container,
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
