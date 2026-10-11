/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import androidx.compose.ui.input.key.Key

/**
 * One human-readable line of the keyboard-shortcut reference (F7). Pure data; the UI renders it.
 */
data class ShortcutRow(
    val keyLabel: String,
    val actionLabel: String,
    val description: String,
    val isAppWide: Boolean = false
)

/**
 * Readable name of a bound key, so the reference is searchable and understandable without
 * guessing the Compose [Key] constant.
 */
fun Shortcut.keyLabel(): String = when (key) {
    Key.Spacebar -> "Space"
    Key.MediaPlayPause -> "Media Play/Pause"
    Key.MediaStop -> "Media Stop"
    Key.MediaNext -> "Media Next"
    Key.MediaPrevious -> "Media Previous"
    Key.DirectionRight -> "\u2192"
    Key.DirectionLeft -> "\u2190"
    Key.DirectionUp -> "\u2191"
    Key.DirectionDown -> "\u2193"
    Key.VolumeUp -> "Volume Up"
    Key.VolumeDown -> "Volume Down"
    Key.VolumeMute -> "Volume Mute"
    Key.M -> "M"
    Key.S -> "S"
    else -> printableKeyLabel(key)
}

private fun printableKeyLabel(key: Key): String {
    val ch = key.keyCode.toInt().toChar()
    return if (ch in ' '..'~') ch.toString() else key.keyCode.toString()
}

/**
 * Search + formatting for the shortcut reference. Pure and testable: no UI or engine state.
 *
 * @param query Matched (case-insensitively) against the action label, its description and the
 *   human-readable key label. Blank query returns every bound shortcut.
 */
object ShortcutReference {

    fun rows(query: String, shortcuts: List<Shortcut> = PlayerShortcuts.defaults): List<ShortcutRow> {
        val normalized = query.trim().lowercase()
        val resolver = ShortcutResolver(shortcuts)
        return shortcuts
            .asSequence()
            .filter { normalized.isEmpty() || it.matchesQuery(normalized) }
            .map { shortcut ->
                ShortcutRow(
                    keyLabel = shortcut.keyLabel(),
                    actionLabel = shortcut.action.label,
                    description = shortcut.action.description,
                    isAppWide = AppShortcutScope.appActionFor(
                        resolver = resolver,
                        key = shortcut.key,
                        isCtrlPressed = shortcut.withCtrl,
                        isShiftPressed = false
                    ) == shortcut.action
                )
            }
            .toList()
    }

    private fun Shortcut.matchesQuery(normalizedQuery: String): Boolean {
        val haystack = buildList {
            add(action.label)
            add(action.description)
            add(keyLabel())
            if (withCtrl) add("ctrl")
        }.joinToString(" ").lowercase()
        return haystack.contains(normalizedQuery)
    }

    /** Bindings that are locked and cannot be re-bound (safety/accessibility affordances). */
    fun reservedKeyLabels(): List<String> =
        PlayerShortcuts.reservedMediaKeys.map {
            PlayerShortcuts.defaults.first { s -> s.key == it }.keyLabel()
        }.distinct()
}