/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.composable.ContentGrid
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.PlaylistDetails
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.screen.search.Message
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import net.newpipe.app.viewmodel.playlist.PlaylistViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows a playlist together with its entries in a responsive grid.
 *
 * @param serviceId the id of the service the playlist belongs to
 * @param url the canonical url of the playlist
 */
@Composable
fun PlaylistScreen(
    serviceId: Int,
    url: String,
    navigator: Navigator = koinInject(),
    viewModel: PlaylistViewModel = koinViewModel()
) {
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

            else -> ContentGrid(
                items = playlistDetails.items,
                onOpenItem = { item -> openItem(item, navigator) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                header = { PlaylistHeader(playlistDetails) }
            )
        }
    }
}

/**
 * The thumbnail and metadata shown above the entries of a playlist.
 */
@Composable
private fun PlaylistHeader(details: PlaylistDetails) {
    Column {
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

        Text(
            text = details.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.md)
        )

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
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.sm)
            )
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
