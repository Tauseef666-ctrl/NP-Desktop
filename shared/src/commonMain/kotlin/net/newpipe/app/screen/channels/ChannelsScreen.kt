/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.component.VideoCard
import net.newpipe.app.component.providerErrorMessage
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.provider.ChannelNavigation
import net.newpipe.app.provider.PlaylistNavigation
import net.newpipe.app.provider.ProviderChannelTab
import net.newpipe.app.provider.ProviderClickAction
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentKind
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.providerClickAction
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.util.formatDuration
import net.newpipe.app.util.formatItemDetails
import net.newpipe.app.viewmodel.channels.ChannelsState
import net.newpipe.app.viewmodel.channels.ChannelsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.channels_empty_message
import newpipe.shared.generated.resources.channels_empty_title
import newpipe.shared.generated.resources.channels_loading
import newpipe.shared.generated.resources.channels_screen_title
import newpipe.shared.generated.resources.provider_retry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Channel destination. The channel shown comes from [net.newpipe.app.provider.ChannelNavigation];
 * its content is loaded through [ChannelsViewModel]. When nothing is selected, no provider is
 * connected, or the channel is empty the screen reports that honestly instead of inventing content.
 */
@Composable
fun ChannelsScreen(
    navigator: Navigator = koinInject(),
    viewModel: ChannelsViewModel = koinViewModel(),
    playlistNavigation: PlaylistNavigation = koinInject(),
    channelNavigation: ChannelNavigation = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val selectedTabId by viewModel.selectedTabId.collectAsStateWithLifecycle()

    LaunchedEffect(playbackState) {
        if (playbackState is ProviderPlaybackState.Launched) {
            viewModel.acknowledgePlayback()
            navigator.navigateTo(Destination.Player)
        }
    }

    ChannelsScreenContent(
        state = state,
        title = selection?.name ?: stringResource(Res.string.channels_screen_title),
        playbackState = playbackState,
        tabs = tabs,
        selectedTabId = selectedTabId,
        onSelectTab = { viewModel.selectTab(it) },
        onBack = { navigator.navigateUp() },
        onRetry = { viewModel.retry() },
        onItemClick = { item ->
            when (providerClickAction(item)) {
                ProviderClickAction.PLAY -> viewModel.play(item)
                ProviderClickAction.OPEN_CHANNEL -> {
                    channelNavigation.select(item.url, item.title)
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

@Composable
fun ChannelsScreenContent(
    state: ChannelsState,
    title: String,
    modifier: Modifier = Modifier,
    playbackState: ProviderPlaybackState = ProviderPlaybackState.Idle,
    tabs: List<ProviderChannelTab> = emptyList(),
    selectedTabId: String? = null,
    onSelectTab: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onItemClick: (ProviderContentItem) -> Unit = {},
    onLoadMore: () -> Unit = {}
) {
    PlusScaffold(
        modifier = modifier,
        toolbar = {
            TopToolbar(title = title, onBack = onBack)
        }
    ) {
        if (tabs.isEmpty()) {
            ChannelStateContent(
                state = state,
                playbackState = playbackState,
                onRetry = onRetry,
                onItemClick = onItemClick,
                onLoadMore = onLoadMore
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                FilterChipRow(
                    options = tabs.map { it.label },
                    selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0),
                    onSelect = { index -> onSelectTab(tabs[index].id) },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ChannelStateContent(
                        state = state,
                        playbackState = playbackState,
                        onRetry = onRetry,
                        onItemClick = onItemClick,
                        onLoadMore = onLoadMore
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ChannelStateContent(
    state: ChannelsState,
    playbackState: ProviderPlaybackState,
    onRetry: () -> Unit,
    onItemClick: (ProviderContentItem) -> Unit,
    onLoadMore: () -> Unit
) {
    PlaybackStatusMessage(playbackState, Modifier.align(Alignment.TopStart))
    when (state) {
        ChannelsState.Idle -> EmptyState(
            title = stringResource(Res.string.channels_empty_title),
            message = stringResource(Res.string.channels_empty_message),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth()
        )

        ChannelsState.Loading -> LoadingState(
            message = stringResource(Res.string.channels_loading),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth()
        )

        is ChannelsState.Failure -> ErrorState(
            message = providerErrorMessage(state.reason),
            retryLabel = stringResource(Res.string.provider_retry),
            onRetry = onRetry,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth()
        )

        is ChannelsState.Success -> if (state.items.isEmpty()) {
            EmptyState(
                title = stringResource(Res.string.channels_empty_title),
                message = stringResource(Res.string.channels_empty_message),
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
            )
        } else {
            ChannelItems(
                items = state.items,
                nextPageToken = state.nextPageToken,
                loadingMore = state.loadingMore,
                onItemClick = onItemClick,
                onLoadMore = onLoadMore
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelItems(
    items: List<ProviderContentItem>,
    nextPageToken: String?,
    loadingMore: Boolean,
    onItemClick: (ProviderContentItem) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spaceLarge),
        horizontalArrangement = Arrangement.spacedBy(spaceLarge),
        verticalArrangement = Arrangement.spacedBy(spaceLarge)
    ) {
        items.forEach { item ->
            VideoCard(
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
                subtitle = item.uploader,
                details = formatItemDetails(item.viewCount, item.textualUploadDate),
                durationText = item.durationSeconds?.let { formatDuration(it) },
                isLive = item.kind == ProviderContentKind.LIVE,
                onClick = { onItemClick(item) }
            )
        }
        if (nextPageToken != null) {
            ProviderLoadMore(
                loadingMore = loadingMore,
                onLoadMore = onLoadMore
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ChannelsScreenPreview() {
    ChannelsScreenContent(
        state = ChannelsState.Success(
            items = listOf(
                ProviderContentItem(
                    id = "v1",
                    title = "NewPipe+ — a private, polished media experience",
                    url = "https://example.com/watch?v=1",
                    uploader = "NewPipe",
                    durationSeconds = 754L,
                    kind = ProviderContentKind.VIDEO
                ),
                ProviderContentItem(
                    id = "l1",
                    title = "Live: release walkthrough",
                    url = "https://example.com/watch?v=2",
                    uploader = "NewPipe",
                    kind = ProviderContentKind.LIVE
                )
            )
        ),
        title = "NewPipe"
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ChannelsScreenTabsPreview() {
    ChannelsScreenContent(
        state = ChannelsState.Success(
            items = listOf(
                ProviderContentItem(
                    id = "v1",
                    title = "NewPipe+ — a private, polished media experience",
                    url = "https://example.com/watch?v=1",
                    uploader = "NewPipe",
                    durationSeconds = 754L,
                    kind = ProviderContentKind.VIDEO
                )
            )
        ),
        title = "NewPipe",
        tabs = listOf(
            ProviderChannelTab("videos", "Videos"),
            ProviderChannelTab("shorts", "Shorts"),
            ProviderChannelTab("livestreams", "Live")
        ),
        selectedTabId = "videos"
    )
}
