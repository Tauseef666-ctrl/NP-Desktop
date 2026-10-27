/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.newpipe.app.composable.ContentCard
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.screen.search.Message
import net.newpipe.app.ui.Spacing
import net.newpipe.app.viewmodel.home.HomeViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The home screen: a personalised feed followed by the predefined content lists ("kiosks") of the
 * selected service.
 *
 * The feed mixes the most recent uploads of the channels the user watched last with a random
 * selection of the service content, so it changes as the user consumes content. The grid adapts
 * its column count to the available width.
 */
@Composable
fun HomeScreen(
    navigator: Navigator = koinInject(),
    viewModel: HomeViewModel = koinViewModel()
) {
    val services by viewModel.services.collectAsState()
    val kiosks by viewModel.kiosks.collectAsState()
    val selectedKiosk by viewModel.selectedKiosk.collectAsState()
    // Feed is a dedicated section on the home page, switchable like the other sections;
    // the latest section choice stays selected until the user picks another one.
    var selectedSection by remember { mutableStateOf("feed") }
    val items by viewModel.items.collectAsState()
    val feed by viewModel.feed.collectAsState()
    val feedLoading by viewModel.feedLoading.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()

    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(nearEnd, loadingMore, canLoadMore) {
        if (nearEnd && canLoadMore && !loadingMore) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HOME_BAR_HEIGHT)
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // The search entry sits centred in the toolbar like on YouTube and grows
                    // with the window width up to a sensible maximum.
                    Spacer(modifier = Modifier.weight(0.5f))

                    Surface(
                        onClick = { navigator.navigateTo(Destination.Search) },
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .height(HOME_SEARCH_HEIGHT)
                            .fillMaxWidth(0.72f)
                            .widthIn(min = HOME_SEARCH_MIN_WIDTH, max = HOME_SEARCH_MAX_WIDTH)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_search),
                                contentDescription = "Search",
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Search on NP Desktop",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.5f))
                }
            }
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
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    FilterChip(
                        selected = selectedSection == "feed",
                        onClick = { selectedSection = "feed" },
                        label = { Text("Feed") }
                    )
                    kiosks.forEach { kiosk ->
                        FilterChip(
                            selected = selectedSection == "kiosk" && kiosk.id == selectedKiosk,
                            onClick = {
                                selectedSection = "kiosk"
                                viewModel.selectKiosk(kiosk.id)
                            },
                            label = { Text(kiosk.name) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = Spacing.sm))

            when {
                error != null -> Message(
                    text = error!!,
                    modifier = Modifier.fillMaxSize()
                )

                selectedSection == "feed" && feedLoading && feed.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                selectedSection == "feed" && feed.isEmpty() -> Message(
                    text = "Watch a few videos to fill your feed",
                    modifier = Modifier.fillMaxSize()
                )

                selectedSection == "kiosk" && items.isEmpty() && loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                selectedSection == "kiosk" && items.isEmpty() -> Message(
                    text = "Nothing to show here right now",
                    modifier = Modifier.fillMaxSize()
                )

                selectedSection == "feed" -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = "Feed",
                            subtitle = if (feedLoading) {
                                "Updating your feed\u2026"
                            } else {
                                "Mixed from what you watched"
                            },
                            actionLabel = "Shuffle",
                            onAction = viewModel::shuffleFeed
                        )
                    }

                    items(feed, key = { "feed-${it.url}" }) { item ->
                        ContentCard(
                            item = item,
                            onClick = { navigateToItem(item, navigator) }
                        )
                    }
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = kiosks.firstOrNull { it.id == selectedKiosk }?.name
                                ?: "Trending",
                            subtitle = if (loading) "Loading\u2026" else null
                        )
                    }

                    items(items, key = { "kiosk-${it.url}" }) { item ->
                        ContentCard(
                            item = item,
                            onClick = { navigateToItem(item, navigator) }
                        )
                    }

                    if (loadingMore && items.isNotEmpty()) {
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
 * The heading of a feed or kiosk section, optionally with an action on the right.
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Navigates to the screen that matches the type of the item.
 */
/** Height of the compact home toolbar. */
private val HOME_BAR_HEIGHT = 56.dp

/** Maximum width of the search entry in the home toolbar. */
private val HOME_SEARCH_MAX_WIDTH = 640.dp

/** Minimum width of the search entry in the home toolbar. */
private val HOME_SEARCH_MIN_WIDTH = 320.dp

/** Height of the search entry in the home toolbar. */
private val HOME_SEARCH_HEIGHT = 40.dp

private fun navigateToItem(item: ContentItem, navigator: Navigator) {
    when (item.type) {
        ContentType.STREAM -> navigator.navigateTo(Destination.Stream(item.serviceId, item.url))
        ContentType.CHANNEL -> navigator.navigateTo(Destination.Channel(item.serviceId, item.url))
        ContentType.PLAYLIST -> navigator.navigateTo(Destination.Playlist(item.serviceId, item.url))
    }
}

/**
 * A dropdown to select the service whose content is shown.
 */
@Composable
private fun ServiceSelector(
    serviceName: String,
    serviceNames: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(text = serviceName)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            serviceNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(name)
                    }
                )
            }
        }
    }
}
