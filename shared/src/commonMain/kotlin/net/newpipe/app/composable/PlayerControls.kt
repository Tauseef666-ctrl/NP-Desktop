/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.ui.Spacing
import net.newpipe.app.util.formatDuration
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_close
import newpipe.shared.generated.resources.ic_fullscreen
import newpipe.shared.generated.resources.ic_fullscreen_exit
import newpipe.shared.generated.resources.ic_pause
import newpipe.shared.generated.resources.ic_play_arrow
import newpipe.shared.generated.resources.ic_skip_next
import newpipe.shared.generated.resources.ic_skip_previous
import newpipe.shared.generated.resources.ic_volume_off
import newpipe.shared.generated.resources.ic_volume_up
import org.jetbrains.compose.resources.painterResource

/**
 * The controls that are drawn over the video area.
 *
 * The layout follows the Android player: the speed can be changed with a slider and with buttons
 * that step by a quarter, the transport contains previous, play/pause, next and the skip buttons,
 * and the seek bar shows the position and the duration.
 *
 * @param controller the controller that is controlled
 * @param immersive whether the player currently uses the whole window
 * @param onToggleImmersive invoked when the user switches the immersive mode, or `null` to hide
 * the button
 * @param onClose invoked when the user closes the player, or `null` to hide the button
 * @param onNext invoked when the user skips to the next stream, or `null` to disable the button
 * @param onPrevious invoked when the user goes back to the previous stream, or `null` to disable
 * the button
 */
@Composable
fun PlayerControls(
    controller: PlaybackController,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
    onToggleImmersive: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null
) {
    val state by controller.state.collectAsState()

    var seekPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(state.positionMs) {
        if (!isSeeking) {
            seekPosition = state.positionMs.toFloat()
        }
    }

    val durationMs = state.durationMs.coerceAtLeast(1L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                )
            )
            .padding(top = Spacing.md, start = Spacing.sm, end = Spacing.sm, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDuration(seekPosition.toLong() / 1000),
                color = PlayerForeground,
                style = MaterialTheme.typography.bodySmall
            )

            Slider(
                value = seekPosition.coerceIn(0f, durationMs.toFloat()),
                onValueChange = {
                    seekPosition = it
                    isSeeking = true
                },
                onValueChangeFinished = {
                    isSeeking = false
                    controller.seekTo(seekPosition.toLong())
                },
                valueRange = 0f..durationMs.toFloat(),
                colors = playerSliderColors(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.sm)
            )

            Text(
                text = formatDuration(state.durationMs / 1000),
                color = PlayerForeground,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            IconButton(
                onClick = { onPrevious?.invoke() },
                enabled = onPrevious != null
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_skip_previous),
                    contentDescription = "Previous stream",
                    tint = PlayerForeground
                )
            }

            TextButton(
                onClick = {
                    controller.seekTo((state.positionMs - SKIP_STEP_MS).coerceAtLeast(0L))
                }
            ) {
                Text("-10s", color = PlayerForeground)
            }

            IconButton(
                onClick = {
                    if (state.playing) controller.pause() else controller.resume()
                }
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = PlayerForeground,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(
                            if (state.playing) Res.drawable.ic_pause else Res.drawable.ic_play_arrow
                        ),
                        contentDescription = if (state.playing) "Pause" else "Play",
                        tint = PlayerForeground,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            TextButton(
                onClick = {
                    controller.seekTo(
                        (state.positionMs + SKIP_STEP_MS).coerceAtMost(state.durationMs)
                    )
                }
            ) {
                Text("+10s", color = PlayerForeground)
            }

            IconButton(
                onClick = { onNext?.invoke() },
                enabled = onNext != null
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_skip_next),
                    contentDescription = "Next stream",
                    tint = PlayerForeground
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { controller.setMuted(!state.muted) }) {
                Icon(
                    painter = painterResource(
                        if (state.muted) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up
                    ),
                    contentDescription = if (state.muted) "Unmute" else "Mute",
                    tint = PlayerForeground
                )
            }

            Slider(
                value = state.volume.toFloat(),
                onValueChange = { controller.setVolume(it.toInt()) },
                valueRange = 0f..100f,
                colors = playerSliderColors(),
                modifier = Modifier.width(96.dp)
            )

            if (onToggleImmersive != null) {
                IconButton(onClick = onToggleImmersive) {
                    Icon(
                        painter = painterResource(
                            if (immersive) {
                                Res.drawable.ic_fullscreen_exit
                            } else {
                                Res.drawable.ic_fullscreen
                            }
                        ),
                        contentDescription = if (immersive) {
                            "Exit full screen"
                        } else {
                            "Full screen"
                        },
                        tint = PlayerForeground
                    )
                }
            }

            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = "Close player",
                        tint = PlayerForeground
                    )
                }
            }
        }

        val error = state.error
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = PlayerError
            )
        }
    }
}

/**
 * The amount the skip buttons jump.
 */
private const val SKIP_STEP_MS = 10_000L

private val PlayerForeground = Color(0xFFF5F5F5)
private val PlayerError = Color(0xFFFF6B6B)

@Composable
private fun playerSliderColors() = SliderDefaults.colors(
    thumbColor = PlayerForeground,
    activeTrackColor = PlayerForeground,
    inactiveTrackColor = PlayerForeground.copy(alpha = 0.3f)
)
