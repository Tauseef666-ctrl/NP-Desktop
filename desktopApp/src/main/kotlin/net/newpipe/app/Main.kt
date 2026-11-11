/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import net.newpipe.app.ui.PlayerShortcutDispatcher
import net.newpipe.app.service.UpdateService
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.File
import net.newpipe.app.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.app.composable.VideoPlayer
import net.newpipe.app.navigation.Destination
import net.newpipe.app.platform.PopupWindowBounds
import net.newpipe.app.platform.applyDarkTitlebarToWindow
import net.newpipe.app.platform.loadPopupWindowBounds
import net.newpipe.app.platform.savePopupWindowBounds
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PopupPlayer
import net.newpipe.app.service.createPlaybackController
import net.newpipe.app.ui.PlayerUiState
import javax.imageio.ImageIO

/**
 * Entry point for compose-related UI components on Desktop
 */
fun main() {
    // Everything the app logs (extractor refusals, player errors, the updater) is written into
    // `%APPDATA%\NP-Desktop\logs\npdesktop.log`, so failures of the installed build can be
    // diagnosed afterwards.
    runCatching { net.newpipe.app.platform.AppFileLogger.install() }

    // Hand the packaged version into the in-app updater: the updater compares its release
    // lookup against this constant. Keep it in sync with `packageVersion` in the Gradle file.
    UpdateService.initialize(DesktopVersions.APP_VERSION)

    // A generous image cache: thumbnails are served from memory on revisit and from disk after
    // a restart, so scrolling through the feed or reopening the app no longer refetches every
    // picture.
    val cacheRoot = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "NP-Desktop")
    val memory = MemoryCache.Builder()
        .maxSizeBytes(Runtime.getRuntime().maxMemory() / 4)
        .build()
    val disk = DiskCache.Builder()
        .directory(File(cacheRoot, "image-cache"))
        .maxSizeBytes(256L * 1024 * 1024)
        .build()
    application {
    // A generous image cache serves thumbnails from memory on revisit and from disk after a
    // restart, so scrolling through the feed or reopening the app no longer refetches every
    // picture.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).memoryCache(memory).diskCache(disk).build()
    }

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
        // Prepare libVLC in the background while the user browses, so the first video starts
        // playing instantly.
        withContext(Dispatchers.Default) { created.warmUp() }
        // Warm up the extraction engine as well: initializing the downloader and the service
        // registry is paid here instead of on the user's first click, so the home grid and
        // the first stream open sooner.
        withContext(Dispatchers.Default) {
            // A failure is harmless: the home screen load path initialises the engine on
            // demand anyway.
            runCatching {
                net.newpipe.app.service.createServiceRegistry().services()
            }
        }
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
            // Black window chrome on Windows so the title bar matches the app's dark design.
            applyDarkTitlebarToWindow(awt)
        }
        // The fullscreen player hides the mouse pointer with the controls after 3 seconds
        // without movement. An invisible custom cursor does that; any real pointer movement
        // flips the flag back and the normal cursor returns.
        val hideCursor by playerUiState.hideCursor.collectAsState()
        val blankCursor = remember {
            runCatching {
                val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "NP hidden pointer")
            }.getOrNull()
        }
        LaunchedEffect(hideCursor, blankCursor) {
            val awt = window
            awt.cursor = when {
                hideCursor && blankCursor != null -> blankCursor
                else -> Cursor.getDefaultCursor()
            }
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
        val popupState = remember { popupWindowState() }
        Window(
            onCloseRequest = {
                // Closing the popup returns the video to the main player with the playback
                // running: the shared controller keeps playing, only the extra window goes.
                persistPopupWindowState(popupState)
                PopupPlayer.close()
            },
            title = state.title.ifEmpty { "NP Desktop" },
            state = popupState,
            alwaysOnTop = true,
        ) {
            val popupAwt = window
            SideEffect {
                windowIcon?.let { popupAwt.setIconImage(it) }
                // The popup title bar carries the running video name; apply the dark chrome
                // directly on the window so unicode titles cannot break the lookup.
                applyDarkTitlebarToWindow(popupAwt)
            }
            // The title changes with every video; make sure the black chrome survives that.
            LaunchedEffect(state.title) {
                applyDarkTitlebarToWindow(popupAwt)
                kotlinx.coroutines.delay(400)
                applyDarkTitlebarToWindow(popupAwt)
            }
            // Remember where the user leaves the popup: the geometry is written into the
            // desktop settings (throttled while dragging, final write when the window closes).
            DisposableEffect(popupAwt) {
                val lastSaved = longArrayOf(0L)
                val listener = object : java.awt.event.ComponentAdapter() {
                    override fun componentMoved(event: java.awt.event.ComponentEvent) {
                        saveThrottled(popupState, lastSaved)
                    }

                    override fun componentResized(event: java.awt.event.ComponentEvent) {
                        saveThrottled(popupState, lastSaved)
                    }
                }
                popupAwt.addComponentListener(listener)
                onDispose { popupAwt.removeComponentListener(listener) }
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
                            // Same as the window close button: the video returns to the main
                            // player and the playback keeps running through the shared
                            // controller.
                            PopupPlayer.close()
                        }
                    )
                }
            }
        }
    }
}
}

/**
 * A window state for the popup player built from the geometry the user last left the window at,
 * so the next popup opens on the same place with the same size.
 */
private fun popupWindowState(): WindowState {
    val bounds = loadPopupWindowBounds()
    val onScreen = bounds?.hasPosition == true && bounds.x >= -10_000f && bounds.y >= -10_000f &&
        runCatching {
            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
            bounds.x < screen.width - 80f && bounds.y < screen.height - 80f
        }.getOrDefault(false)
    return WindowState(
        placement = if (bounds?.maximized == true) {
            WindowPlacement.Maximized
        } else {
            WindowPlacement.Floating
        },
        position = if (onScreen && bounds != null) {
            WindowPosition(Dp(bounds.x), Dp(bounds.y))
        } else {
            WindowPosition.PlatformDefault
        },
        size = DpSize(
            Dp(bounds?.width?.takeIf { it > 80f } ?: 720f),
            Dp(bounds?.height?.takeIf { it > 60f } ?: 480f)
        )
    )
}

/**
 * Writes the current geometry of the popup window state into the desktop settings.
 */
private fun persistPopupWindowState(state: WindowState) {
    runCatching {
        val size = state.size
        val position = state.position
        savePopupWindowBounds(
            PopupWindowBounds(
                x = if (position.isSpecified) position.x.value else 0f,
                y = if (position.isSpecified) position.y.value else 0f,
                width = size.width.value,
                height = size.height.value,
                maximized = state.placement == WindowPlacement.Maximized,
                hasPosition = position.isSpecified
            )
        )
    }
}

/**
 * Throttled geometry saver for the popup window: at most one write per second while the user
 * moves or resizes, so dragging stays smooth while every resting position is stored.
 */
private fun saveThrottled(state: WindowState, lastSaved: LongArray) {
    val now = System.currentTimeMillis()
    if (now - lastSaved[0] >= 800L) {
        lastSaved[0] = now
        persistPopupWindowState(state)
    }
}

