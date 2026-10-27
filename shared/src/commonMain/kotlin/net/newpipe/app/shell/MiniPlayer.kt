/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.newpipe.app.composable.QueuePanel
import net.newpipe.app.composable.VideoSurface
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_close
import newpipe.shared.generated.resources.ic_fullscreen
import newpipe.shared.generated.resources.ic_pause
import newpipe.shared.generated.resources.ic_play_arrow
import newpipe.shared.generated.resources.ic_playlist_add
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * The small player that stays open while the user browses other sections of the app.
 *
 * The mini player renders the frames of the shared playback controller, so the video keeps
 * playing without a restart when the user leaves the watch page: only the surface the frames
 * are painted onto changes. The bar is styled like frosted glass (a translucent, lightly
 * tinted surface with a thin bright border) and shows only the video frame together with
 * compact controls in one row.
 *
 * @param controller the shared playback controller
 * @param visible whether the player should be shown; the shell hides it on screens that host a
 * large player of their own
 * @param onClick invoked when the video area or the title is clicked, to open the full watch page
 * @param onExpand invoked when the expand button is pressed; resumes the fullscreen main player
 * @param onClose invoked when the user closes the mini player
 */
@Composable
fun MiniPlayer(
    controller: PlaybackController,
    visible: Boolean,
    onClick: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    queueRepository: net.newpipe.app.service.QueueRepository = koinInject(),
    navigator: net.newpipe.app.navigation.Navigator = koinInject()
) {
    val state by controller.state.collectAsState()
    val queueCount by queueRepository.queue.collectAsState()
    var showQueue by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible && state.title.isNotEmpty(),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            shape = MINI_SHAPE,
            color = MINI_GLASS,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MINI_HEIGHT)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.06f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // The video itself, keeps rendering the shared controller frames.
                Box(
                    modifier = Modifier
                        .width(MINI_VIDEO_WIDTH)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(Color.Black)
                        .clickable(onClick = onClick)
                ) {
                    VideoSurface(
                        controller = controller,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onClick)
                        .padding(vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically)
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = formatMiniTime(state.positionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (state.durationMs > 0) {
                            Text(
                                text = "/ " + formatMiniTime(state.durationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        state.qualityLabel?.let { quality ->
                            Text(
                                text = "\u00B7 $quality",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = {
                            (state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1L))
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { showQueue = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_playlist_add),
                        contentDescription = "Open play queue",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    if (queueCount.isNotEmpty()) {
                        Text(
                            text = "${queueCount.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (state.playing) controller.pause() else controller.resume()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = if (state.playing) {
                            painterResource(Res.drawable.ic_pause)
                        } else {
                            painterResource(Res.drawable.ic_play_arrow)
                        },
                        contentDescription = if (state.playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onExpand,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fullscreen),
                        contentDescription = "Open fullscreen player",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = "Close mini player",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showQueue) {
        QueuePanel(
            queueRepository = queueRepository,
            onDismiss = { showQueue = false },
            navigationDispatcher = { item ->
                navigator.navigateTo(
                    net.newpipe.app.navigation.Destination.Stream(item.serviceId, item.url)
                )
            }
        )
    }
}

private val MINI_HEIGHT = 104.dp
private val MINI_VIDEO_WIDTH = 176.dp
private val MINI_SHAPE: Shape = RoundedCornerShape(Radius.lg)

private val MINI_GLASS: Color
    @Composable get() = MaterialTheme.colorScheme.surface.copy(
        // High alpha keeps the title, timestamps and controls readable even while bright
        // thumbnails scroll behind the glass bar.
        alpha = 0.92f
    )

private fun formatMiniTime(ms: Long): String {
    val totalSeconds = ms / 1_000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
