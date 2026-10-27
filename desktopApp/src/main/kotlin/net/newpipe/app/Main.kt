/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import net.newpipe.app.ui.PlayerShortcutDispatcher
import net.newpipe.app.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.app.composable.VideoPlayer
import net.newpipe.app.navigation.Destination
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PopupPlayer
import net.newpipe.app.service.createPlaybackController
import net.newpipe.app.ui.PlayerUiState
import javax.imageio.ImageIO

/**
 * Entry point for compose-related UI components on Desktop
 */
fun main() = application {
    // Compose `Painter` window icons are converted to an AWT window icon through a painter
    // boundary that draws with fixed insets, which throws for small icon sizes. A plain AWT
    // image is applied to the underlying windows instead.
    val windowIcon = remember {
        runCatching {
            ClassLoader.getSystemResource("np-desktop-icon.png")
                ?.let { ImageIO.read(it) }
        }.getOrNull()
    }

    val windowState = rememberWindowState(width = 1440.dp, height = 900.dp)

    // The player is created off the main thread; the loading screen stays visible until the
    // controller and its native libraries are ready, so that the home screen never freezes.
    var controller by remember { mutableStateOf<PlaybackController?>(null) }
    val playerUiState = remember { PlayerUiState() }
    val fullscreen by playerUiState.fullscreen.collectAsState()
    LaunchedEffect(Unit) {
        val created = withContext(Dispatchers.Default) { createPlaybackController() }
        controller = created
    }

    // The fullscreen flag of the player drives the real window placement of the main window.
    // `setUndecorated` throws for frames that are already displayable, so the decoration flag
    // must stay constant and only the placement is toggled (AWT handles Fullscreen safely).
    // F11 fullscreens the whole app, F (and the player button) fullscreens a playing video.
    LaunchedEffect(fullscreen) {
        playerUiState.setImmersive(fullscreen)
    }
    val appFullscreen by playerUiState.appFullscreen.collectAsState()
    LaunchedEffect(fullscreen, appFullscreen) {
        runCatching {
            if (fullscreen || appFullscreen) {
                windowState.placement = WindowPlacement.Fullscreen
            } else {
                windowState.placement = WindowPlacement.Floating
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = if (fullscreen || appFullscreen) "" else "NP Desktop",
        state = windowState,
        undecorated = false,
        transparent = false,
        onPreviewKeyEvent = { event ->
            when {
                event.type != KeyEventType.KeyDown -> false
                event.key == Key.F11 -> {
                    playerUiState.toggleAppFullscreen()
                    true
                }
                else -> PlayerShortcutDispatcher.dispatch(
                    event.key,
                    event.isShiftPressed,
                    event.isCtrlPressed
                )
            }
        }
    ) {
        SideEffect {
            val awt = window
            windowIcon?.let { awt.setIconImage(it) }
        }
        val readyController = controller
        if (readyController == null) {
            SplashScreen()
        } else {
            MaterialTheme {
                val debugStreamUrl = System.getenv("NEWPIPE_START_STREAM_URL")
                val debugDest = System.getenv("NEWPIPE_START_DEST")
                App(
                    startDestination = when {
                        debugStreamUrl != null -> Destination.Stream(0, debugStreamUrl)
                        debugDest != null -> when (debugDest) {
                            "history" -> Destination.History
                            "downloads" -> Destination.Downloads
                            "search" -> Destination.Search
                            "subscriptions" -> Destination.Subscriptions
                            "feed" -> Destination.Feed("Trending", "Trending")
                            else -> Destination.Home
                        }
                        else -> Destination.Home
                    },
                    onCloseRequest = ::exitApplication,
                    playbackController = readyController,
                    playerUiState = playerUiState
                )
            }
        }
    }

    val popup by PopupPlayer.state.collectAsState()

    popup?.let { state ->
        Window(
            onCloseRequest = {
                PopupPlayer.close()
                controller?.clear()
            },
            title = state.title.ifEmpty { "NP Desktop" },
            state = rememberWindowState(width = 720.dp, height = 480.dp),
            alwaysOnTop = true,
        ) {
            SideEffect {
                val awt = window
                windowIcon?.let { awt.setIconImage(it) }
            }
            val readyController = controller
            if (readyController == null) {
                SplashScreen()
            } else {
                MaterialTheme {
                    VideoPlayer(
                        controller = readyController,
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
                            readyController.clear()
                        }
                    )
                }
            }
        }
    }
}

