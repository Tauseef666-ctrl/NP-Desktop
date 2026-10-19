/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.ErrorState
import net.newpipe.app.component.LoadingState
import net.newpipe.app.component.PlaybackStatusMessage
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.component.VideoCard
import net.newpipe.app.component.VideoRail
import net.newpipe.app.component.providerErrorMessage
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentKind
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.util.formatDuration
import net.newpipe.app.util.formatItemDetails
import net.newpipe.app.viewmodel.home.HomeTrendingState
import net.newpipe.app.viewmodel.home.HomeViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.home_continue_watching_title
import newpipe.shared.generated.resources.home_downloads_title
import newpipe.shared.generated.resources.home_empty_message
import newpipe.shared.generated.resources.home_empty_title
import newpipe.shared.generated.resources.home_playlists_title
import newpipe.shared.generated.resources.home_recently_played_title
import newpipe.shared.generated.resources.nav_trending
import newpipe.shared.generated.resources.provider_retry
import newpipe.shared.generated.resources.trending_loading
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Desktop home shell: navigation rail + toolbar + content fed by two honest sources — the live
 * provider's trending page (the front-page online rail) and the persisted local stores. When every
 * source is empty an honest empty state is displayed instead; provider failures show localized
 * error text with a retry rather than fake content.
 */
@Composable
fun HomeScreen(
    navigator: Navigator = koinInject(),
    viewModel: HomeViewModel = koinViewModel()
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val trendingState by viewModel.trending.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(playbackState) {
        if (playbackState is ProviderPlaybackState.Launched) {
            viewModel.acknowledgePlayback()
            navigator.navigateTo(Destination.Player)
        }
    }
    HomeScreenContent(
        sections = sections,
        trendingState = trendingState,
        playbackState = playbackState,
        onPlayItem = { item ->
            viewModel.play(item)
            if (item.url.isNotBlank()) navigator.navigateTo(Destination.Player)
        },
        onPlayTrending = { item -> viewModel.playTrending(item) },
        onRetryTrending = { viewModel.retryTrending() }
    )
}

@Composable
fun HomeScreenContent(
    sections: List<HomeSection> = emptyList(),
    trendingState: HomeTrendingState = HomeTrendingState.Idle,
    playbackState: ProviderPlaybackState = ProviderPlaybackState.Idle,
    onPlayItem: (HomeRailItem) -> Unit = {},
    onPlayTrending: (ProviderContentItem) -> Unit = {},
    onRetryTrending: () -> Unit = {}
) {
    val trendingItems = (trendingState as? HomeTrendingState.Items)?.items.orEmpty()
    val showEmptyState = sections.isEmpty() &&
        trendingItems.isEmpty() &&
        trendingState !is HomeTrendingState.Loading &&
        trendingState !is HomeTrendingState.Failure

    PlusScaffold(
        toolbar = { TopToolbar(title = "Home") }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                WelcomeHero()
                PlaybackStatusMessage(playbackState)
                when (trendingState) {
                    HomeTrendingState.Idle -> Unit
                    HomeTrendingState.Loading -> LoadingState(
                        message = stringResource(Res.string.trending_loading)
                    )
                    is HomeTrendingState.Failure -> ErrorState(
                        message = providerErrorMessage(trendingState.reason),
                        retryLabel = stringResource(Res.string.provider_retry),
                        onRetry = onRetryTrending
                    )
                    is HomeTrendingState.Items -> if (trendingState.items.isNotEmpty()) {
                        TrendingRail(
                            items = trendingState.items,
                            onPlayItem = onPlayTrending
                        )
                    }
                }
                if (showEmptyState) {
                    EmptyState(
                        title = stringResource(Res.string.home_empty_title),
                        message = stringResource(Res.string.home_empty_message)
                    )
                } else {
                    sections.forEach { section ->
                        HomeRail(
                            title = homeRailTitle(section.type),
                            items = section.items,
                            onPlayItem = onPlayItem
                        )
                    }
                }
                Spacer(Modifier.height(spaceLarge))
            }
        }
    }
}

@Composable
private fun homeRailTitle(type: HomeRailType): String = when (type) {
    HomeRailType.CONTINUE_WATCHING -> stringResource(Res.string.home_continue_watching_title)
    HomeRailType.RECENTLY_PLAYED -> stringResource(Res.string.home_recently_played_title)
    HomeRailType.DOWNLOADS -> stringResource(Res.string.home_downloads_title)
    HomeRailType.PLAYLISTS -> stringResource(Res.string.home_playlists_title)
}

/** One titled horizontal rail of [VideoCard]s rendered from a real [HomeSection]. */
@Composable
private fun HomeRail(
    title: String,
    items: List<HomeRailItem>,
    onPlayItem: (HomeRailItem) -> Unit
) {
    VideoRail(title = title) {
        items(items) { item ->
            VideoCard(
                title = item.title,
                subtitle = item.subtitle,
                onClick = { onPlayItem(item) }
            )
        }
    }
}

/** The provider's trending page as the front-page rail, with real artwork and duration badges. */
@Composable
private fun TrendingRail(
    items: List<ProviderContentItem>,
    onPlayItem: (ProviderContentItem) -> Unit
) {
    VideoRail(title = stringResource(Res.string.nav_trending)) {
        items(items) { item ->
            VideoCard(
                title = item.title,
                subtitle = item.uploader,
                details = formatItemDetails(item.viewCount, item.textualUploadDate),
                thumbnailUrl = item.thumbnailUrl,
                durationText = item.durationSeconds
                    ?.takeIf { item.kind != ProviderContentKind.LIVE }
                    ?.let { formatDuration(it) },
                isLive = item.kind == ProviderContentKind.LIVE,
                onClick = { onPlayItem(item) }
            )
        }
    }
}

@Composable
private fun WelcomeHero() {
    val colors = plusColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spaceLarge)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(spaceLarge)
    ) {
        Column {
            Text(
                text = "Private, open, and yours",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(spaceSmall))
            Text(
                text = "NewPipe+ enhances NewPipe with a polished desktop experience — no accounts, no tracking.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun HomeScreenPreview() {
    HomeScreenContent()
}