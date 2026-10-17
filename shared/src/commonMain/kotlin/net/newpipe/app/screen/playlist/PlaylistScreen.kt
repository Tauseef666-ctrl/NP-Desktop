/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import net.newpipe.app.component.LoadingState
import net.newpipe.app.component.PlaybackStatusMessage
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.component.VideoCard
import net.newpipe.app.component.providerErrorMessage
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentKind
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.util.formatDuration
import net.newpipe.app.viewmodel.playlist.PlaylistState
import net.newpipe.app.viewmodel.playlist.PlaylistViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.playlist_empty_message
import newpipe.shared.generated.resources.playlist_empty_title
import newpipe.shared.generated.resources.playlist_load_more
import newpipe.shared.generated.resources.playlist_loading
import newpipe.shared.generated.resources.playlist_screen_title
import newpipe.shared.generated.resources.provider_retry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Playlist destination. The playlist shown comes from [net.newpipe.app.provider.PlaylistNavigation];
 * its content is loaded through [PlaylistViewModel]. When nothing is selected, no provider is
 * connected, or the playlist is empty the screen reports that honestly instead of inventing content.
 */
@Composable
fun PlaylistScreen(
    navigator: Navigator = koinInject(),
    viewModel: PlaylistViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    LaunchedEffect(playbackState) {
        if (playbackState is ProviderPlaybackState.Launched) {
            viewModel.acknowledgePlayback()
            navigator.navigateTo(Destination.Player)
        }
    }

    PlaylistScreenContent(
        state = state,
        title = selection?.title ?: stringResource(Res.string.playlist_screen_title),
        playbackState = playbackState,
        onBack = { navigator.navigateUp() },
        onRetry = { viewModel.retry() },
        onItemClick = { viewModel.play(it) },
        onPlayAll = { viewModel.playAll() },
        onLoadMore = { viewModel.loadMore() }
    )
}

@Composable
fun PlaylistScreenContent(
    state: PlaylistState,
    title: String,
    modifier: Modifier = Modifier,
    playbackState: ProviderPlaybackState = ProviderPlaybackState.Idle,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onItemClick: (ProviderContentItem) -> Unit = {},
    onPlayAll: () -> Unit = {},
    onLoadMore: () -> Unit = {}
) {
    PlusScaffold(
        modifier = modifier,
        toolbar = {
            TopToolbar(title = title, onBack = onBack)
        }
    ) {
        PlaybackStatusMessage(playbackState, Modifier.align(Alignment.TopStart))
        when (state) {
            PlaylistState.Idle -> EmptyState(
                title = stringResource(Res.string.playlist_empty_title),
                message = stringResource(Res.string.playlist_empty_message),
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
            )

            PlaylistState.Loading -> LoadingState(
                message = stringResource(Res.string.playlist_loading),
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
            )

            is PlaylistState.Failure -> ErrorState(
                message = providerErrorMessage(state.reason),
                retryLabel = stringResource(Res.string.provider_retry),
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
            )

            is PlaylistState.Success -> if (state.items.isEmpty()) {
                EmptyState(
                    title = stringResource(Res.string.playlist_empty_title),
                    message = stringResource(Res.string.playlist_empty_message),
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth()
                )
            } else {
                PlaylistItems(
                    items = state.items,
                    nextPageToken = state.nextPageToken,
                    loadingMore = state.loadingMore,
                    onPlayAll = onPlayAll,
                    onItemClick = onItemClick,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun PlaylistItems(
    items: List<ProviderContentItem>,
    nextPageToken: String?,
    loadingMore: Boolean,
    onPlayAll: () -> Unit,
    onItemClick: (ProviderContentItem) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spaceLarge),
        verticalArrangement = Arrangement.spacedBy(spaceLarge)
    ) {
        TextButton(
            onClick = onPlayAll,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(text = "Play all")
        }
        items.forEach { item ->
            VideoCard(
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
                subtitle = item.uploader,
                durationText = item.durationSeconds?.let { formatDuration(it) },
                isLive = item.kind == ProviderContentKind.LIVE,
                onClick = { onItemClick(item) }
            )
        }
        if (nextPageToken != null) {
            TextButton(
                onClick = onLoadMore,
                enabled = !loadingMore,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = stringResource(Res.string.playlist_load_more))
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlaylistScreenPreview() {
    PlaylistScreenContent(
        state = PlaylistState.Success(
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
            ),
            nextPageToken = "PAGE2",
            loadingMore = false
        ),
        title = "NewPipe Weekly"
    )
}