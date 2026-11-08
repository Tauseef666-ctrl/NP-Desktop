/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.StreamDetails
import net.newpipe.app.navigation.Destination
import net.newpipe.app.screen.stream.DownloadDialog
import net.newpipe.app.screen.stream.StreamIntents
import net.newpipe.app.service.ContentRepository
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.DownloadRepository
import net.newpipe.app.service.MediaDownloader
import net.newpipe.app.service.PopupPlayer
import net.newpipe.app.ui.Breakpoints
import net.newpipe.app.ui.Layout
import net.newpipe.app.ui.PlayerUiState
import net.newpipe.app.ui.Motion
import net.newpipe.app.ui.isTvLayout
import net.newpipe.app.ui.Glass
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_file_download
import newpipe.shared.generated.resources.ic_live_tv
import newpipe.shared.generated.resources.ic_movie
import newpipe.shared.generated.resources.ic_music_note
import newpipe.shared.generated.resources.ic_podcasts
import newpipe.shared.generated.resources.ic_sports_esports
import newpipe.shared.generated.resources.ic_subscriptions
import newpipe.shared.generated.resources.ic_trending_up
import newpipe.shared.generated.resources.ic_history
import newpipe.shared.generated.resources.ic_home
import newpipe.shared.generated.resources.ic_np_desktop_logo
import newpipe.shared.generated.resources.ic_language
import newpipe.shared.generated.resources.ic_search
import newpipe.shared.generated.resources.ic_settings_backup_restore
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * An entry of the shell navigation rail.
 *
 * Only destinations that are actually implemented are listed, so that the rail never contains a
 * button which does nothing.
 */
private data class ShellEntry(
    val destination: Destination,
    val label: String,
    val icon: DrawableResource
)

private val shellEntries = listOf(
    ShellEntry(Destination.Home, "Home", Res.drawable.ic_home),
    ShellEntry(
        Destination.Feed("Trending", "Trending"),
        "Trending",
        Res.drawable.ic_trending_up
    ),
    ShellEntry(Destination.Feed("live", "Live"), "Live", Res.drawable.ic_live_tv),
    ShellEntry(
        Destination.Feed("trending_gaming", "Gaming"),
        "Gaming",
        Res.drawable.ic_sports_esports
    ),
    ShellEntry(
        Destination.Feed("trending_music", "Music"),
        "Music",
        Res.drawable.ic_music_note
    ),
    ShellEntry(
        Destination.Feed("trending_movies_and_shows", "Movies"),
        "Movies",
        Res.drawable.ic_movie
    ),
    ShellEntry(
        Destination.Feed("trending_podcasts_episodes", "Podcasts"),
        "Podcasts",
        Res.drawable.ic_podcasts
    ),
        ShellEntry(Destination.Subscriptions, "Feed", Res.drawable.ic_subscriptions),
        ShellEntry(Destination.History, "History", Res.drawable.ic_history),
        ShellEntry(Destination.Downloads, "Downloads", Res.drawable.ic_file_download),
    ShellEntry(Destination.Settings, "Settings", Res.drawable.ic_settings_backup_restore),
    ShellEntry(Destination.About, "About", Res.drawable.ic_language)
)

/**
 * The desktop application shell.
 *
 * The navigation rail is hidden by default, so the screen content uses the whole window. Moving the
 * pointer to the left edge of the window reveals the rail; it hides again shortly after the pointer
 * leaves it. Rail clicks work normally because the rail stays open while the pointer is over it.
 *
 * @param currentDestination the destination currently shown, used to highlight the rail entry
 * @param onNavigate invoked when a rail entry is activated
 * @param immersive whether the player currently uses the whole window; the rail stays hidden then
 * @param content the screen content
 */
@Composable
fun ModernShell(
    currentDestination: Destination,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
    controller: PlaybackController = koinInject(),
    playerUiState: PlayerUiState = koinInject(),
    contentRepository: ContentRepository = koinInject(),
    downloader: MediaDownloader = koinInject(),
    downloadRepository: DownloadRepository = koinInject(),
    content: @Composable () -> Unit
) {
    val edgeInteraction = remember { MutableInteractionSource() }
    val edgeHovered by edgeInteraction.collectIsHoveredAsState()
    val railInteraction = remember { MutableInteractionSource() }
    val railHovered by railInteraction.collectIsHoveredAsState()

    // The layout split between desktop (Windows/Linux, hover driven) and television
    // (Android TV, remote driven): a television always shows its rail and never uses the
    // hover reveal, while the desktop keeps the edge reveal with auto collapse.
    val tv = isTvLayout

    var revealed by remember { mutableStateOf(false) }

    // On the home screen the rail is always visible in its collapsed form and expands on hover.
    val collapsedRail = !tv && currentDestination == Destination.Home && !immersive

    // Navigating from the rail collapses it at once, so that the expanded panel does not stay
    // open over the content of the new screen while the pointer is still on it.
    val railNavigate: (Destination) -> Unit = { destination ->
        revealed = false
        onNavigate(destination)
    }

    LaunchedEffect(edgeHovered, railHovered, immersive, tv) {
        if (tv) return@LaunchedEffect
        if (immersive) {
            revealed = false
            return@LaunchedEffect
        }

        if (edgeHovered || railHovered) {
            revealed = true
        } else {
            delay(RAIL_HIDE_DELAY_MS)
            revealed = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val playbackState by controller.state.collectAsState()
        val popupState by PopupPlayer.state.collectAsState()

        // The layout split between desktop (Windows/Linux, hover driven) and television
        // (Android TV, remote driven): a television always shows its rail and never uses the
        // hover reveal, while the desktop keeps the edge reveal with auto collapse.
        val tvRailExpanded = maxWidth >= Breakpoints.medium

        // Only one player surface at a time: while the popup player is open the mini player
        // bar is hidden, so the video is never playing twice on the same screen.
        val showMiniPlayer = playbackState.title.isNotEmpty() && popupState == null && !immersive &&
            currentDestination !is Destination.Stream

        val contentStartPadding = when {
            immersive -> 0.dp
            tv && tvRailExpanded -> Layout.navRailExpanded
            collapsedRail -> Layout.navRailCollapsed
            tv -> Layout.navRailCollapsed
            else -> 0.dp
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Every screen is painted with the theme background; screens that use a
                    // Scaffold paint it themselves, the others (history, downloads, ...) rely
                    // on this one so they never show the bright default window background.
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = contentStartPadding)
            ) {
                content()
            }

            if (showMiniPlayer) {
                MiniPlayer(
                    controller = controller,
                    visible = true,
                    onClick = {
                        val state = controller.state.value
                        if (state.webUrl.isNotEmpty()) {
                            onNavigate(Destination.Stream(state.serviceId, state.webUrl))
                        }
                    },
                    onExpand = {
                        val state = controller.state.value
                        if (state.webUrl.isNotEmpty()) {
                            if (!tv) playerUiState.setFullscreen(true)
                            onNavigate(Destination.Stream(state.serviceId, state.webUrl))
                        }
                    },
                    onClose = { controller.clear() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(2f)
                )
            }

            // The download options for a download requested directly on a video card: the stream
            // details are loaded first, the dialog opens without the watch page ever being shown.
            val pendingDownload by StreamIntents.pendingDownload.collectAsState()
            var downloadOptions by remember { mutableStateOf<StreamDetails?>(null) }
            LaunchedEffect(pendingDownload) {
                val request = pendingDownload ?: return@LaunchedEffect
                runCatching {
                    contentRepository.streamDetails(request.serviceId, request.url)
                }.onSuccess { details ->
                    downloadOptions = details
                }.onFailure { failure ->
                    Logger.w(
                        messageString = "The download options could not be loaded",
                        throwable = failure
                    )
                }
                StreamIntents.consumeDownload()
            }

            if (pendingDownload != null && downloadOptions == null) {
                Surface(
                    shape = RoundedCornerShape(Radius.lg),
                    color = Glass.color(0.95f),
                    border = Glass.borderStroke(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(4f)
                ) {
                    Text(
                        text = "Preparing the download\u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(Spacing.lg)
                    )
                }
            }

            downloadOptions?.let { details ->
                DownloadDialog(
                    details = details,
                    item = ContentItem(
                        serviceId = details.serviceId,
                        url = details.url,
                        title = details.title,
                        type = ContentType.STREAM,
                        uploader = details.uploader,
                        uploaderUrl = details.uploaderUrl,
                        thumbnailUrl = details.thumbnailUrl,
                        durationSeconds = details.durationSeconds
                    ),
                    downloader = downloader,
                    repository = downloadRepository,
                    onResult = { result ->
                        result.fold(
                            onSuccess = { path ->
                                Logger.i(messageString = "The download finished: $path")
                            },
                            onFailure = { failure ->
                                Logger.w(
                                    messageString = "The download failed",
                                    throwable = failure
                                )
                            }
                        )
                    },
                    onDismiss = { downloadOptions = null }
                )
            }

            // On TV the rail is permanently visible; on desktop it is hover revealed.
            if (tv) {
                if (!immersive) {
                    NavigationRail(
                        currentDestination = currentDestination,
                        onNavigate = railNavigate,
                        interactionSource = railInteraction,
                        expanded = tvRailExpanded,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(if (tvRailExpanded) Layout.navRailExpanded else Layout.navRailCollapsed)
                            .fillMaxHeight()
                            .zIndex(1f)
                    )
                }
            } else {
                // A small, transparent search shortcut in the top right corner of every screen.
                if (!immersive) {
                    IconButton(
                        onClick = { onNavigate(Destination.Search) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = Spacing.xs, end = Spacing.xs)
                            .size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_search),
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (collapsedRail && !revealed) {
                    NavigationRail(
                        currentDestination = currentDestination,
                        onNavigate = railNavigate,
                        interactionSource = railInteraction,
                        expanded = false,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(Layout.navRailCollapsed)
                            .fillMaxHeight()
                            .zIndex(1f)
                    )
                }

                if (!immersive) {
                    // A narrow, invisible strip at the left window edge reveals the rail on hover.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(RAIL_EDGE_WIDTH)
                            .hoverable(edgeInteraction)
                    )

                    AnimatedVisibility(
                        visible = revealed,
                        enter = slideInHorizontally(
                            animationSpec = tween(Motion.medium),
                            initialOffsetX = { -it }
                        ) + fadeIn(animationSpec = tween(Motion.fast)),
                        exit = slideOutHorizontally(
                            animationSpec = tween(Motion.medium),
                            targetOffsetX = { -it }
                        ) + fadeOut(animationSpec = tween(Motion.fast)),
                        modifier = Modifier.zIndex(1f)
                    ) {
                        NavigationRail(
                            currentDestination = currentDestination,
                            onNavigate = railNavigate,
                            interactionSource = railInteraction,
                            expanded = true,
                            modifier = Modifier
                                .width(Layout.navRailExpanded)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationRail(
    currentDestination: Destination,
    onNavigate: (Destination) -> Unit,
    interactionSource: MutableInteractionSource,
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.hoverable(interactionSource),
        color = net.newpipe.app.ui.Glass.color(0.98f),
        shadowElevation = 8.dp,
        border = net.newpipe.app.ui.Glass.borderStroke()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.sm, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Layout.toolbarHeight)
                    .padding(horizontal = Spacing.sm),
                horizontalArrangement = if (expanded) {
                    Arrangement.Start
                } else {
                    Arrangement.Center
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_np_desktop_logo),
                        contentDescription = "NP Desktop",
                        tint = Color.Unspecified,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (expanded) {
                    Text(
                        text = "NP Desktop",
                        modifier = Modifier.padding(start = Spacing.md),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            shellEntries.forEach { entry ->
                NavigationRailItem(
                    entry = entry,
                    selected = entry.destination == currentDestination,
                    expanded = expanded,
                    onClick = { onNavigate(entry.destination) }
                )
            }
        }
    }
}

@Composable
private fun NavigationRailItem(
    entry: ShellEntry,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val background by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            hovered -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = Motion.fast),
        label = "navigation-item-background"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = Motion.fast),
        label = "navigation-item-content"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Layout.navigationItemHeight)
            .clip(RoundedCornerShape(Radius.md))
            .background(background)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = if (expanded) Spacing.md else 0.dp),
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(entry.icon),
            contentDescription = entry.label,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )

        if (expanded) {
            Text(
                text = entry.label,
                modifier = Modifier.padding(start = Spacing.md),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

/** How long the pointer may leave the rail before it hides. */
private const val RAIL_HIDE_DELAY_MS = 1200L

/** Width of the invisible hover strip at the left window edge. */
private val RAIL_EDGE_WIDTH = 10.dp
