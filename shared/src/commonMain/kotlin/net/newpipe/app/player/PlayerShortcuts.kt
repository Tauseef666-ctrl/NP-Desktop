/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import androidx.compose.ui.input.key.Key

/**
 * Transport actions a keyboard shortcut can trigger. Labels/descriptions feed the future
 * searchable shortcut reference.
 */
enum class PlayerAction(val label: String, val description: String) {
    PLAY_PAUSE("Play/Pause", "Start or pause playback"),
    STOP("Stop", "Stop playback and reset position"),
    NEXT("Next", "Skip to the next item in the queue"),
    PREVIOUS("Previous", "Go back to the previous item"),
    SEEK_FORWARD("Seek forward", "Jump ahead by the configured skip interval (large step with Shift)"),
    SEEK_BACKWARD("Seek backward", "Jump back by the configured skip interval (large step with Shift)"),
    VOLUME_UP("Volume up", "Increase volume by 10 percent"),
    VOLUME_DOWN("Volume down", "Decrease volume by 10 percent"),
    MUTE("Mute / Unmute", "Toggle muting"),
    FOCUS_SEARCH("Focus search", "Move the keyboard cursor to the search field"),
    TOGGLE_MINI_PLAYER("Mini player", "Open or close the floating mini player")
}

/**
 * One key binding. A single action may have several bindings (e.g. keyboard key + media key).
 */
data class Shortcut(
    val action: PlayerAction,
    val key: Key,
    val label: String = action.label,
    val withCtrl: Boolean = false
) {

    /** Identity of the combination, used for conflict detection. */
    val combinationKey: Pair<Key, Boolean>
        get() = key to withCtrl

    fun matches(key: Key, isCtrlPressed: Boolean): Boolean =
        this.key == key && this.withCtrl == isCtrlPressed
}

/**
 * Default desktop bindings. Focus-aware by design: they resolve wherever the owning screen has
 * focus and consume the event rather than double-firing a focused control.
 */
object PlayerShortcuts {

    val defaults: List<Shortcut> = listOf(
        Shortcut(PlayerAction.PLAY_PAUSE, Key.Spacebar),
        Shortcut(PlayerAction.PLAY_PAUSE, Key.MediaPlayPause),
        Shortcut(PlayerAction.STOP, Key.MediaStop),
        Shortcut(PlayerAction.STOP, Key.S),
        Shortcut(PlayerAction.NEXT, Key.MediaNext),
        Shortcut(PlayerAction.PREVIOUS, Key.MediaPrevious),
        Shortcut(PlayerAction.SEEK_FORWARD, Key.DirectionRight),
        Shortcut(PlayerAction.SEEK_BACKWARD, Key.DirectionLeft),
        Shortcut(PlayerAction.VOLUME_UP, Key.DirectionUp),
        Shortcut(PlayerAction.VOLUME_UP, Key.VolumeUp),
        Shortcut(PlayerAction.VOLUME_DOWN, Key.DirectionDown),
        Shortcut(PlayerAction.VOLUME_DOWN, Key.VolumeDown),
        Shortcut(PlayerAction.MUTE, Key.M),
        Shortcut(PlayerAction.MUTE, Key.VolumeMute),
        Shortcut(PlayerAction.FOCUS_SEARCH, Key.F, withCtrl = true),
        Shortcut(PlayerAction.TOGGLE_MINI_PLAYER, Key.M, withCtrl = true)
    )

    /** Media-keys that must never be re-bound because they are safety/accessibility affordances. */
    val reservedMediaKeys: Set<Key> = setOf(
        Key.MediaPlayPause,
        Key.MediaStop,
        Key.MediaNext,
        Key.MediaPrevious,
        Key.VolumeUp,
        Key.VolumeDown,
        Key.VolumeMute
    )

    const val DEFAULT_SEEK_STEP_MS = 10_000L
    const val LARGE_SEEK_STEP_MS = 60_000L

    /**
     * Milliseconds to jump per seek (F2 "configurable skip intervals"). [configuredSeekMs] comes
     * from the shared `seek_duration` preference (default 10s); Shift still jumps the larger step.
     */
    fun seekStepMillis(isShiftPressed: Boolean, configuredSeekMs: Long = DEFAULT_SEEK_STEP_MS): Long =
        if (isShiftPressed) LARGE_SEEK_STEP_MS else configuredSeekMs.coerceAtLeast(1_000L)
}

/**
 * Maps key events to [PlayerAction]s. Pure and testable; the Compose layer only extracts the key
 * fields and delegates here.
 */
class ShortcutResolver(private val shortcuts: List<Shortcut> = PlayerShortcuts.defaults) {

    fun actionFor(key: Key, isCtrlPressed: Boolean = false): PlayerAction? {
        if (key == Key.Unknown) return null
        return shortcuts.firstOrNull { it.matches(key, isCtrlPressed) }?.action
    }

    fun reference(): List<Shortcut> = shortcuts

    /** Bindings that collide with another binding — must be empty for a valid configuration. */
    fun conflicts(): List<Shortcut> {
        val seen = mutableSetOf<Pair<Key, Boolean>>()
        val duplicates = mutableListOf<Shortcut>()
        shortcuts.forEach { shortcut ->
            if (!seen.add(shortcut.combinationKey)) duplicates.add(shortcut)
        }
        return duplicates
    }
}