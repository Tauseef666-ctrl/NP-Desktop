/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.playlist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.PlaylistDetails
import net.newpipe.app.model.ResolvedContent
import net.newpipe.app.model.SavedPlaylist
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.screen.stream.StreamIntents
import net.newpipe.app.service.ContentRepository
import net.newpipe.app.service.ContentException
import net.newpipe.app.service.PlaylistRepository
import net.newpipe.app.service.QueueRepository
import net.newpipe.app.service.SearchRequest
import net.newpipe.app.service.ServiceRegistry
import net.newpipe.app.ui.Glass
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_arrow_back
import newpipe.shared.generated.resources.ic_close
import newpipe.shared.generated.resources.ic_edit
import newpipe.shared.generated.resources.ic_play_arrow
import newpipe.shared.generated.resources.ic_playlist_add
import newpipe.shared.generated.resources.ic_playlist_play
import newpipe.shared.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * The playlists hub: create, rename and delete playlists, play them straight away, and browse
 * the ones videos have actually been saved to.
 */
@Composable
fun PlaylistsScreen(
    navigator: Navigator = koinInject(),
    repository: PlaylistRepository = koinInject(),
    queueRepository: QueueRepository = koinInject(),
    contentRepository: ContentRepository = koinInject(),
    serviceRegistry: ServiceRegistry = koinInject()
) {
    val playlists by repository.playlists.collectAsState()
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<SavedPlaylist?>(null) }
    var pendingRename by remember { mutableStateOf<SavedPlaylist?>(null) }
    var pendingAddVideos by remember { mutableStateOf<SavedPlaylist?>(null) }
    var showFindOnline by remember { mutableStateOf(false) }
    var openPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }

    val withContent = playlists.filter { it.items.isNotEmpty() }
    val opened = openPlaylistId?.let { id -> withContent.firstOrNull { it.id == id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        val openedPlaylist = opened
        if (openedPlaylist != null) {
            item(key = "detail-back") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    IconButton(onClick = { openPlaylistId = null }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_back),
                            contentDescription = "Back to playlists"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = openedPlaylist.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${openedPlaylist.items.size} videos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            queueAllAndPlay(openedPlaylist, queueRepository, navigator)
                        },
                        enabled = openedPlaylist.items.isNotEmpty()
                    ) {
                        Text("Play all")
                    }
                }
            }
            items(openedPlaylist.items, key = { it.url }) { item ->
                SavedPlaylistItemRow(
                    item = item,
                    onPlay = {
                        navigator.navigateTo(Destination.Stream(item.serviceId, item.url))
                    }
                )
            }
        } else {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    // Search a playlist on the service itself and import it as a local playlist.
                    IconButton(onClick = { showFindOnline = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_search),
                            contentDescription = "Find a playlist online",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            item(key = "create") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Glass.color(0.88f),
                    border = Glass.borderStroke(),
                    shape = RoundedCornerShape(Radius.lg)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        TextField(
                            value = newName,
                            onValueChange = { newName = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "Playlist name",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        TextButton(
                            onClick = {
                                val created = repository.create(newName)
                                if (created > 0) newName = ""
                            },
                            enabled = newName.trim().isNotEmpty()
                        ) {
                            Text("Create")
                        }
                    }
                }
            }

            if (playlists.isNotEmpty()) {
                item(key = "filter") {
                    TextField(
                        value = filter,
                        onValueChange = { filter = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "Filter playlists",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(Radius.md)
                    )
                }
            }

            val matches = playlists.filter {
                filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true)
            }
            val savedMatches = withContent.filter {
                filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true)
            }

            item(key = "your-title") {
                Text(
                    text = "Your playlists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
            if (matches.isEmpty() && playlists.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No playlists yet. Create one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.lg)
                    )
                }
            } else if (matches.isEmpty()) {
                item(key = "filter-empty") {
                    Text(
                        text = "No playlist matches \"${filter.trim()}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.lg)
                    )
                }
            } else {
                items(matches, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onOpen = {
                            navigator.navigateTo(Destination.Playlist(0, "local:${playlist.id}"))
                        },
                        onAdd = { pendingAddVideos = playlist },
                        onPlay = { queueAllAndPlay(playlist, queueRepository, navigator) },
                        onRename = { pendingRename = playlist },
                        onDelete = { pendingDelete = playlist }
                    )
                }
            }

            item(key = "saved-title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Saved playlists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (savedMatches.isNotEmpty()) {
                        CountBadge(text = "${savedMatches.size} with videos")
                    }
                }
            }
            if (savedMatches.isEmpty()) {
                item(key = "saved-empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = "Nothing saved yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Right-click a video and choose \"Save to playlist\". Playlists with saved videos show up here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(savedMatches, key = { it.id }) { playlist ->
                    SavedPlaylistRow(
                        playlist = playlist,
                        onOpen = { openPlaylistId = playlist.id },
                        onPlayAll = { queueAllAndPlay(playlist, queueRepository, navigator) }
                    )
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete playlist") },
            text = { Text("Delete playlist \"${toDelete.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.delete(toDelete.id)
                        pendingDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val toRename = pendingRename
    if (toRename != null) {
        var renameValue by remember(toRename.id) { mutableStateOf(toRename.name) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename playlist") },
            text = {
                TextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    placeholder = { Text("Playlist name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameValue.isNotBlank()) {
                            repository.rename(toRename.id, renameValue.trim())
                        }
                        pendingRename = null
                    },
                    enabled = renameValue.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFindOnline) {
        FindOnlineDialog(
            repository = repository,
            contentRepository = contentRepository,
            serviceRegistry = serviceRegistry,
            onDismiss = { showFindOnline = false }
        )
    }

    val toAddVideos = pendingAddVideos
    if (toAddVideos != null) {
        AddVideosDialog(
            playlistId = toAddVideos.id,
            initialItems = toAddVideos.items,
            repository = repository,
            contentRepository = contentRepository,
            serviceRegistry = serviceRegistry,
            onDismiss = { pendingAddVideos = null }
        )
    }
}

/** Small rounded badge for section counts, in the glass design language. */
@Composable
private fun CountBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

/**
 * A managed playlist row: name and count plus open, add-videos, play-through, rename and delete
 * actions.
 */
@Composable
private fun PlaylistRow(
    playlist: SavedPlaylist,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.items.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            IconButton(onClick = onOpen) {
                Icon(
                    painter = painterResource(Res.drawable.ic_playlist_play),
                    contentDescription = "Open playlist",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onAdd) {
                Icon(
                    painter = painterResource(Res.drawable.ic_playlist_add),
                    contentDescription = "Add videos to playlist",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onPlay, enabled = playlist.items.isNotEmpty()) {
                Icon(
                    painter = painterResource(Res.drawable.ic_play_arrow),
                    contentDescription = "Play and queue playlist",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    painter = painterResource(Res.drawable.ic_edit),
                    contentDescription = "Rename playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = "Delete playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * A saved playlist row: its first video as thumbnail, the name and the count, with a
 * play-through button.
 */
@Composable
private fun SavedPlaylistRow(
    playlist: SavedPlaylist,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                playlist.items.firstOrNull()?.thumbnailUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.items.size} saved videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onPlayAll) {
                Icon(
                    painter = painterResource(Res.drawable.ic_play_arrow),
                    contentDescription = "Play all",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * One saved video row inside a playlist: click plays it.
 */
@Composable
private fun SavedPlaylistItemRow(
    item: ContentItem,
    onPlay: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                item.thumbnailUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
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
                    text = item.uploader ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Search a playlist on the streaming service (keyword or pasted playlist URL) and import it as a
 * local playlist, videos included.
 */
@Composable
private fun FindOnlineDialog(
    repository: PlaylistRepository,
    contentRepository: ContentRepository,
    serviceRegistry: ServiceRegistry,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var importingUrl by remember { mutableStateOf<String?>(null) }
    var urlResult by remember { mutableStateOf<PlaylistDetails?>(null) }
    var results by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        val term = query.trim()
        if (term.isEmpty() || searching) return
        scope.launch {
            searching = true
            error = null
            results = emptyList()
            urlResult = null
            try {
                if (term.contains("://") || term.startsWith("www.")) {
                    val normalized = if (term.startsWith("www.")) "https://$term" else term
                    when (val resolved = contentRepository.resolveUrl(normalized)) {
                        is ResolvedContent.Playlist -> urlResult = resolved.details
                        is ResolvedContent.Stream ->
                            error = "That link points at a video, not a playlist."
                        is ResolvedContent.Channel ->
                            error = "That link points at a channel, not a playlist."
                    }
                } else {
                    val serviceId = serviceRegistry.services().firstOrNull()?.id ?: 0
                    val found = contentRepository.search(
                        SearchRequest(query = term, serviceId = serviceId, filter = "playlists")
                    )
                    results = found.filter { it.type == ContentType.PLAYLIST }
                    if (results.isEmpty()) error = "No playlists found for \"$term\"."
                }
            } catch (exception: ContentException) {
                error = exception.message ?: "The search did not complete."
            } catch (exception: Exception) {
                error = "The search did not complete."
            } finally {
                searching = false
            }
        }
    }

    fun importDetails(details: PlaylistDetails) {
        if (importingUrl != null) return
        scope.launch {
            importingUrl = details.url
            try {
                val id = repository.create(details.name)
                details.items.forEach { repository.toggle(id, it) }
                onDismiss()
            } finally {
                importingUrl = null
            }
        }
    }

    fun importFromItem(item: ContentItem) {
        if (importingUrl != null) return
        scope.launch {
            importingUrl = item.url
            try {
                importDetails(contentRepository.playlist(item.serviceId, item.url))
            } finally {
                importingUrl = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find a playlist online") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text("Search playlists or paste a playlist URL")
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "The playlist is copied into your local playlists with its videos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { runSearch() }, enabled = !searching) {
                        Text("Search")
                    }
                }
                if (searching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    urlResult?.let { details ->
                        item(key = "url-result") {
                            OnlinePlaylistRow(
                                title = details.name,
                                subtitle = details.uploader
                                    ?: "${details.items.size} videos",
                                thumbnailUrl = details.thumbnailUrl,
                                busy = importingUrl == details.url,
                                onClick = { importDetails(details) }
                            )
                        }
                    }
                    items(results, key = { it.url + it.serviceId }) { item ->
                        OnlinePlaylistRow(
                            title = item.title,
                            subtitle = item.uploader,
                            thumbnailUrl = item.thumbnailUrl,
                            busy = importingUrl == item.url,
                            onClick = { importFromItem(item) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * One import candidate row: thumbnail, name and creator, click imports the whole playlist.
 */
@Composable
private fun OnlinePlaylistRow(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    busy: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, enabled = !busy),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                thumbnailUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "Import",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Search for videos on the service and add or remove them from a local playlist.
 */
@Composable
private fun AddVideosDialog(
    playlistId: Long,
    initialItems: List<ContentItem>,
    repository: PlaylistRepository,
    contentRepository: ContentRepository,
    serviceRegistry: ServiceRegistry,
    onDismiss: () -> Unit
) {
    val playlists by repository.playlists.collectAsState()
    val current = playlists.firstOrNull { it.id == playlistId } ?: return
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        val term = query.trim()
        if (term.isEmpty() || searching) return
        scope.launch {
            searching = true
            error = null
            try {
                val serviceId = serviceRegistry.services().firstOrNull()?.id ?: 0
                results = contentRepository.search(
                    SearchRequest(query = term, serviceId = serviceId, filter = "videos")
                ).filter { it.type == ContentType.STREAM }
                if (results.isEmpty()) error = "No videos found for \"$term\"."
            } catch (exception: ContentException) {
                error = exception.message ?: "The search did not complete."
            } catch (exception: Exception) {
                error = "The search did not complete."
            } finally {
                searching = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add videos to \"${current.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text("Search videos to add")
                    },
                    trailingIcon = {
                        IconButton(onClick = { runSearch() }, enabled = !searching) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_search),
                                contentDescription = "Search videos",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (results.isEmpty() && initialItems.isEmpty() && error == null) {
                    Text(
                        text = "Search a video and tap it to add; tap again to remove.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (searching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(results, key = { it.url }) { item ->
                        val added = current.items.any { it.url == item.url }
                        VideoPickRow(
                            item = item,
                            added = added,
                            onToggle = { repository.toggle(playlistId, item) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

/**
 * One video candidate row for the add-videos dialog: click toggles membership in the playlist.
 */
@Composable
private fun VideoPickRow(
    item: ContentItem,
    added: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        color = if (added) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                item.thumbnailUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.uploader ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (added) "Added" else "Add",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (added) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
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
