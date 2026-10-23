/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.newpipe.app.composable.ContentListItem
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.viewmodel.search.SearchViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The search screen, which is the entry point of the application.
 */
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

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Load the next page when the user scrolls to the end of the list
    LaunchedEffect(listState, items.size, hasMore) {
        if (!hasMore || loading) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisible >= items.size - 3) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = "NewPipe")
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(query, debounce = false) })
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ServiceSelector(
                    serviceName = services.firstOrNull { it.id == serviceId }?.name ?: "Service",
                    serviceNames = services.map { it.name },
                    onSelect = { name ->
                        services.firstOrNull { it.name == name }?.let { viewModel.selectService(it.id) }
                    }
                )

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

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

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

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items, key = { it.url }) { item ->
                        ContentListItem(
                            item = item,
                            onClick = { navigateToItem(item, navigator) }
                        )
                    }

                    if (loading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
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
 * A dropdown to select the service to search on.
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

/**
 * A centred message, used for the empty and error states.
 */
@Composable
internal fun Message(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
