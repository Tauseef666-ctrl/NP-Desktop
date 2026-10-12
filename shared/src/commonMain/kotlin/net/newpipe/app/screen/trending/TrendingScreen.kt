/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.trending

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.ErrorState
import net.newpipe.app.component.LoadingState
import net.newpipe.app.component.PlaybackStatusMessage
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.component.VideoCard
import net.newpipe.app.component.providerErrorMessage
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.provider.PlaylistNavigation
import net.newpipe.app.provider.ProviderClickAction
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentKind
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.providerClickAction
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.util.formatDuration
import net.newpipe.app.viewmodel.trending.TrendingState
import net.newpipe.app.viewmodel.trending.TrendingViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.provider_retry
import newpipe.shared.generated.resources.trending_empty_message
import newpipe.shared.generated.resources.trending_empty_title
import newpipe.shared.generated.resources.trending_loading
import newpipe.shared.generated.resources.trending_results_title
import newpipe.shared.generated.resources.trending_screen_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val ResultColumns = 3

/**
 * Trending destination. The ranked page is loaded from the provider on open; none of it is invented,
 * so before a provider is connected (or when loading fails) the screen reports that honestly.
 * Tapping a card selects its channel and opens the channel screen.
 */
@Composable
fun TrendingScreen(
    navigator: Navigator = koinInject(),
    viewModel: TrendingViewModel = koinViewModel(),
    playlistNavigation: PlaylistNavigation = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    LaunchedEffect(playbackState) {
        if (playbackState is ProviderPlaybackState.Launched) {
            viewModel.acknowledgePlayback()
            navigator.navigateTo(Destination.Player)
        }
    }

    TrendingScreenContent(
        state = state,
        playbackState = playbackState,
        onNavigateUp = { navigator.navigateUp() },
        onRetry = { viewModel.retry() },
        onItemClick = { item ->
            when (providerClickAction(item)) {
                ProviderClickAction.PLAY -> viewModel.play(item)
                ProviderClickAction.OPEN_CHANNEL -> {
                    if (viewModel.selectChannel(item)) {
                        navigator.navigateTo(Destination.Channels)
                    }
                }
                ProviderClickAction.OPEN_PLAYLIST -> {
                    playlistNavigation.select(item.url, item.title)
                    navigator.navigateTo(Destination.Playlist)
                }
            }
        }
    )
}

@Composable
fun TrendingScreenContent(
    state: TrendingState,
    modifier: Modifier = Modifier,
    playbackState: ProviderPlaybackState = ProviderPlaybackState.Idle,
    onNavigateUp: () -> Unit = {},
    onRetry: () -> Unit = {},
    onItemClick: (ProviderContentItem) -> Unit = {}
) {
    PlusScaffold(
        modifier = modifier,
        toolbar = {
            TopToolbar(
                title = stringResource(Res.string.trending_screen_title),
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
            SectionHeader(title = stringResource(Res.string.trending_results_title))
            PlaybackStatusMessage(playbackState)
            when (state) {
                TrendingState.Loading -> LoadingState(
                    message = stringResource(Res.string.trending_loading)
                )

                is TrendingState.Failure -> ErrorState(
                    message = providerErrorMessage(state.reason),
                    retryLabel = stringResource(Res.string.provider_retry),
                    onRetry = onRetry
                )

                is TrendingState.Success -> if (state.items.isEmpty()) {
                    EmptyState(
                        title = stringResource(Res.string.trending_empty_title),
                        message = stringResource(Res.string.trending_empty_message)
                    )
                } else {
                    TrendingGrid(items = state.items, onItemClick = onItemClick)
                }
            }
            Spacer(Modifier.height(spaceNormal))
        }
    }
}

@Composable
private fun TrendingGrid(
    items: List<ProviderContentItem>,
    onItemClick: (ProviderContentItem) -> Unit
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
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun TrendingScreenPreview() {
    TrendingScreenContent(
        state = TrendingState.Success(
            items = listOf(
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
                    title = "Live: community stream",
                    url = "https://example.org/live/2",
                    uploader = "NewPipe",
                    kind = ProviderContentKind.LIVE
                )
            )
        )
    )
}
