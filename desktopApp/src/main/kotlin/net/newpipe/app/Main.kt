/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.PreferencesSettings
import java.awt.Dimension
import java.util.prefs.Preferences
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import net.newpipe.app.navigation.Destination
import net.newpipe.app.platform.DesktopMiniPlayerBridge
import net.newpipe.app.platform.LocalWorkspaceMode
import net.newpipe.app.platform.LocalWorkspaceModeChange
import net.newpipe.app.platform.WindowBounds
import net.newpipe.app.platform.WorkspaceMode
import net.newpipe.app.platform.WorkspacePreferences
import net.newpipe.app.player.BackgroundPlaybackController
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlayerAction
import net.newpipe.app.player.PlayerShortcuts
import net.newpipe.app.player.ShortcutResolver
import net.newpipe.app.player.plusAppShortcuts
import org.koin.compose.koinInject

/**
 * Window icon in the NewPipe+ style: a near-black rounded tile with a red play glyph.
 * Built as an [ImageVector] so the desktop app needs no bundled raster asset.
 */
private val AppIcon: ImageVector = ImageVector.Builder(
    name = "ic_app_logo",
    defaultWidth = 108.dp,
    defaultHeight = 108.dp,
    viewportWidth = 108f,
    viewportHeight = 108f
).apply {
    path(fill = SolidColor(Color(0xFF0F0F12))) {
        moveTo(28f, 4f)
        horizontalLineTo(80f)
        quadTo(104f, 4f, 104f, 28f)
        verticalLineTo(80f)
        quadTo(104f, 104f, 80f, 104f)
        horizontalLineTo(28f)
        quadTo(4f, 104f, 4f, 80f)
        verticalLineTo(28f)
        quadTo(4f, 4f, 28f, 4f)
        close()
    }
    path(fill = SolidColor(Color(0xFFE53935))) {
        moveTo(78f, 55f)
        lineTo(40f, 33f)
        lineTo(40f, 77f)
        close()
    }
}.build()

/**
 * Installs the app-wide keyboard-shortcut scope (F7) around the whole window content and routes
 * the claimed transport actions to the shared [PlaybackController].
 *
 * Consumption order: [plusAppShortcuts] uses `onKeyEvent` (bubble phase) while the player screen
 * consumes its keys with `onPreviewKeyEvent` (preview phase). On the player screen the focused
 * descendant handles the key first and consumes it before bubbling reaches this ancestor, so the
 * player and app scopes never double-fire. Inside a focused text field the field consumes Space and
 * typing first, which is the intended honest guard.
 *
 * Only acts while something is queued (`queueState.current != null`). Seeks use the default step:
 * the app-wide callback carries no modifier state, so Shift's large-step remains player-screen only.
 */
@Composable
private fun AppShortcutHost(content: @Composable () -> Unit) {
    val resolver = remember { ShortcutResolver() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .plusAppShortcuts(resolver) { action ->
                val controller = DesktopMiniPlayerBridge.controller.value
                    ?: return@plusAppShortcuts
                if (controller.queueState.value.current == null) return@plusAppShortcuts
                when (action) {
                    PlayerAction.PLAY_PAUSE -> controller.togglePlayPause()
                    PlayerAction.STOP -> controller.stop()
                    PlayerAction.NEXT -> controller.skipToNext()
                    PlayerAction.PREVIOUS -> controller.skipToPrevious()
                    PlayerAction.SEEK_FORWARD -> controller.seekTo(
                        controller.state.value.positionMs +
                            PlayerShortcuts.seekStepMillis(isShiftPressed = false)
                    )
                    PlayerAction.SEEK_BACKWARD -> controller.seekTo(
                        (controller.state.value.positionMs -
                            PlayerShortcuts.seekStepMillis(isShiftPressed = false))
                            .coerceAtLeast(0L)
                    )
                    else -> Unit
                }
            }
    ) {
        content()
    }
}

/**
 * Entry point for compose-related UI components on Desktop
 */
fun main() = application {
    val isMiniPlayerOpen by DesktopMiniPlayerBridge.isOpen.collectAsState()

    // Restore the saved workspace once; any failure falls back to defaults.
    val workspace = runCatching {
        WorkspacePreferences(
            settings = PreferencesSettings(Preferences.userNodeForPackage(WorkspacePreferences::class.java)),
            json = Json
        )
    }.getOrNull()
    val savedBounds = workspace?.windowBounds()?.takeIf { it.width >= 400 && it.height >= 300 }
    val windowState = rememberWindowState(
        position = savedBounds?.let { WindowPosition(it.x.dp, it.y.dp) }
            ?: WindowPosition(Alignment.Center),
        size = savedBounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1280.dp, 800.dp)
    )

    Window(
        onCloseRequest = {
            workspace?.let {
                runCatching {
                    it.setWindowBounds(
                        WindowBounds(
                            x = windowState.position.x.value.roundToInt(),
                            y = windowState.position.y.value.roundToInt(),
                            width = windowState.size.width.value.roundToInt(),
                            height = windowState.size.height.value.roundToInt()
                        )
                    )
                }
            }
            val background = DesktopMiniPlayerBridge.background.value
            val keepRunning = background?.onCloseRequested() ?: false
            if (keepRunning) {
                windowState.isMinimized = true
            } else {
                exitApplication()
            }
        },
        title = "YT Desktop",
        icon = rememberVectorPainter(AppIcon),
        state = windowState
    ) {
        val isMinimized = windowState.isMinimized
        LaunchedEffect(isMinimized) {
            if (!isMinimized) DesktopMiniPlayerBridge.background.value?.returnToForeground()
        }
        var workspaceMode by remember {
            mutableStateOf(workspace?.mode() ?: WorkspaceMode.BALANCED)
        }
        CompositionLocalProvider(
            LocalWorkspaceMode provides workspaceMode,
            LocalWorkspaceModeChange provides { changed ->
                workspaceMode = changed
                workspace?.setMode(changed)
            }
        ) {
            AppShortcutHost {
                App(
                    startDestination = Destination.Home,
                    onCloseRequest = ::exitApplication,
                    withKoin = {
                        // Share the same singleton controller with the floating mini-player window.
                        val playback: PlaybackController = koinInject()
                        LaunchedEffect(playback) { DesktopMiniPlayerBridge.controller.value = playback }
                        val background: BackgroundPlaybackController = koinInject()
                        LaunchedEffect(background) { DesktopMiniPlayerBridge.background.value = background }
                    }
                )
            }
        }
    }

    if (isMiniPlayerOpen) {
        val savedMini = workspace?.miniPlayerBounds()?.takeIf { it.width >= 220 && it.height >= 80 }
        var miniCompact by remember { mutableStateOf(workspace?.miniPlayerCompact() ?: false) }
        val miniState = rememberWindowState(
            position = savedMini?.let { WindowPosition(it.x.dp, it.y.dp) }
                ?: WindowPosition(Alignment.BottomEnd),
            size = savedMini?.let { DpSize(it.width.dp, it.height.dp) }
                ?: DpSize(if (miniCompact) 300.dp else 420.dp, if (miniCompact) 84.dp else 160.dp)
        )
        // Persist the mini-player bounds on any close path (window X or the in-app Close button).
        DisposableEffect(Unit) {
            onDispose {
                workspace?.let {
                    runCatching {
                        it.setMiniPlayerBounds(
                            WindowBounds(
                                x = miniState.position.x.value.roundToInt(),
                                y = miniState.position.y.value.roundToInt(),
                                width = miniState.size.width.value.roundToInt(),
                                height = miniState.size.height.value.roundToInt()
                            )
                        )
                    }
                }
            }
        }
        Window(
            title = "YT Desktop - Mini player",
            icon = rememberVectorPainter(AppIcon),
            alwaysOnTop = true,
            resizable = true,
            state = miniState,
            onCloseRequest = { DesktopMiniPlayerBridge.toggle() }
        ) {
            window.minimumSize = Dimension(220, 72)
            MiniPlayerWindow(
                compact = miniCompact,
                onToggleCompact = {
                    miniCompact = !miniCompact
                    workspace?.setMiniPlayerCompact(miniCompact)
                    miniState.size = if (miniCompact) {
                        DpSize(300.dp, 84.dp)
                    } else {
                        DpSize(420.dp, 160.dp)
                    }
                }
            )
        }
    }
}