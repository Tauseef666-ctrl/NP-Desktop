/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * The APP-WIDE subset of the player shortcuts (F7).
 *
 * Only transport bindings that make sense while any screen is visible live here; volume, mute and
 * the letter aliases stay player-screen only. This is deliberately a strict subset of
 * [PlayerShortcuts.defaults]: a key is claimed only when the same [ShortcutResolver] the player
 * uses resolves it to the very action this scope expects, so a re-bound or conflicting key is
 * never hijacked app-wide.
 */
object AppShortcutScope {

    /** Reserved transport media keys that must keep working regardless of the focused screen. */
    private val mediaKeys: Set<Key> = setOf(
        Key.MediaPlayPause,
        Key.MediaStop,
        Key.MediaNext,
        Key.MediaPrevious
    )

    /** The app-wide bindings, kept in lock-step with the player defaults. */
    private val appBindings: Map<Key, PlayerAction> = mapOf(
        Key.MediaPlayPause to PlayerAction.PLAY_PAUSE,
        Key.MediaNext to PlayerAction.NEXT,
        Key.MediaPrevious to PlayerAction.PREVIOUS,
        Key.MediaStop to PlayerAction.STOP,
        Key.Spacebar to PlayerAction.PLAY_PAUSE,
        Key.DirectionLeft to PlayerAction.SEEK_BACKWARD,
        Key.DirectionRight to PlayerAction.SEEK_FORWARD,
        Key.F to PlayerAction.FOCUS_SEARCH,
        Key.M to PlayerAction.TOGGLE_MINI_PLAYER,
        Key.F11 to PlayerAction.TOGGLE_FULLSCREEN
    )

    /** True for the four reserved transport media keys (volume keys are intentionally excluded). */
    fun isMediaKey(key: Key): Boolean = key in mediaKeys

    /**
     * Resolves [key] to an app-wide [PlayerAction], or `null` for anything outside the app scope.
     *
     * [isShiftPressed] never changes the resolved action (it only selects the large seek step at
     * the call site) but is part of the signature so the Compose layer can pass the event verbatim.
     */
    @Suppress("UNUSED_PARAMETER")
    fun appActionFor(
        resolver: ShortcutResolver,
        key: Key,
        isCtrlPressed: Boolean,
        isShiftPressed: Boolean
    ): PlayerAction? {
        val expected = appBindings[key] ?: return null
        val resolved = resolver.actionFor(key, isCtrlPressed) ?: return null
        return resolved.takeIf { it == expected }
    }
}

/**
 * App-wide shortcut scope. Installed on the root content so playback keeps working on every screen.
 *
 * Uses [onKeyEvent] (the bubble phase), never the preview phase: a focused text field consumes
 * Space and typing first, so this handler only ever sees keys the focus owner left alone. Callers
 * own the real playback guard — see the desktop host — and this modifier consumes the event only
 * for keys [AppShortcutScope] actually claims.
 */
fun Modifier.plusAppShortcuts(
    resolver: ShortcutResolver,
    onAction: (PlayerAction) -> Unit
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    val action = AppShortcutScope.appActionFor(
        resolver = resolver,
        key = event.key,
        isCtrlPressed = event.isCtrlPressed,
        isShiftPressed = event.isShiftPressed
    ) ?: return@onKeyEvent false
    onAction(action)
    true
}
