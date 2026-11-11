/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.newpipe.app.composable.ContentCard
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.ui.Glass
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import net.newpipe.app.ui.trackTextFieldFocus
import net.newpipe.app.viewmodel.search.SearchSessionStore
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
    val suggestions by viewModel.suggestions.collectAsState()
    val selectedSuggestionIndex by viewModel.selectedSuggestionIndex.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val selectedFilter by viewModel.filter.collectAsState()
    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    var serviceMenuOpen by remember { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }

    // The search field is focused right after the screen opens, so the user can type immediately
    // without clicking into the field first.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        runCatching { focusRequester.requestFocus() }
    }

    // Returning to a recent search restores its results from the session store instead of
    // starting blank; typing a new query replaces them through the normal update path.
    LaunchedEffect(Unit) {
        if (items.isEmpty() && SearchSessionStore.items.isNotEmpty()) {
            viewModel.restoreSession(
                query = SearchSessionStore.query,
                filter = SearchSessionStore.filter,
                items = SearchSessionStore.items,
                hasMore = SearchSessionStore.hasMore
            )
        }
    }

    // Load the next page when the user scrolls to the end of the list. A snapshot flow on the
    // last visible index is used instead of a plain LaunchedEffect key: the grid state object
    // never changes identity, so a keyed effect would not re-fire while the user keeps
    // scrolling through pages of results.
    LaunchedEffect(gridState, hasMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (!hasMore) return@collect
                val index = lastVisible ?: return@collect
                if (index >= items.size - 3) {
                    viewModel.loadMore()
                }
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
                onValueChange = { viewModel.updateQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .focusRequester(focusRequester)
                    .trackTextFieldFocus()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val dropdownVisible = suggestions.isNotEmpty() && query.isNotBlank() && !loading
                        when (event.key) {
                            Key.DirectionDown -> dropdownVisible && viewModel.moveSuggestionSelection(1)
                            Key.DirectionUp -> dropdownVisible && viewModel.moveSuggestionSelection(-1)
                            Key.Enter, Key.NumPadEnter -> {
                                if (dropdownVisible && selectedSuggestionIndex >= 0) {
                                    viewModel.submitSuggestionOrQuery()
                                    true
                                } else {
                                    viewModel.search(query, debounce = false)
                                    true
                                }
                            }
                            Key.Tab -> {
                                if (dropdownVisible) {
                                    val selected = suggestions.getOrNull(selectedSuggestionIndex)
                                        ?: suggestions.firstOrNull()
                                    if (selected != null) {
                                        viewModel.completeSuggestion(selected)
                                        true
                                    } else false
                                } else false
                            }
                            Key.Escape -> {
                                if (dropdownVisible) {
                                    viewModel.clearSuggestions()
                                    true
                                } else false
                            }
                            else -> false
                        }
                    },
                placeholder = {
                    Text(
                        text = "Search",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.submitSuggestionOrQuery() }
                )
            )

            if (suggestions.isNotEmpty() && query.isNotBlank() && !loading) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    color = Glass.color(0.9f),
                    border = Glass.borderStroke(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.md)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = Spacing.xs)
                    ) {
                        lazyItemsIndexed(suggestions, key = { _, suggestion -> suggestion }) { index, suggestion ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val hovered by interactionSource.collectIsHoveredAsState()
                            LaunchedEffect(hovered) {
                                if (hovered) viewModel.highlightSuggestion(index)
                            }
                            val selected = index == selectedSuggestionIndex
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .hoverable(interactionSource)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                        } else {
                                            androidx.compose.ui.graphics.Color.Transparent
                                        }
                                    )
                                    .clickable { viewModel.search(suggestion, debounce = false) }
                                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Services and content filters as compact menus instead of long chip rows: every
                // service (YouTube, SoundCloud, media.ccc.de, PeerTube, Bandcamp) stays switchable
                // without flooding the screen.
                val activeService = services.firstOrNull { it.id == serviceId }
                ExposedDropdownMenuBox(
                    expanded = serviceMenuOpen,
                    onExpandedChange = { serviceMenuOpen = it },
                    modifier = Modifier.width(150.dp)
                ) {
                    OutlinedTextField(
                        value = activeService?.name ?: "YouTube",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Service") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(serviceMenuOpen) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = serviceMenuOpen,
                        onDismissRequest = { serviceMenuOpen = false }
                    ) {
                        services.forEach { service ->
                            DropdownMenuItem(
                                text = { Text(service.name) },
                                onClick = {
                                    serviceMenuOpen = false
                                    viewModel.selectService(service.id)
                                }
                            )
                        }
                    }
                }

                if (filters.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = filterMenuOpen,
                        onExpandedChange = { filterMenuOpen = it },
                        modifier = Modifier.width(170.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedFilter?.replaceFirstChar { it.uppercase() } ?: "All",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Filter") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(filterMenuOpen) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = filterMenuOpen,
                            onDismissRequest = { filterMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All") },
                                onClick = {
                                    filterMenuOpen = false
                                    viewModel.selectFilter(null)
                                }
                            )
                            filters.forEach { filter ->
                                DropdownMenuItem(
                                    text = { Text(filter.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        filterMenuOpen = false
                                        viewModel.selectFilter(filter)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = Spacing.sm))

            when {
                // A failed page while results are already on the screen must keep the grid
                // visible: replacing it with an error message made the user lose everything
                // when a single continuation was slow or failed. The inline retry row at the
                // end of the grid covers that case below.
                error != null && items.isEmpty() -> Message(
                    text = error!!,
                    modifier = Modifier.fillMaxSize()
                )

                items.isEmpty() && loading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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

                    if (error != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SearchRetryRow(
                                message = error!!,
                                onRetry = { viewModel.retryPage() }
                            )
                        }
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
private fun navigateToItem(item: ContentItem, navigator: Navigator) {    when (item.type) {
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

/**
 * The inline "load the next page" failure row at the end of the results grid: the already
 * loaded results stay visible while it explains that a page failed and repeats it on demand.
 */
@Composable
private fun SearchRetryRow(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}
