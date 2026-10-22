/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PlaybackState
import net.newpipe.app.service.VideoSurfaceProvider
import net.newpipe.app.util.formatDuration

/**
 * The independent window that shows the video and the playback controls.
 *
 * The window is only shown while something is playing. Closing it stops the playback.
 */
@Composable
fun PlayerWindow(controller: PlaybackController) {
    val state by controller.state.collectAsState()
    val visible = state.title.isNotEmpty()

    Window(
        onCloseRequest = { controller.stop() },
        title = if (state.title.isEmpty()) "NewPipe Player" else state.title,
        state = rememberWindowState(width = 960.dp, height = 560.dp),
        visible = visible
    ) {
        if (visible) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoOutput(
                    controller = controller,
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                PlayerControls(
                    controller = controller,
                    state = state
                )
            }
        }
    }
}

/**
 * The video surface of the player.
 */
@Composable
private fun VideoOutput(
    controller: PlaybackController,
    state: PlaybackState,
    modifier: Modifier = Modifier
) {
    val surface = controller as? VideoSurfaceProvider
    val videoComponent = surface?.videoComponent

    if (videoComponent != null) {
        SwingPanel(
            factory = { videoComponent },
            modifier = modifier
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = state.error ?: "No video output is available",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * The transport controls of the player.
 */
@Composable
private fun PlayerControls(
    controller: PlaybackController,
    state: PlaybackState
) {
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(state.positionMs) {
        if (!isSeeking) {
            seekPosition = state.positionMs.toFloat()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1C1C))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Slider(
            value = seekPosition,
            onValueChange = {
                seekPosition = it
                isSeeking = true
            },
            onValueChangeFinished = {
                isSeeking = false
                controller.seekTo(seekPosition.toLong())
            },
            valueRange = 0f..(state.durationMs.coerceAtLeast(1L).toFloat()),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = {
                if (state.playing) controller.pause() else controller.resume()
            }) {
                Text(text = if (state.playing) "❚❚" else "▶")
            }

            Text(
                text = "${formatDuration(seekPosition.toLong() / 1000)} / " +
                    formatDuration(state.durationMs / 1000),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )

            Box(modifier = Modifier.weight(1f))

            SpeedSelector(
                speed = state.speed,
                onSelect = { controller.setSpeed(it) }
            )

            Slider(
                value = state.volume.toFloat(),
                onValueChange = { controller.setVolume(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.width(120.dp)
            )

            IconButton(onClick = { controller.setMuted(!state.muted) }) {
                Text(text = if (state.muted) "MUTE" else "SOUND")
            }
        }

        val error = state.error
        if (error != null) {
            Text(
                text = error,
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * A dropdown that lets the user pick the playback speed.
 */
@Composable
private fun SpeedSelector(
    speed: Float,
    onSelect: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(text = "${speed}x", color = Color.White)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SPEEDS.forEach { value ->
                DropdownMenuItem(
                    text = { Text("${value}x") },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

/**
 * The playback speeds the user can choose from.
 */
private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
