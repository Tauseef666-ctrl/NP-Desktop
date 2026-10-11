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
import net.newpipe.app.component.NavEntry
import net.newpipe.app.component.NavRail
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.component.VideoCard
import net.newpipe.app.component.VideoRail
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.LocalWorkspaceMode
import net.newpipe.app.platform.LocalWorkspaceModeChange
import net.newpipe.app.platform.WorkspaceMode
import net.newpipe.app.platform.isCompactSidebar
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.viewmodel.home.HomeViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.home_continue_watching_title
import newpipe.shared.generated.resources.home_downloads_title
import newpipe.shared.generated.resources.home_empty_message
import newpipe.shared.generated.resources.home_empty_title
import newpipe.shared.generated.resources.home_playlists_title
import newpipe.shared.generated.resources.home_recently_played_title
import newpipe.shared.generated.resources.ic_cloud_download
import newpipe.shared.generated.resources.ic_file_download
import newpipe.shared.generated.resources.ic_foreground
import newpipe.shared.generated.resources.ic_headset
import newpipe.shared.generated.resources.ic_notifications
import newpipe.shared.generated.resources.ic_palette
import newpipe.shared.generated.resources.layout_balanced
import newpipe.shared.generated.resources.layout_compact
import newpipe.shared.generated.resources.layout_expanded
import newpipe.shared.generated.resources.nav_history
import newpipe.shared.generated.resources.nav_search
import newpipe.shared.generated.resources.nav_trending
import newpipe.shared.generated.resources.playlists_screen_title
import newpipe.shared.generated.resources.subscriptions_screen_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Desktop home shell: navigation rail + toolbar + real content rails fed by the persisted stores.
 * Only real data is shown; when every store is empty an honest empty state is displayed instead.
 */
@Composable
fun HomeScreen(
    navigator: Navigator = koinInject(),
    viewModel: HomeViewModel = koinViewModel()
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val isEmpty by viewModel.isEmpty.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    HomeScreenContent(
        sections = sections,
        isEmpty = isEmpty,
        onPlayItem = { item ->
            viewModel.play(item)
            if (item.url.isNotBlank()) navigator.navigateTo(Destination.Player)
        },
        onNavigate = { destination ->
            if (destination != Destination.Home) navigator.navigateTo(destination)
        }
    )
}

@Composable
fun HomeScreenContent(
    selectedDestination: Destination = Destination.Home,
    onNavigate: (Destination) -> Unit = {},
    sections: List<HomeSection> = emptyList(),
    isEmpty: Boolean = true,
    onPlayItem: (HomeRailItem) -> Unit = {}
) {
    val workspaceMode = LocalWorkspaceMode.current
    val onWorkspaceModeChange = LocalWorkspaceModeChange.current

    val entries = listOf(
        NavEntry(
            label = "Home",
            selected = selectedDestination == Destination.Home,
            onClick = { onNavigate(Destination.Home) }
        ),
        NavEntry(
            label = stringResource(Res.string.nav_search),
            selected = selectedDestination == Destination.Search,
            onClick = { onNavigate(Destination.Search) }
        ),
        NavEntry(
            label = stringResource(Res.string.nav_trending),
            selected = selectedDestination == Destination.Trending,
            onClick = { onNavigate(Destination.Trending) }
        ),
        NavEntry(
            label = stringResource(Res.string.nav_history),
            selected = selectedDestination == Destination.History,
            onClick = { onNavigate(Destination.History) }
        ),
        NavEntry(
            label = "Player",
            selected = selectedDestination == Destination.Player,
            onClick = { onNavigate(Destination.Player) },
            icon = painterResource(Res.drawable.ic_headset)
        ),
        NavEntry(
            label = "Library",
            selected = selectedDestination == Destination.Library,
            onClick = { onNavigate(Destination.Library) },
            icon = painterResource(Res.drawable.ic_file_download)
        ),
        NavEntry(
            label = stringResource(Res.string.playlists_screen_title),
            selected = selectedDestination == Destination.Playlists,
            onClick = { onNavigate(Destination.Playlists) },
            icon = painterResource(Res.drawable.ic_headset)
        ),
        NavEntry(
            label = stringResource(Res.string.subscriptions_screen_title),
            selected = selectedDestination == Destination.Subscriptions,
            onClick = { onNavigate(Destination.Subscriptions) },
            icon = painterResource(Res.drawable.ic_notifications)
        ),
        NavEntry(
            label = "Downloads",
            selected = selectedDestination == Destination.Downloads,
            onClick = { onNavigate(Destination.Downloads) },
            icon = painterResource(Res.drawable.ic_cloud_download)
        ),
        NavEntry(
            label = "Settings",
            selected = selectedDestination == Destination.Settings,
            onClick = { onNavigate(Destination.Settings) },
            icon = painterResource(Res.drawable.ic_palette)
        ),
        NavEntry(
            label = "About",
            selected = selectedDestination == Destination.About,
            onClick = { onNavigate(Destination.About) },
            icon = painterResource(Res.drawable.ic_foreground)
        )
    )

    PlusScaffold(
        rail = {
            NavRail(
                entries = entries,
                header = { RailHeader(mode = workspaceMode) },
                onToggleExpanded = {
                    onWorkspaceModeChange(
                        if (workspaceMode.isCompactSidebar) {
                            WorkspaceMode.EXPANDED
                        } else {
                            WorkspaceMode.COMPACT
                        }
                    )
                }
            )
        },
        toolbar = { TopToolbar(title = "Home") }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                WelcomeHero()
                if (isEmpty) {
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

@Composable
private fun RailHeader(mode: WorkspaceMode) {
    val colors = plusColors()
    val expanded = !mode.isCompactSidebar
    Column(modifier = Modifier.padding(horizontal = spaceNormal, vertical = spaceNormal)) {
        Text(
            text = if (expanded) "NewPipe+" else "N+",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        if (expanded) {
            Text(
                text = mode.displayName(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun WorkspaceMode.displayName(): String = stringResource(
    when (this) {
        WorkspaceMode.COMPACT -> Res.string.layout_compact
        WorkspaceMode.BALANCED -> Res.string.layout_balanced
        WorkspaceMode.EXPANDED -> Res.string.layout_expanded
    }
)

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