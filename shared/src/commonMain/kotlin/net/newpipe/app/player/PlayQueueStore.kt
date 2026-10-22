/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton

/**
 * Snapshot of a [PlayQueue] that can survive an app restart (F6). The display order already
 * reflects shuffle, so restoring it verbatim keeps the same "up next" experience.
 */
@Serializable
data class PlayQueueSnapshot(
    val items: List<MediaItem>,
    val currentIndex: Int,
    val repeatMode: RepeatMode,
    val isShuffled: Boolean
)

/**
 * Persists the play queue to [Settings] so it survives an app restart (F6). Playback itself is
 * never restarted automatically — restoring a queue merely re-lists the items so the user decides.
 */
@Singleton
class PlayQueueStore(
    private val settings: Settings,
    private val json: Json
) {
    private val key = "npp_persisted_queue"

    fun save(snapshot: PlayQueueSnapshot) {
        try {
            settings.putString(key, json.encodeToString(snapshot))
        } catch (_: IllegalArgumentException) {
            // java.util.prefs caps a value at ~8KB; an oversized queue just does not persist.
            // Never let persistence break playback.
        }
    }

    /** Returns the saved queue, or null when none/blank/corrupt (corrupt data is dropped). */
    fun load(): PlayQueueSnapshot? {
        val raw = settings.getString(key, "")
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString<PlayQueueSnapshot>(raw)
                .takeIf { it.items.isNotEmpty() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun clear() {
        settings.remove(key)
    }
}