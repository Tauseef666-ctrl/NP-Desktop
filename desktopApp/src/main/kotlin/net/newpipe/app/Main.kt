/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import net.newpipe.app.composable.VideoPlayer
import net.newpipe.app.navigation.Destination
import net.newpipe.app.service.PopupPlayer
import net.newpipe.app.service.createPlaybackController

/**
 * Entry point for compose-related UI components on Desktop
 */
fun main() = application {
    // The player is created up front, so that the video output is ready as soon as the user
    // opens a stream. The video surface is embedded into the stream screen.
    val playbackController = remember { createPlaybackController() }

    val windowState = rememberWindowState(width = 1440.dp, height = 900.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "YT Desktop",
        state = windowState,
    ) {
        MaterialTheme {
            val debugStreamUrl = System.getenv("NEWPIPE_START_STREAM_URL")
            App(
                startDestination = if (debugStreamUrl != null) {
                    Destination.Stream(0, debugStreamUrl)
                } else {
                    Destination.Home
                },
                onCloseRequest = ::exitApplication,
                playbackController = playbackController
            )
        }
    }

    val popup by PopupPlayer.state.collectAsState()

    popup?.let { state ->
        Window(
            onCloseRequest = {
                PopupPlayer.close()
                playbackController.clear()
            },
            title = state.title.ifEmpty { "YT Desktop" },
            state = rememberWindowState(width = 720.dp, height = 480.dp),
            alwaysOnTop = true
        ) {
            MaterialTheme {
                VideoPlayer(
                    controller = playbackController,
                    thumbnailUrl = state.thumbnailUrl,
                    canPlay = state.canPlay,
                    onPlay = state.onPlay,
                    modifier = Modifier.fillMaxSize(),
                    videoStreams = state.videoStreams,
                    selectedVideo = state.selectedVideo,
                    onSelectVideo = state.onSelectVideo,
                    isPopupWindow = true,
                    onClose = {
                        PopupPlayer.close()
                        playbackController.clear()
                    }
                )
            }
        }
    }
}
