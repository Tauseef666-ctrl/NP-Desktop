/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class WorkspaceMode { COMPACT, BALANCED, EXPANDED }

@Serializable
data class WindowBounds(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
data class WorkspaceSnapshot(
    val mode: WorkspaceMode = WorkspaceMode.BALANCED,
    val windowBounds: WindowBounds? = null,
    val miniPlayerBounds: WindowBounds? = null,
    val miniPlayerCompact: Boolean = false
)

/**
 * Persists the desktop workspace (layout mode + main-window bounds) as one JSON snapshot so it
 * survives a restart (F8). Constructed directly in desktopApp, not a Koin singleton.
 */
class WorkspacePreferences(
    private val settings: Settings,
    private val json: Json
) {
    private val key = "npp_workspace"

    fun mode(): WorkspaceMode = snapshot()?.mode ?: WorkspaceMode.BALANCED

    fun setMode(mode: WorkspaceMode) {
        val current = snapshot() ?: WorkspaceSnapshot()
        settings.putString(key, json.encodeToString(current.copy(mode = mode)))
    }

    /** Returns the saved bounds, or null when missing/corrupt or the size is not positive. */
    fun windowBounds(): WindowBounds? =
        snapshot()?.windowBounds?.takeIf { it.width > 0 && it.height > 0 }

    fun setWindowBounds(bounds: WindowBounds) {
        val current = snapshot() ?: WorkspaceSnapshot()
        settings.putString(key, json.encodeToString(current.copy(windowBounds = bounds)))
    }

    /** Returns the saved mini-player bounds, or null when missing/corrupt or the size is not positive. */
    fun miniPlayerBounds(): WindowBounds? =
        snapshot()?.miniPlayerBounds?.takeIf { it.width > 0 && it.height > 0 }

    fun setMiniPlayerBounds(bounds: WindowBounds) {
        val current = snapshot() ?: WorkspaceSnapshot()
        settings.putString(key, json.encodeToString(current.copy(miniPlayerBounds = bounds)))
    }

    /** Whether the mini-player was last left in its compact (collapsed) layout. */
    fun miniPlayerCompact(): Boolean = snapshot()?.miniPlayerCompact ?: false

    fun setMiniPlayerCompact(compact: Boolean) {
        val current = snapshot() ?: WorkspaceSnapshot()
        settings.putString(key, json.encodeToString(current.copy(miniPlayerCompact = compact)))
    }

    fun clear() {
        settings.remove(key)
    }

    private fun snapshot(): WorkspaceSnapshot? {
        val raw = settings.getString(key, "")
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString<WorkspaceSnapshot>(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}