/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import net.newpipe.app.model.ChannelDetails
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.screen.search.Message
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import net.newpipe.app.util.formatSubscriberCount
import net.newpipe.app.viewmodel.channel.ChannelViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows a channel together with its most recent uploads.
 *
 * The uploads are shown in a responsive grid, so the screen uses the extra width of a desktop
 * window instead of stretching a single column of list rows.
 *
 * @param serviceId the id of the service the channel belongs to
 * @param url the canonical url of the channel
 */
@Composable
fun ChannelScreen(
    serviceId: Int,
    url: String,
    navigator: Navigator = koinInject(),
    viewModel: ChannelViewModel = koinViewModel()
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
        val channelDetails = details
        when {
            error != null -> Message(
                text = error!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            channelDetails == null && loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            channelDetails == null -> Message(
                text = "The channel could not be loaded",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            else -> ContentGrid(
                items = channelDetails.items,
                onOpenItem = { item -> openItem(item, navigator) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                header = { ChannelHeader(channelDetails) }
            )
        }
    }
}

/**
 * The banner, avatar and description shown above the uploads of a channel.
 */
@Composable
private fun ChannelHeader(details: ChannelDetails) {
    Column {
        val bannerUrl = details.bannerUrl
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg)),
                contentScale = ContentScale.Crop
            )
        }

        Row(
            modifier = Modifier.padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarUrl = details.avatarUrl
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = details.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(
                    text = details.name,
                    style = MaterialTheme.typography.titleLarge
                )

                val subscriberCount = details.subscriberCount
                if (subscriberCount != null) {
                    Text(
                        text = "${formatSubscriberCount(subscriberCount)} subscribers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }
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
