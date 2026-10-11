/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import net.newpipe.app.player.PlaybackController
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
            exitApplication()
        },
        title = "NewPipe+",
        icon = rememberVectorPainter(AppIcon),
        state = windowState
    ) {
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
            App(
                startDestination = Destination.Home,
                onCloseRequest = ::exitApplication,
                withKoin = {
                    // Share the same singleton controller with the floating mini-player window.
                    val playback: PlaybackController = koinInject()
                    LaunchedEffect(playback) { DesktopMiniPlayerBridge.controller.value = playback }
                }
            )
        }
    }

    if (isMiniPlayerOpen) {
        Window(
            title = "Mini player",
            icon = rememberVectorPainter(AppIcon),
            alwaysOnTop = true,
            state = rememberWindowState(size = DpSize(420.dp, 160.dp)),
            onCloseRequest = { DesktopMiniPlayerBridge.toggle() }
        ) {
            MiniPlayerWindow()
        }
    }
}