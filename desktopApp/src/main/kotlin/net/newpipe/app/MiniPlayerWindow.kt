/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackStatus
import net.newpipe.app.platform.DesktopMiniPlayerBridge
import net.newpipe.app.theme.AppTheme
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.util.formatDuration

/**
 * Compact always-on-top companion window (F1). It drives the SAME shared [PlaybackController] as
 * the main player, so transport/queue state is identical in both places.
 *
 * The window is user-resizable and user-repositionable; its bounds are persisted by the host
 * (desktopApp `Main.kt`). [compact] collapses the surface to a single transport row (title +
 * play/pause + expand/close) for a minimal, unobtrusive footprint.
 *
 * Honest note: the VLCJ video surface is embedded in the main window and cannot be re-parented, so
 * this window is audio/control focused — video keeps rendering in the full player.
 */
@Composable
fun MiniPlayerWindow(
    compact: Boolean = false,
    onToggleCompact: () -> Unit = {}
) {
    AppTheme {
        val controller by DesktopMiniPlayerBridge.controller.collectAsState()
        val current = controller
        if (current == null) {
            EmptyMiniPlayer()
        } else {
            MiniPlayerContent(current, compact, onToggleCompact)
        }
    }
}

@Composable
private fun EmptyMiniPlayer() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(spaceLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Nothing is playing yet. Start a video in the Player screen.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun MiniPlayerContent(
    playback: PlaybackController,
    compact: Boolean,
    onToggleCompact: () -> Unit
) {
    val state by playback.state.collectAsState()
    val queue by playback.queueState.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(spaceNormal)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = queue.current?.title ?: "No media loaded",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = plusColors().textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onToggleCompact,
                    modifier = Modifier.width(84.dp)
                ) {
                    Text(if (compact) "Expand" else "Compact")
                }
                TextButton(
                    onClick = { DesktopMiniPlayerBridge.toggle() },
                    modifier = Modifier.width(72.dp)
                ) {
                    Text("Close")
                }
            }
            if (compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { playback.togglePlayPause() },
                        enabled = playback.isEngineAvailable
                    ) { Text(if (state.isPlaying) "Pause" else "Play") }
                }
                return@Column
            }
            Text(
                text = playbackStatusLabel(state.status),
                style = MaterialTheme.typography.labelSmall,
                color = plusColors().textSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val duration = state.durationMs.coerceAtLeast(0L)
                val canSeek = playback.isEngineAvailable && duration > 0L
                var scrubbing by remember { mutableStateOf(false) }
                var scrubPosition by remember { mutableStateOf(0f) }
                val shownPosition = if (scrubbing) scrubPosition else state.positionMs.toFloat()
                val rangeEnd = if (duration > 0L) duration.toFloat() else 1f

                Text(
                    text = formatDuration(shownPosition.toLong() / 1000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = plusColors().textMuted
                )
                Slider(
                    value = shownPosition.coerceIn(0f, rangeEnd),
                    onValueChange = {
                        scrubbing = true
                        scrubPosition = it
                    },
                    onValueChangeFinished = {
                        if (canSeek) playback.seekTo(scrubPosition.toLong())
                        scrubbing = false
                    },
                    valueRange = 0f..rangeEnd,
                    enabled = canSeek,
                    modifier = Modifier.weight(1f).padding(horizontal = spaceSmall)
                )
                Text(
                    text = formatDuration(if (duration > 0L) duration / 1000L else 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = plusColors().textMuted
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { playback.skipToPrevious() },
                    enabled = playback.isEngineAvailable
                ) { Text("Prev") }
                Button(
                    onClick = { playback.togglePlayPause() },
                    enabled = playback.isEngineAvailable,
                    modifier = Modifier.padding(horizontal = spaceSmall)
                ) { Text(if (state.isPlaying) "Pause" else "Play") }
                TextButton(
                    onClick = { playback.skipToNext() },
                    enabled = playback.isEngineAvailable
                ) { Text("Next") }
                TextButton(
                    onClick = { playback.setMuted(!state.isMuted) },
                    enabled = playback.isEngineAvailable
                ) { Text(if (state.isMuted) "Unmute" else "Mute") }
            }
        }
}

private fun playbackStatusLabel(status: PlaybackStatus): String = when (status) {
    PlaybackStatus.IDLE -> "Idle"
    PlaybackStatus.LOADING -> "Loading"
    PlaybackStatus.READY -> "Ready"
    PlaybackStatus.PLAYING -> "Playing"
    PlaybackStatus.PAUSED -> "Paused"
    PlaybackStatus.ENDED -> "Ended"
    PlaybackStatus.ERROR -> "Error"
}
