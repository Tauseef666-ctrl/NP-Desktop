/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.ErrorState
import net.newpipe.app.component.FilterChipRow
import net.newpipe.app.component.LoadingState
import net.newpipe.app.component.PlaybackStatusMessage
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.ProviderLoadMore
import net.newpipe.app.component.SearchBarField
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.component.VideoCard
import net.newpipe.app.component.providerErrorMessage
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.provider.PlaylistNavigation
import net.newpipe.app.provider.ProviderClickAction
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentKind
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.SearchFilter
import net.newpipe.app.provider.providerClickAction
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.util.formatDuration
import net.newpipe.app.viewmodel.search.SearchResultsState
import net.newpipe.app.viewmodel.search.SearchViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.provider_retry
import newpipe.shared.generated.resources.search_history_clear
import newpipe.shared.generated.resources.search_history_empty
import newpipe.shared.generated.resources.search_history_title
import newpipe.shared.generated.resources.search_results_empty_message
import newpipe.shared.generated.resources.search_results_empty_title
import newpipe.shared.generated.resources.search_results_loading
import newpipe.shared.generated.resources.search_results_title
import newpipe.shared.generated.resources.search_screen_placeholder
import newpipe.shared.generated.resources.search_screen_title
import newpipe.shared.generated.resources.search_suggestions_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val ResultColumns = 2

/**
 * Search destination. The input is live; recent searches and suggestions come from the persisted
 * history, while submitted queries are resolved through [SearchViewModel] against the provider
 * repository and rendered as real result cards.
 */
@Composable
fun SearchScreen(
    navigator: Navigator = koinInject(),
    viewModel: SearchViewModel = koinViewModel(),
    playlistNavigation: PlaylistNavigation = koinInject()
) {
    var query by remember { mutableStateOf("") }
    val history by viewModel.historyEntries.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    LaunchedEffect(playbackState) {
        if (playbackState is ProviderPlaybackState.Launched) {
            viewModel.acknowledgePlayback()
            navigator.navigateTo(Destination.Player)
        }
    }
    SearchScreenContent(
        query = query,
        onQueryChange = {
            query = it
            viewModel.clearResults()
        },
        onNavigateUp = { navigator.navigateUp() },
        history = history,
        suggestions = viewModel.suggestions(query),
        results = results,
        selectedFilter = selectedFilter,
        playbackState = playbackState,
        onClearHistory = { viewModel.clearHistory() },
        onSubmitQuery = { viewModel.search(it) },
        onFilterChange = { viewModel.setFilter(it) },
        onItemClick = { item ->
            when (providerClickAction(item)) {
                ProviderClickAction.PLAY -> viewModel.play(item)
                ProviderClickAction.OPEN_CHANNEL -> {
                    viewModel.selectChannel(item.url, item.title)
                    navigator.navigateTo(Destination.Channels)
                }
                ProviderClickAction.OPEN_PLAYLIST -> {
                    playlistNavigation.select(item.url, item.title)
                    navigator.navigateTo(Destination.Playlist)
                }
            }
        },
        onLoadMore = { viewModel.loadMore() }
    )
}

/**
 * Stateless search content. Result rendering is driven by [results]; while the query is blank only
 * recent history is shown, and while no search has run yet ([SearchResultsState.Idle]) the
 * suggestions are shown in place of the results section.
 */
@Composable
fun SearchScreenContent(
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    history: List<String> = emptyList(),
    suggestions: List<String> = emptyList(),
    results: SearchResultsState = SearchResultsState.Idle,
    selectedFilter: SearchFilter = SearchFilter.ALL,
    playbackState: ProviderPlaybackState = ProviderPlaybackState.Idle,
    onClearHistory: () -> Unit = {},
    onSubmitQuery: (String) -> Unit = {},
    onFilterChange: (SearchFilter) -> Unit = {},
    onItemClick: (ProviderContentItem) -> Unit = {},
    onLoadMore: () -> Unit = {}
) {
    PlusScaffold(
        toolbar = {
            TopToolbar(
                title = stringResource(Res.string.search_screen_title),
                onBack = onNavigateUp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = spaceNormal)
        ) {
            SearchBarField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.padding(horizontal = spaceLarge),
                placeholder = stringResource(Res.string.search_screen_placeholder),
                onSubmit = { onSubmitQuery(query) }
            )
            if (query.isNotBlank()) {
                FilterChipRow(
                    options = SearchFilter.entries.map { it.label },
                    selectedIndex = SearchFilter.entries.indexOf(selectedFilter).coerceAtLeast(0),
                    onSelect = { index -> onFilterChange(SearchFilter.entries[index]) },
                    modifier = Modifier.padding(horizontal = spaceLarge)
                )
            }
            when {
                query.isBlank() -> {
                    SectionHeader(title = stringResource(Res.string.search_history_title))
                    if (history.isEmpty()) {
                        EmptyState(title = stringResource(Res.string.search_history_empty))
                    } else {
                        TextButton(
                            onClick = onClearHistory,
                            modifier = Modifier.padding(horizontal = spaceNormal)
                        ) {
                            Text(text = stringResource(Res.string.search_history_clear))
                        }
                        history.forEach { entry ->
                            PreferenceRow(
                                title = entry,
                                onClick = { onQueryChange(entry) }
                            )
                        }
                    }
                }

                results is SearchResultsState.Idle -> {
                    SectionHeader(title = stringResource(Res.string.search_suggestions_title))
                    suggestions.forEach { suggestion ->
                        PreferenceRow(
                            title = suggestion,
                            onClick = { onQueryChange(suggestion) }
                        )
                    }
                }

                else -> {
                    PlaybackStatusMessage(playbackState)
                    SearchResultsSection(
                        results = results,
                        onRetry = { onSubmitQuery(query) },
                        onItemClick = onItemClick,
                        onLoadMore = onLoadMore
                    )
                }
            }
            Spacer(Modifier.height(spaceNormal))
        }
    }
}

@Composable
private fun SearchResultsSection(
    results: SearchResultsState,
    onRetry: () -> Unit,
    onItemClick: (ProviderContentItem) -> Unit,
    onLoadMore: () -> Unit
) {
    SectionHeader(title = stringResource(Res.string.search_results_title))
    when (results) {
        SearchResultsState.Idle -> Unit

        SearchResultsState.Loading -> LoadingState(
            message = stringResource(Res.string.search_results_loading)
        )

        is SearchResultsState.Failure -> ErrorState(
            message = providerErrorMessage(results.reason),
            retryLabel = stringResource(Res.string.provider_retry),
            onRetry = onRetry
        )

        is SearchResultsState.Success -> if (results.items.isEmpty()) {
            EmptyState(
                title = stringResource(Res.string.search_results_empty_title),
                message = stringResource(Res.string.search_results_empty_message)
            )
        } else {
            SearchResultsGrid(
                items = results.items,
                nextPageToken = results.nextPageToken,
                loadingMore = results.loadingMore,
                onItemClick = onItemClick,
                onLoadMore = onLoadMore
            )
        }
    }
}

@Composable
private fun SearchResultsGrid(
    items: List<ProviderContentItem>,
    nextPageToken: String?,
    loadingMore: Boolean,
    onItemClick: (ProviderContentItem) -> Unit,
    onLoadMore: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.chunked(ResultColumns).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spaceNormal, vertical = spaceNormal),
                horizontalArrangement = Arrangement.spacedBy(spaceNormal)
            ) {
                row.forEach { item ->
                    VideoCard(
                        title = item.title,
                        subtitle = item.uploader,
                        durationText = item.durationSeconds
                            ?.takeIf { item.kind != ProviderContentKind.LIVE }
                            ?.let { formatDuration(it) },
                        isLive = item.kind == ProviderContentKind.LIVE,
                        modifier = Modifier.weight(1f),
                        onClick = { onItemClick(item) }
                    )
                }
                repeat(ResultColumns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (nextPageToken != null) {
            ProviderLoadMore(
                loadingMore = loadingMore,
                onLoadMore = onLoadMore,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SearchScreenPreview() {
    SearchScreenContent(
        query = "NewPipe",
        history = listOf("NewPipe updates", "Kotlin Multiplatform"),
        selectedFilter = SearchFilter.VIDEOS,
        results = SearchResultsState.Success(
            listOf(
                ProviderContentItem(
                    id = "1",
                    title = "NewPipe+ walkthrough",
                    url = "https://example.org/watch/1",
                    uploader = "NewPipe",
                    durationSeconds = 754,
                    kind = ProviderContentKind.VIDEO
                ),
                ProviderContentItem(
                    id = "2",
                    title = "Kotlin Multiplatform in 2026",
                    url = "https://example.org/watch/2",
                    uploader = "Kotlin",
                    durationSeconds = 3600,
                    kind = ProviderContentKind.VIDEO
                ),
                ProviderContentItem(
                    id = "3",
                    title = "Live: NewPipe community stream",
                    url = "https://example.org/live/3",
                    uploader = "NewPipe",
                    kind = ProviderContentKind.LIVE
                )
            )
        )
    )
}
