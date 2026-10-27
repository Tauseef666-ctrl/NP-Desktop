/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preferences.VideoAudioPreferences
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PopupPlayer
import com.russhwolf.settings.Settings
import org.koin.compose.koinInject

/**
 * Actions that only the current screen can provide, such as the next related video.
 */
object PlayerShortcutHooks {
    var next: (() -> Unit)? = null
    var previous: (() -> Unit)? = null
    var togglePopup: (() -> Unit)? = null
    var nextChapter: (() -> Unit)? = null
    var previousChapter: (() -> Unit)? = null
    var toggleCaptions: (() -> Unit)? = null
    var cycleCaptionBrightness: (() -> Unit)? = null
    var cycleCaptionBackground: (() -> Unit)? = null
    var increaseCaptionSize: (() -> Unit)? = null
    var decreaseCaptionSize: (() -> Unit)? = null
    var showShortcuts: (() -> Unit)? = null

    /**
     * The seek step used by the seek shortcuts, taken from the Video & Audio settings.
     */
    var seekDurationMs: Long = DEFAULT_SEEK_DURATION_MS

    private const val DEFAULT_SEEK_DURATION_MS = 10_000L
}

/**
 * Tracks whether a text field has keyboard focus, so shortcut handling can stay out of the way
 * while the user is typing.
 */
object TextFieldFocusTracker {
    @Volatile
    var textFieldFocused: Boolean = false
}

/**
 * Bridges window level keyboard events of the platform shell to the shortcut logic that lives
 * inside the composition. On the desktop the players of the shell do not necessarily hold input
 * focus, so the window forwards every key press here and the handler that the shortcuts installed
 * decides whether the key means something.
 */
object PlayerShortcutDispatcher {
    @Volatile
    var handler: ((Key, Boolean, Boolean) -> Boolean)? = null

    fun dispatch(key: Key, shift: Boolean, ctrl: Boolean): Boolean =
        handler?.invoke(key, shift, ctrl) ?: false
}

/**
 * Attach to a text field's modifier chain so keyboard shortcuts do not fight the editor.
 */
fun Modifier.trackTextFieldFocus(): Modifier = onFocusChanged {
    TextFieldFocusTracker.textFieldFocused = it.isFocused
}

/**
 * Adds the YouTube desktop keyboard shortcuts to the application.
 *
 * The events are observed in the preview phase at the root of the composition, so every key press
 * reaches the handler no matter which inner element currently holds focus. A focused text field
 * keeps its keys and typing a search query or a playlist name is not disturbed.
 *
 * Shortcuts: `k`/space play or pause, `j`/`l` seek 10 s, arrow keys seek 5 s or change the volume,
 * `m` mute, `f` full screen, `t` theatre mode (immersive player), `i` popup player, `,`/`.` and
 * `shift`+`,`/`>` change the speed, `0`-`9` seek to a tenth of the video, home/end seek to the
 * start/end, `/` opens the search, `shift+n` and `shift+p` switch to the next and previous video
 * and escape leaves the immersive player or closes the popup.
 *
 * @param modifier the modifier of the container
 * @param content the application content
 */
@Composable
fun PlayerShortcuts(
    modifier: Modifier = Modifier,
    controller: PlaybackController = koinInject(),
    playerUiState: PlayerUiState = koinInject(),
    navigator: Navigator = koinInject(),
    content: @Composable () -> Unit
) {
    val settings: Settings = koinInject()
    LaunchedEffect(Unit) {
        val stored = settings.getString(
            VideoAudioPreferences.KEY_SEEK_DURATION,
            VideoAudioPreferences.DEFAULT_SEEK_DURATION_MS
        ).toLongOrNull()
        if (stored != null && stored > 0) {
            PlayerShortcutHooks.seekDurationMs = stored
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (TextFieldFocusTracker.textFieldFocused) {
                    if (event.key != Key.Escape) return@onPreviewKeyEvent false
                }
                handleShortcut(event.key, event.isShiftPressed, event.isCtrlPressed, controller, playerUiState, navigator)
            }
    ) {
        DisposableEffect(Unit) {
            PlayerShortcutDispatcher.handler = { key, shift, ctrl ->
                if (TextFieldFocusTracker.textFieldFocused && key != Key.Escape) {
                    false
                } else {
                    handleShortcut(key, shift, ctrl, controller, playerUiState, navigator)
                }
            }
            onDispose { PlayerShortcutDispatcher.handler = null }
        }
        content()
    }
}
/**
 * Handles a single key press and returns whether it was consumed.
 */
fun handlePlayerShortcut(
    key: Key,
    shift: Boolean,
    ctrl: Boolean,
    controller: PlaybackController,
    playerUiState: PlayerUiState,
    navigator: Navigator
): Boolean = handleShortcut(key, shift, ctrl, controller, playerUiState, navigator)

@Composable
fun rememberPlayerShortcutHandler(
    controller: PlaybackController = koinInject(),
    playerUiState: PlayerUiState = koinInject(),
    navigator: Navigator = koinInject()
): (androidx.compose.ui.input.key.KeyEvent) -> Boolean {
    val settings: Settings = koinInject()
    LaunchedEffect(Unit) {
        val stored = settings
            .getString(
                VideoAudioPreferences.KEY_SEEK_DURATION,
                VideoAudioPreferences.DEFAULT_SEEK_DURATION_MS
            )
            .toLongOrNull()
        if (stored != null && stored > 0) {
            PlayerShortcutHooks.seekDurationMs = stored
        }
    }
    return { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else if (TextFieldFocusTracker.textFieldFocused && event.key != Key.Escape) {
            false
        } else {
            handleShortcut(event.key, event.isShiftPressed, event.isCtrlPressed, controller, playerUiState, navigator)
        }
    }
}

/**
 * Handles a single key press and returns whether it was consumed.
 */
fun handleShortcut(
    key: Key,
    shift: Boolean,
    ctrl: Boolean,
    controller: PlaybackController,
    playerUiState: PlayerUiState,
    navigator: Navigator
): Boolean {
    val state = controller.state.value
    val hasMedia = state.title.isNotEmpty()

    if (key == Key.Slash) {
        if (shift) {
            PlayerShortcutHooks.showShortcuts?.invoke()
        } else {
            navigator.switchTo(Destination.Search)
        }
        return true
    }

    if (key == Key.Escape) {
        if (playerUiState.fullscreen.value) {
            playerUiState.setFullscreen(false)
            return true
        }
        if (playerUiState.appFullscreen.value) {
            playerUiState.setAppFullscreen(false)
            return true
        }
        if (playerUiState.immersive.value) {
            playerUiState.setImmersive(false)
            return true
        }
        if (PopupPlayer.state.value != null) {
            PopupPlayer.close()
            return true
        }
        return false
    }

    if (ctrl) {
        when (key) {
            Key.DirectionRight -> {
                PlayerShortcutHooks.nextChapter?.invoke()
                return true
            }
            Key.DirectionLeft -> {
                PlayerShortcutHooks.previousChapter?.invoke()
                return true
            }
        }
    }

    if (!hasMedia) return false

    if (shift) {
        when (key) {
            Key.N -> {
                PlayerShortcutHooks.next?.invoke()
                return true
            }

            Key.P -> {
                PlayerShortcutHooks.previous?.invoke()
                return true
            }

            Key.Comma -> {
                controller.setSpeed((state.speed - 0.25f).coerceAtLeast(0.25f))
                return true
            }

            Key.Period -> {
                controller.setSpeed((state.speed + 0.25f).coerceAtMost(3f))
                return true
            }

            Key.Slash -> {
                PlayerShortcutHooks.showShortcuts?.invoke()
                return true
            }
        }
    }

    when (key) {
        Key.Spacebar, Key.K -> {
            if (state.playing) controller.pause() else controller.resume()
            return true
        }

        Key.J -> {
            controller.seekTo((state.positionMs - PlayerShortcutHooks.seekDurationMs).coerceAtLeast(0))
            return true
        }

        Key.L -> {
            controller.seekTo(
                (state.positionMs + PlayerShortcutHooks.seekDurationMs).coerceAtMost(state.durationMs)
            )
            return true
        }

        Key.DirectionLeft -> {
            controller.seekTo((state.positionMs - PlayerShortcutHooks.seekDurationMs / 2).coerceAtLeast(0))
            return true
        }

        Key.DirectionRight -> {
            controller.seekTo(
                (state.positionMs + PlayerShortcutHooks.seekDurationMs / 2).coerceAtMost(state.durationMs)
            )
            return true
        }

        Key.DirectionUp -> {
            controller.setVolume((state.volume + 5).coerceAtMost(100))
            return true
        }

        Key.DirectionDown -> {
            controller.setVolume((state.volume - 5).coerceAtLeast(0))
            return true
        }

        Key.M -> {
            controller.setMuted(!state.muted)
            return true
        }

        Key.F -> {
            playerUiState.toggleFullscreen()
            return true
        }

        Key.T -> {
            playerUiState.toggleImmersive()
            return true
        }

        Key.I -> {
            PlayerShortcutHooks.togglePopup?.invoke() ?: PopupPlayer.toggle()
            return true
        }

        Key.C -> {
            PlayerShortcutHooks.toggleCaptions?.invoke()
            return true
        }

        Key.O -> {
            PlayerShortcutHooks.cycleCaptionBrightness?.invoke()
            return true
        }

        Key.W -> {
            PlayerShortcutHooks.cycleCaptionBackground?.invoke()
            return true
        }

        Key.Equals -> {
            PlayerShortcutHooks.increaseCaptionSize?.invoke()
            return true
        }

        Key.Minus -> {
            PlayerShortcutHooks.decreaseCaptionSize?.invoke()
            return true
        }

        Key.Comma -> {
            if (!state.playing) {
                controller.seekTo((state.positionMs - FRAME_STEP_MS).coerceAtLeast(0))
            }
            return true
        }

        Key.Period -> {
            if (!state.playing) {
                controller.seekTo(
                    (state.positionMs + FRAME_STEP_MS)
                        .coerceAtMost(state.durationMs.coerceAtLeast(0))
                )
            }
            return true
        }

        Key.MoveHome -> {
            controller.seekTo(0)
            return true
        }

        Key.MoveEnd -> {
            if (state.durationMs > 0) {
                controller.seekTo((state.durationMs - 1_000).coerceAtLeast(0))
            }
            return true
        }

        Key.Zero, Key.One, Key.Two, Key.Three, Key.Four,
        Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine -> {
            if (state.durationMs > 0) {
                val digit = keyDigit(key)
                controller.seekTo(state.durationMs * digit / 10)
            } else if (digitForKey(key) == 0) {
                controller.seekTo(0)
            }
            return true
        }

        else -> return false
    }
}

private const val FRAME_STEP_MS = 40L

/**
 * Maps the number keys to their digit.
 */
private fun digitForKey(key: Key): Int = when (key) {
    Key.Zero -> 0
    Key.One -> 1
    Key.Two -> 2
    Key.Three -> 3
    Key.Four -> 4
    Key.Five -> 5
    Key.Six -> 6
    Key.Seven -> 7
    Key.Eight -> 8
    Key.Nine -> 9
    else -> 0
}

private fun keyDigit(key: Key): Int = digitForKey(key)
