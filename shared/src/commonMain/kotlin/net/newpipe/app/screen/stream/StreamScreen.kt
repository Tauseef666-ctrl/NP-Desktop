/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.composable.ContentListItem
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.MediaStream
import net.newpipe.app.model.StreamDetails
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.screen.search.Message
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PlaybackRequest
import net.newpipe.app.ui.Breakpoints
import net.newpipe.app.ui.Layout
import net.newpipe.app.util.formatDuration
import net.newpipe.app.util.formatViewCount
import net.newpipe.app.viewmodel.stream.StreamViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows the details of a single stream and lets the user start the playback.
 *
 * @param serviceId the id of the service the stream belongs to
 * @param url the canonical url of the stream
 */
@Composable
fun StreamScreen(
    serviceId: Int,
    url: String,
    navigator: Navigator = koinInject(),
    viewModel: StreamViewModel = koinViewModel(),
    playbackController: PlaybackController = koinInject()
) {
    val details by viewModel.details.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val selectedAudio by viewModel.selectedAudio.collectAsState()

    LaunchedEffect(serviceId, url) {
        viewModel.load(serviceId, url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = details?.title ?: "",
                onNavigateUp = { navigator.navigateUp() }
            )
        }
    ) { paddingValues ->
        val streamDetails = details
        when {
            error != null -> Message(
                text = error!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            streamDetails == null && loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            streamDetails == null -> Message(
                text = "The stream could not be loaded",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            else -> StreamContent(
                details = streamDetails,
                selectedVideo = selectedVideo,
                selectedAudio = selectedAudio,
                modifier = Modifier.padding(paddingValues),
                onSelectVideo = viewModel::selectVideo,
                onSelectAudio = viewModel::selectAudio,
                onPlay = {
                    val video = selectedVideo
                    if (video != null) {
                        playbackController.play(
                            PlaybackRequest(
                                title = streamDetails.title,
                                videoUrl = video.url,
                                audioUrl = selectedAudio?.url,
                                thumbnailUrl = streamDetails.thumbnailUrl
                            )
                        )
                    }
                },
                onOpenItem = { item ->
                    when (item.type) {
                        ContentType.STREAM ->
                            navigator.navigateTo(Destination.Stream(item.serviceId, item.url))

                        ContentType.CHANNEL ->
                            navigator.navigateTo(Destination.Channel(item.serviceId, item.url))

                        ContentType.PLAYLIST ->
                            navigator.navigateTo(Destination.Playlist(item.serviceId, item.url))
                    }
                }
            )
        }
    }
}

/**
 * The content of the stream screen, once the details have been loaded.
 *
 * On wide screens the description sits in a column on the left while the related streams scroll in
 * a second column to its right. On narrow screens everything is stacked in a single scrolling
 * column, so no information is lost when the window is small.
 */
@Composable
private fun StreamContent(
    details: StreamDetails,
    selectedVideo: MediaStream?,
    selectedAudio: MediaStream?,
    modifier: Modifier = Modifier,
    onSelectVideo: (MediaStream) -> Unit,
    onSelectAudio: (MediaStream) -> Unit,
    onPlay: () -> Unit,
    onOpenItem: (ContentItem) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth >= Breakpoints.compact && details.relatedItems.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .widthIn(max = Layout.contentMaxWidth)
                        .verticalScroll(rememberScrollState())
                ) {
                    StreamHeader(
                        details = details,
                        selectedVideo = selectedVideo,
                        selectedAudio = selectedAudio,
                        onSelectVideo = onSelectVideo,
                        onSelectAudio = onSelectAudio,
                        onPlay = onPlay
                    )
                }

                VerticalDivider()

                LazyColumn(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                ) {
                    item {
                        Text(
                            text = "Related streams",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    items(details.relatedItems, key = { it.url }) { item ->
                        ContentListItem(
                            item = item,
                            onClick = { onOpenItem(item) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    StreamHeader(
                        details = details,
                        selectedVideo = selectedVideo,
                        selectedAudio = selectedAudio,
                        onSelectVideo = onSelectVideo,
                        onSelectAudio = onSelectAudio,
                        onPlay = onPlay
                    )
                }

                if (details.relatedItems.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                        Text(
                            text = "Related streams",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    items(details.relatedItems, key = { it.url }) { item ->
                        ContentListItem(
                            item = item,
                            onClick = { onOpenItem(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The thumbnail, metadata, stream selectors and play button shared by both layouts.
 */
@Composable
private fun StreamHeader(
    details: StreamDetails,
    selectedVideo: MediaStream?,
    selectedAudio: MediaStream?,
    onSelectVideo: (MediaStream) -> Unit,
    onSelectAudio: (MediaStream) -> Unit,
    onPlay: () -> Unit
) {
    Column {
        val thumbnailUrl = details.thumbnailUrl
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = details.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = details.title,
                style = MaterialTheme.typography.titleLarge
            )

            val metadata = buildMetadata(details)
            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val description = details.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StreamSelector(
                    label = "Video",
                    streams = details.videoStreams,
                    selected = selectedVideo,
                    onSelect = onSelectVideo
                )

                StreamSelector(
                    label = "Audio",
                    streams = details.audioStreams,
                    selected = selectedAudio,
                    onSelect = onSelectAudio
                )
            }

            Button(
                onClick = onPlay,
                enabled = selectedVideo != null,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Play")
            }
        }
    }
}

/**
 * A dropdown that lets the user pick one of the available streams.
 */
@Composable
private fun StreamSelector(
    label: String,
    streams: List<MediaStream>,
    selected: MediaStream?,
    onSelect: (MediaStream) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = streams.isNotEmpty()
        ) {
            Text(text = "$label: ${selected?.quality ?: "none"}")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            streams.forEach { stream ->
                DropdownMenuItem(
                    text = { Text(stream.quality) },
                    onClick = {
                        expanded = false
                        onSelect(stream)
                    }
                )
            }
        }
    }
}

/**
 * Builds the line that shows the uploader, the view count and the upload date.
 */
private fun buildMetadata(details: StreamDetails): String {
    val parts = mutableListOf<String>()

    details.uploader.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    details.viewCount?.let { parts.add("${formatViewCount(it)} views") }
    details.uploadDate?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    details.durationSeconds?.let { parts.add(formatDuration(it)) }
    if (details.liveNow) parts.add("LIVE")

    return parts.joinToString(" · ")
}
