/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import net.newpipe.app.composable.ContentCard
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.ChannelDetails
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.SubscribedChannel
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.screen.search.Message
import net.newpipe.app.service.SubscriptionRepository
import net.newpipe.app.ui.Glass
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import net.newpipe.app.util.formatSubscriberCount
import net.newpipe.app.viewmodel.channel.ChannelViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_language
import newpipe.shared.generated.resources.ic_playlist_play
import newpipe.shared.generated.resources.ic_trending_up
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class ChannelTab(val label: String) {
    HOME("Home"),
    VIDEOS("Videos"),
    PLAYLISTS("Playlists")
}

/**
 * Shows a channel with banner, identity, subscribe action, and tabbed content.
 */
@Composable
fun ChannelScreen(
    serviceId: Int,
    url: String,
    navigator: Navigator = koinInject(),
    viewModel: ChannelViewModel = koinViewModel(),
    subscriptionRepository: SubscriptionRepository = koinInject()
) {
    val details by viewModel.details.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val subscriptions by subscriptionRepository.subscriptions.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(ChannelTab.HOME.ordinal) }

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

            else -> {
                val subscribed = subscriptions.any { it.url == channelDetails.url }
                ChannelContent(
                    details = channelDetails,
                    subscribed = subscribed,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    onToggleSubscribe = {
                        subscriptionRepository.toggle(
                            SubscribedChannel(
                                serviceId = channelDetails.serviceId,
                                url = channelDetails.url,
                                name = channelDetails.name,
                                avatarUrl = channelDetails.avatarUrl
                            )
                        )
                    },
                    onOpenItem = { openItem(it, navigator) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun ChannelContent(
    details: ChannelDetails,
    subscribed: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onToggleSubscribe: () -> Unit,
    onOpenItem: (ContentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val videos = details.items.filter { it.type == ContentType.STREAM }
    val tab = ChannelTab.entries[selectedTab.coerceIn(0, ChannelTab.entries.lastIndex)]

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                ChannelBanner(details.bannerUrl)
                ChannelIdentity(
                    details = details,
                    subscribed = subscribed,
                    videoCount = videos.size,
                    onToggleSubscribe = onToggleSubscribe
                )
                SecondaryTabRow(selectedTabIndex = selectedTab) {
                    ChannelTab.entries.forEachIndexed { index, channelTab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { onSelectTab(index) },
                            text = {
                                Text(
                                    text = channelTab.label,
                                    color = if (selectedTab == index) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        when (tab) {
            ChannelTab.HOME -> {
                if (videos.isEmpty() && details.description.isNullOrBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ChannelEmptyState("No channel content available yet.")
                    }
                } else {
                    val featured = videos.firstOrNull()
                    if (featured != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Featured",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.lg,
                                    vertical = Spacing.sm
                                )
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                                ContentCard(item = featured, onClick = { onOpenItem(featured) })
                            }
                        }
                    }
                    if (!details.description.isNullOrBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(Radius.md)
                            ) {
                                Column(modifier = Modifier.padding(Spacing.md)) {
                                    Text(
                                        text = "About",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = details.description.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Spacing.sm)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ChannelTab.VIDEOS -> {
                if (videos.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ChannelEmptyState("No videos available.")
                    }
                } else {
                    items(videos, key = { "video-${it.url}" }) { item ->
                        Box(modifier = Modifier.padding(horizontal = Spacing.xs)) {
                            ContentCard(item = item, onClick = { onOpenItem(item) })
                        }
                    }
                }
            }

            ChannelTab.PLAYLISTS -> {
                if (details.playlists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ChannelEmptyState("No playlists available.")
                    }
                } else {
                    items(details.playlists, key = { "playlist-${it.url}" }) { item ->
                        PlaylistCard(item = item, onClick = { onOpenItem(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelBanner(bannerUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (bannerUrl == null) 0.35f else 0.12f))
        )
    }
}

@Composable
private fun ChannelIdentity(
    details: ChannelDetails,
    subscribed: Boolean,
    videoCount: Int,
    onToggleSubscribe: () -> Unit
) {
    var showMore by rememberSaveable(details.url) { mutableStateOf(false) }
    Column(modifier = Modifier.padding(Spacing.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            val avatarUrl = details.avatarUrl
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = details.name,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = details.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = details.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val subscriberText = details.subscriberCount?.let {
                    "${formatSubscriberCount(it)} subscribers"
                }
                val meta = listOfNotNull(subscriberText, "$videoCount videos")
                    .joinToString(" \u00B7 ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }

            Button(
                onClick = onToggleSubscribe,
                shape = CircleShape
            ) {
                Text(if (subscribed) "Subscribed" else "Subscribe")
            }
        }

        val description = details.description
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (showMore) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.md)
            )
            Text(
                text = if (showMore) "show less" else "more",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .clickable { showMore = !showMore }
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    item: ContentItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs)
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column {
            Box {
                val thumbnailUrl = item.thumbnailUrl
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
                val count = item.viewCount?.takeIf { it > 0 }
                if (count != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Spacing.sm),
                        color = Glass.color(0.8f),
                        border = Glass.borderStroke(),
                        shape = RoundedCornerShape(Radius.sm)
                    ) {
                        Text(
                            text = "$count songs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                horizontal = Spacing.sm,
                                vertical = Spacing.xs
                            )
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.uploader ?: "Playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun ChannelEmptyState(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_playlist_play),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openItem(item: ContentItem, navigator: Navigator) {
    when (item.type) {
        ContentType.STREAM -> navigator.navigateTo(Destination.Stream(item.serviceId, item.url))
        ContentType.CHANNEL -> navigator.navigateTo(Destination.Channel(item.serviceId, item.url))
        ContentType.PLAYLIST -> navigator.navigateTo(Destination.Playlist(item.serviceId, item.url))
    }
}
