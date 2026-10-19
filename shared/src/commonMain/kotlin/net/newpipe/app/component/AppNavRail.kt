/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.LocalWorkspaceMode
import net.newpipe.app.platform.LocalWorkspaceModeChange
import net.newpipe.app.platform.WorkspaceMode
import net.newpipe.app.platform.isCompactSidebar
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceNormal
import newpipe.shared.generated.resources.Res
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

/**
 * The application-wide navigation sidebar, rendered for every screen so the shell behaves like the
 * Android drawer: the current destination is highlighted, selecting a destination switches the root
 * (the backstack is replaced, not grown), and settings sub-screens keep [Destination.Settings]
 * highlighted. [Destination.Player] hides the rail for an immersive player.
 */
@Composable
fun AppNavRail(
    modifier: Modifier = Modifier,
    navigator: Navigator = koinInject()
) {
    val workspaceMode = LocalWorkspaceMode.current
    val onWorkspaceModeChange = LocalWorkspaceModeChange.current
    val currentDestination = navigator.backstack.lastOrNull()
    if (currentDestination == Destination.Player) return

    NavRail(
        modifier = modifier,
        entries = railEntries(
            currentDestination = currentDestination,
            onNavigate = { destination ->
                when {
                    destination == currentDestination -> Unit

                    // The player stacks on top of the current screen so Back returns to it;
                    // every other rail entry switches the root like the Android drawer.
                    destination == Destination.Player -> navigator.navigateTo(destination)

                    else -> {
                        navigator.backstack.clear()
                        navigator.backstack.add(destination)
                    }
                }
            }
        ),
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
}

@Composable
private fun railEntries(
    currentDestination: Destination?,
    onNavigate: (Destination) -> Unit
): List<NavEntry> {
    val selected = railSelectedDestination(currentDestination)
    return listOf(
        NavEntry(
            label = "Home",
            selected = selected == Destination.Home,
            onClick = { onNavigate(Destination.Home) }
        ),
        NavEntry(
            label = stringResource(Res.string.nav_search),
            selected = selected == Destination.Search,
            onClick = { onNavigate(Destination.Search) }
        ),
        NavEntry(
            label = stringResource(Res.string.nav_trending),
            selected = selected == Destination.Trending,
            onClick = { onNavigate(Destination.Trending) }
        ),
        NavEntry(
            label = stringResource(Res.string.nav_history),
            selected = selected == Destination.History,
            onClick = { onNavigate(Destination.History) }
        ),
        NavEntry(
            label = "Player",
            selected = selected == Destination.Player,
            onClick = { onNavigate(Destination.Player) },
            icon = painterResource(Res.drawable.ic_headset)
        ),
        NavEntry(
            label = "Library",
            selected = selected == Destination.Library,
            onClick = { onNavigate(Destination.Library) },
            icon = painterResource(Res.drawable.ic_file_download)
        ),
        NavEntry(
            label = stringResource(Res.string.playlists_screen_title),
            selected = selected == Destination.Playlists,
            onClick = { onNavigate(Destination.Playlists) },
            icon = painterResource(Res.drawable.ic_headset)
        ),
        NavEntry(
            label = stringResource(Res.string.subscriptions_screen_title),
            selected = selected == Destination.Subscriptions,
            onClick = { onNavigate(Destination.Subscriptions) },
            icon = painterResource(Res.drawable.ic_notifications)
        ),
        NavEntry(
            label = "Downloads",
            selected = selected == Destination.Downloads,
            onClick = { onNavigate(Destination.Downloads) },
            icon = painterResource(Res.drawable.ic_cloud_download)
        ),
        NavEntry(
            label = "Settings",
            selected = selected == Destination.Settings,
            onClick = { onNavigate(Destination.Settings) },
            icon = painterResource(Res.drawable.ic_palette)
        ),
        NavEntry(
            label = "About",
            selected = selected == Destination.About,
            onClick = { onNavigate(Destination.About) },
            icon = painterResource(Res.drawable.ic_foreground)
        )
    )
}

/** Maps any destination to the rail entry it belongs to; settings sub-screens map to Settings. */
private fun railSelectedDestination(destination: Destination?): Destination? = when (destination) {
    Destination.Settings,
    Destination.BackupRestore,
    Destination.DownloadsSettings,
    Destination.HistorySettings,
    Destination.ContentSettings,
    Destination.LocalMediaSettings,
    Destination.SearchServicesSettings,
    Destination.AdvancedSettings,
    Destination.NotificationsSettings,
    Destination.AppearanceSettings,
    Destination.VideoAudioSettings,
    Destination.PlayerSettings -> Destination.Settings

    else -> destination
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
