/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import net.newpipe.app.navigation.Destination
import net.newpipe.app.player.PlayerWindow
import net.newpipe.app.service.createPlaybackController

/**
 * Entry point for compose-related UI components on Desktop
 */
fun main() = application {
    // The player is created up front, so that the video output is ready as soon as the user
    // starts a playback, and so that the player window can be shown outside of the main window.
    val playbackController = remember { createPlaybackController() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "NewPipe",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        App(
            startDestination = Destination.Search,
            onCloseRequest = ::exitApplication,
            playbackController = playbackController
        )
    }

    PlayerWindow(playbackController)
}
