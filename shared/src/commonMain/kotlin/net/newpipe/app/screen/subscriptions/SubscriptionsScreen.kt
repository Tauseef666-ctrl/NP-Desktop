/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.newpipe.app.composable.ContentCard
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.ResolvedContent
import net.newpipe.app.model.SubscribedChannel
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.screen.search.Message
import net.newpipe.app.service.ContentRepository
import net.newpipe.app.service.SearchRequest
import net.newpipe.app.service.SubscriptionRepository
import net.newpipe.app.ui.Spacing
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_close
import newpipe.shared.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * The subscriptions screen: the latest uploads of every subscribed channel, plus the list of the
 * channels themselves.
 *
 * The feed is built from the channels the user subscribed to. Channels can be removed from the
 * list, and new ones can be found with the channel search in the toolbar.
 */
@Composable
fun SubscriptionsScreen(
    navigator: Navigator = koinInject(),
    repository: ContentRepository = koinInject(),
    subscriptionRepository: SubscriptionRepository = koinInject()
) {
    val channels by subscriptionRepository.subscriptions.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var feed by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(channels) {
        if (channels.isEmpty()) {
            feed = emptyList()
            loading = false
            error = null
            return@LaunchedEffect
        }

        loading = true
        error = null
        Logger.i(messageString = "Loading uploads of ${channels.size} subscribed channels")
        val uploads = mutableListOf<ContentItem>()
        for (channel in channels.take(MAX_FEED_CHANNELS)) {
            try {
                uploads += repository.channel(channel.serviceId, channel.url).items
                    .filter { it.type == ContentType.STREAM }
                    .take(MAX_UPLOADS_PER_CHANNEL)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // A single broken channel must not hide the uploads of the others.
            }
        }
        feed = uploads.distinctBy { it.url }
        loading = false
    }

    if (showSearch) {
        ChannelSearchDialog(
            repository = repository,
            subscriptions = subscriptionRepository,
            onDismiss = { showSearch = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Subscriptions",
                onNavigateUp = navigator::navigateUp,
                actions = {
                    IconButton(onClick = { showSearch = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_search),
                            contentDescription = "Find a channel"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Feed") }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Channels (${channels.size})") }
                )
            }

            when {
                selectedTab == 1 -> if (channels.isEmpty()) {
                    Message(
                        text = "No subscriptions yet. Use the search button to find a channel.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        listItems(channels, key = { it.url }) { channel ->
                            SubscriptionRow(
                                channel = channel,
                                onClick = {
                                    navigator.navigateTo(
                                        Destination.Channel(channel.serviceId, channel.url)
                                    )
                                },
                                onRemove = { subscriptionRepository.remove(channel.url) }
                            )
                        }
                    }
                }

                error != null -> Message(
                    text = error!!,
                    modifier = Modifier.fillMaxSize()
                )

                channels.isEmpty() -> Message(
                    text = "Subscribe to channels to see their latest videos here.",
                    modifier = Modifier.fillMaxSize()
                )

                feed.isEmpty() && loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                feed.isEmpty() -> Message(
                    text = "No uploads were found for your subscriptions.",
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(
                                text = "Latest from your channels",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (loading) {
                                    "Updating\u2026"
                                } else {
                                    "${channels.size} channels"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(feed, key = { it.url }) { item ->
                        ContentCard(
                            item = item,
                            onClick = {
                                navigator.navigateTo(
                                    Destination.Stream(item.serviceId, item.url)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single row of the channel list, with a button to remove the subscription.
 */
@Composable
private fun SubscriptionRow(
    channel: SubscribedChannel,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (channel.avatarUrl != null) {
                AsyncImage(
                    model = channel.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.md)
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = "Remove subscription"
                )
            }
        }
    }
}

/**
 * A dialog that searches for channels (or resolves a channel URL) and subscribes to the selected
 * one.
 */
@Composable
private fun ChannelSearchDialog(
    repository: ContentRepository,
    subscriptions: SubscriptionRepository,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun search() {
        val term = query.trim()
        if (term.isEmpty() || loading) return
        loading = true
        error = null
        searched = true
        scope.launch {
            try {
                if (term.contains("://") || term.contains("youtube.com/") ||
                    term.contains("youtu.be/")
                ) {
                    results = when (val resolved = repository.resolveUrl(term)) {
                        is ResolvedContent.Channel -> listOf(
                            ContentItem(
                                serviceId = resolved.details.serviceId,
                                url = resolved.details.url,
                                title = resolved.details.name,
                                type = ContentType.CHANNEL,
                                thumbnailUrl = resolved.details.avatarUrl
                            )
                        )

                        else -> emptyList()
                    }
                } else {
                    results = repository.search(
                        SearchRequest(
                            query = term,
                            serviceId = 0,
                            filter = CHANNEL_FILTER
                        )
                    ).filter { it.type == ContentType.CHANNEL }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                error = exception.message ?: "The channel search failed"
                results = emptyList()
            } finally {
                loading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .heightIn(max = 620.dp)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "Find a channel",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text("Channel name or URL") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = ::search,
                        enabled = query.isNotBlank() && !loading
                    ) {
                        Text("Search")
                    }
                }

                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (!loading && searched && results.isEmpty() && error == null) {
                    Text(
                        text = "No channels found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    listItems(results, key = { it.url }) { item ->
                        val subscribed = subscriptions.isSubscribed(item.url)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.thumbnailUrl != null) {
                                AsyncImage(
                                    model = item.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                            }

                            Text(
                                text = item.title,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = Spacing.sm),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            TextButton(
                                onClick = {
                                    subscriptions.add(
                                        SubscribedChannel(
                                            serviceId = item.serviceId,
                                            url = item.url,
                                            name = item.title,
                                            avatarUrl = item.thumbnailUrl
                                        )
                                    )
                                },
                                enabled = !subscribed
                            ) {
                                Text(if (subscribed) "Subscribed" else "Subscribe")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

/** How many channels the subscription feed loads at once. */
private const val MAX_FEED_CHANNELS = 8

/** How many uploads of each channel the subscription feed contains. */
private const val MAX_UPLOADS_PER_CHANNEL = 12

/** The search filter the services use for channels. */
private const val CHANNEL_FILTER = "channels"
