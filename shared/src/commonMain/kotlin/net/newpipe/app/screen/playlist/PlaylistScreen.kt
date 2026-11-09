/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.composable.ContentThumbnail
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.composable.VideoActionsMenuHost
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.PlaylistDetails
import net.newpipe.app.model.SavedPlaylist
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.screen.search.Message
import net.newpipe.app.screen.stream.StreamIntents
import net.newpipe.app.service.PlaylistRepository
import net.newpipe.app.service.QueueRepository
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import net.newpipe.app.viewmodel.playlist.PlaylistViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_close
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows a playlist together with its entries.
 *
 * Local playlists use URLs of the form `local:<id>` and are loaded from [PlaylistRepository].
 * Remote playlists are loaded from the content repository.
 *
 * @param serviceId the id of the service the playlist belongs to
 * @param url the canonical url of the playlist, or `local:<id>` for a local playlist
 */
@Composable
fun PlaylistScreen(
    serviceId: Int,
    url: String,
    navigator: Navigator = koinInject(),
    viewModel: PlaylistViewModel = koinViewModel(),
    playlistRepository: PlaylistRepository = koinInject()
) {
    val localId = url.removePrefix("local:").toLongOrNull()?.takeIf { url.startsWith("local:") }
    if (localId != null) {
        LocalPlaylistScreen(
            playlistId = localId,
            navigator = navigator,
            repository = playlistRepository
        )
        return
    }

    val details by viewModel.details.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(serviceId, url) {
        viewModel.load(serviceId, url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = details?.name ?: "",
                onNavigateUp = { navigator.navigateUp() }
            )
        }
    ) { paddingValues ->
        val playlistDetails = details
        when {
            error != null -> Message(
                text = error!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            playlistDetails == null && loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            playlistDetails == null -> Message(
                text = "The playlist could not be loaded",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            else -> RemotePlaylistBody(
                details = playlistDetails,
                navigator = navigator,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}

@Composable
private fun LocalPlaylistScreen(
    playlistId: Long,
    navigator: Navigator,
    repository: PlaylistRepository,
    queueRepository: QueueRepository = koinInject()
) {
    val playlists by repository.playlists.collectAsState()
    val playlist = playlists.firstOrNull { it.id == playlistId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = playlist?.name ?: "",
                onNavigateUp = { navigator.navigateUp() }
            )
        }
    ) { paddingValues ->
        if (playlist == null) {
            Message(
                text = "The playlist could not be found",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item(key = "header") {
                    LocalPlaylistHeader(
                        playlist = playlist,
                        onPlayAll = { queueAllAndPlay(playlist, queueRepository, navigator) }
                    )
                }
                if (playlist.items.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "This playlist is empty.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.lg)
                        )
                    }
                } else {
                    // Positional key: a playlist may contain the same video twice.
                    itemsIndexed(playlist.items, key = { index, item -> "$index-${item.url}" }) { _, item ->
                        VideoActionsMenuHost(item = item) {
                            PlaylistItemRow(
                                item = item,
                                onOpen = { openItem(item, navigator) },
                                onDelete = { repository.remove(playlistId, item.url) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPlaylistHeader(
    playlist: SavedPlaylist,
    onPlayAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.items.size} videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (playlist.items.isNotEmpty()) {
            TextButton(onClick = onPlayAll) {
                Text("Play all")
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(Radius.lg)
        ) {
            Text(
                text = "${playlist.items.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            )
        }
    }
}

private fun queueAllAndPlay(
    playlist: SavedPlaylist,
    queueRepository: QueueRepository,
    navigator: Navigator
) {
    queueRepository.clear()
    playlist.items.forEach { queueRepository.add(it) }
    playlist.items.firstOrNull()?.let { first ->
        StreamIntents.request(first.url, StreamIntents.Action.PLAY)
        navigator.navigateTo(Destination.Stream(first.serviceId, first.url))
    }
    queueRepository.startWatching()
}

@Composable
private fun RemotePlaylistBody(
    details: PlaylistDetails,
    navigator: Navigator,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item(key = "header") {
            PlaylistHeader(details)
        }
        itemsIndexed(details.items, key = { index, item -> "details-$index-${item.url}" }) { _, item ->
            VideoActionsMenuHost(item = item) {
                PlaylistItemRow(
                    item = item,
                    onOpen = { openItem(item, navigator) },
                    onDelete = null
                )
            }
        }
    }
}

/**
 * The thumbnail and metadata shown above the entries of a playlist.
 */
@Composable
private fun PlaylistHeader(details: PlaylistDetails) {
    Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
        val thumbnailUrl = details.thumbnailUrl
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = details.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(Radius.lg)),
                contentScale = ContentScale.Crop
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = details.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(Radius.lg)
            ) {
                Text(
                    text = "${details.items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                )
            }
        }

        val uploader = details.uploader
        if (!uploader.isNullOrBlank()) {
            Text(
                text = uploader,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }

        val itemCount = details.itemCount
        if (itemCount != null) {
            Text(
                text = "$itemCount videos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }

        val description = details.description
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }
    }
}

@Composable
private fun PlaylistItemRow(
    item: ContentItem,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(160.dp)) {
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.uploader ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = "Remove from playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Navigates to the screen that matches the type of the item.
 */
private fun openItem(item: ContentItem, navigator: Navigator) {
    when (item.type) {
        ContentType.STREAM -> navigator.navigateTo(Destination.Stream(item.serviceId, item.url))
        ContentType.CHANNEL -> navigator.navigateTo(Destination.Channel(item.serviceId, item.url))
        ContentType.PLAYLIST -> navigator.navigateTo(Destination.Playlist(item.serviceId, item.url))
    }
}

