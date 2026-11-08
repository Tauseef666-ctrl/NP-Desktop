/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.newpipe.app.composable.ContentCard
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.ui.Spacing
import net.newpipe.app.ui.trackTextFieldFocus
import net.newpipe.app.viewmodel.search.SearchViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchScreen(
    navigator: Navigator = koinInject(),
    viewModel: SearchViewModel = koinViewModel()
) {
    val services by viewModel.services.collectAsState()
    val serviceId by viewModel.serviceId.collectAsState()
    val query by viewModel.query.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val selectedFilter by viewModel.filter.collectAsState()
    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }

    // The search field is focused right after the screen opens, so the user can type immediately
    // without clicking into the field first.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        runCatching { focusRequester.requestFocus() }
    }

    // Load the next page when the user scrolls to the end of the list
    LaunchedEffect(gridState, items.size, hasMore) {
        if (!hasMore || loading) return@LaunchedEffect
        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            ?: return@LaunchedEffect
        if (lastVisible >= items.size - 3) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = "NP Desktop")
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .focusRequester(focusRequester)
                    .trackTextFieldFocus(),
                placeholder = { Text("Search") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.search(query, debounce = false) }
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                services.forEach { service ->
                    FilterChip(
                        selected = service.id == serviceId,
                        onClick = { viewModel.selectService(service.id) },
                        label = { Text(service.name) }
                    )
                }

                if (filters.isNotEmpty()) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { viewModel.selectFilter(null) },
                        label = { Text("All") }
                    )
                    filters.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { viewModel.selectFilter(filter) },
                            label = { Text(filter.replaceFirstChar { it.uppercase() }) }
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

                items.isEmpty() && loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                items.isEmpty() -> Message(
                    text = if (query.isBlank()) {
                        "Enter a search term to start browsing"
                    } else {
                        "No results found"
                    },
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
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

                    if (loading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
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

/**
 * A centred message, used for the empty and error states.
 */
@Composable
internal fun Message(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
