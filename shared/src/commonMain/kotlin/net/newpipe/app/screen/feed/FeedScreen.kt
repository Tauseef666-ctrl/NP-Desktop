/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import net.newpipe.app.composable.ContentCard
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.screen.search.Message
import net.newpipe.app.service.ContentRepository
import net.newpipe.app.ui.Spacing
import org.koin.compose.koinInject

/**
 * A single content list ("kiosk") of the service, for example Trending or Gaming.
 *
 * The grid loads the next page as soon as the user scrolls close to the end, so the list keeps
 * growing without a "load more" button.
 *
 * @param kioskId the identifier of the kiosk in the extraction engine
 * @param title the label shown in the toolbar
 */
@Composable
fun FeedScreen(
    kioskId: String,
    title: String,
    navigator: Navigator = koinInject(),
    repository: ContentRepository = koinInject()
) {
    var items by remember(kioskId) { mutableStateOf<List<ContentItem>>(emptyList()) }
    var loading by remember(kioskId) { mutableStateOf(true) }
    var loadingMore by remember(kioskId) { mutableStateOf(false) }
    var hasMore by remember(kioskId) { mutableStateOf(true) }
    var nextPage by remember(kioskId) { mutableIntStateOf(1) }
    var error by remember(kioskId) { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    val gridState = rememberLazyGridState()

    LaunchedEffect(kioskId, retryKey) {
        loading = true
        error = null
        try {
            Logger.i(messageString = "Loading feed kiosk '$kioskId'")
            val first = repository.kioskPage(SERVICE_ID, kioskId, 0)
            items = first
            hasMore = first.isNotEmpty()
            nextPage = 1
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            error = exception.message ?: "The content could not be loaded"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(kioskId) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - PAGE_PREFETCH_DISTANCE
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (!nearEnd || loading || loadingMore || !hasMore) return@collect
                loadingMore = true
                try {
                    Logger.i(messageString = "Loading feed kiosk '$kioskId' page $nextPage")
                    val more = repository.kioskPage(SERVICE_ID, kioskId, nextPage)
                    if (more.isEmpty()) {
                        hasMore = false
                    } else {
                        val known = items.map { it.url }.toSet()
                        items = items + more.filterNot { it.url in known }
                        nextPage += 1
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    hasMore = false
                } finally {
                    loadingMore = false
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                onNavigateUp = navigator::navigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            // The body of the section follows below.

            when {
                error != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { retryKey = retryKey + 1 }
                    ) {
                        Text("Retry")
                    }
                }
            items.isEmpty() && loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            items.isEmpty() -> Message(
                text = "Nothing to show here right now",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                items(items, key = { it.url }) { item ->
                    ContentCard(
                        item = item,
                        onClick = { navigateToItem(item, navigator) }
                    )
                }

                if (loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * Navigates to the screen that matches the type of the item.
 */
private fun navigateToItem(item: ContentItem, navigator: Navigator) {
    when (item.type) {
        ContentType.STREAM -> navigator.navigateTo(Destination.Stream(item.serviceId, item.url))
        ContentType.CHANNEL -> navigator.navigateTo(Destination.Channel(item.serviceId, item.url))
        ContentType.PLAYLIST -> navigator.navigateTo(Destination.Playlist(item.serviceId, item.url))
    }
}

/** YouTube, the default service of NewPipe. */
private const val SERVICE_ID = 0

/** How many entries before the end the next page starts loading. */
private const val PAGE_PREFETCH_DISTANCE = 6
