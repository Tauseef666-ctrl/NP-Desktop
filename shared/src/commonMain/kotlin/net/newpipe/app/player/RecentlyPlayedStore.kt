/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton

/**
 * One item in the "Recently played" history. [title]/[url] mirror the [MediaItem] that was played;
 * [durationMs] is `0` when the duration was unknown at play time (never guessed).
 */
@Serializable
data class RecentlyPlayedEntry(
    val id: String,
    val title: String = "",
    val url: String = "",
    val durationMs: Long = 0L,
    val playedAtEpochMs: Long = 0L
)

/** Serializable snapshot of the whole recently-played history. */
@Serializable
data class RecentlyPlayedSnapshot(
    val items: List<RecentlyPlayedEntry> = emptyList()
)

/**
 * Persists the most-recently-played media (newest first, capped at [MAX_ENTRIES]) so the library can
 * show a "Recently played" view. Mirrors [PlayQueueStore]/[PlaybackResumeStore] storage conventions:
 * a blank or corrupt blob decodes to an empty history rather than throwing.
 *
 * [entries] is a hot [StateFlow] so the UI updates the instant a new item starts playing.
 */
@Singleton
class RecentlyPlayedStore(
    private val settings: Settings,
    private val json: Json
) {

    private val key = KEY

    private val _entries = MutableStateFlow(loadPersisted())
    val entries: StateFlow<List<RecentlyPlayedEntry>> = _entries.asStateFlow()

    /**
     * Record [item] as the newest played entry: an existing entry with the same id is replaced and
     * moved to the front, then the list is trimmed to [MAX_ENTRIES]. Blank ids are ignored.
     */
    fun record(item: MediaItem, nowEpochMs: Long = System.currentTimeMillis()) {
        if (item.id.isBlank()) return
        val updated = pureRecord(_entries.value, item, nowEpochMs, MAX_ENTRIES)
        _entries.value = updated
        persist(updated)
    }

    /** Drop the single entry with [id], leaving the rest of the history untouched and persisted. */
    fun remove(id: String) {
        val updated = pureRemove(_entries.value, id)
        _entries.value = updated
        persist(updated)
    }

    /** Forget the entire history (privacy/clear-history). */
    fun clear() {
        _entries.value = emptyList()
        settings.remove(key)
    }

    private fun loadPersisted(): List<RecentlyPlayedEntry> {
        val raw = settings.getStringOrNull(key) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<RecentlyPlayedSnapshot>(raw).items
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun persist(items: List<RecentlyPlayedEntry>) {
        // Java preferences store values as strings capped at 8192 chars. Live streams carry very long
        // manifest URLs, so drop the oldest entries until the snapshot fits instead of throwing.
        var current = items
        while (true) {
            val encoded = json.encodeToString(RecentlyPlayedSnapshot(current))
            if (encoded.length <= MAX_PREFERENCE_CHARS || current.isEmpty()) {
                runCatching { settings.putString(key, encoded) }
                return
            }
            current = current.dropLast(1)
        }
    }

    companion object {
        const val KEY = "npp_recently_played"

        /** Maximum number of remembered items; older entries fall off the end. */
        const val MAX_ENTRIES = 50

        /** Java preferences reject string values longer than 8192 characters; keep a safety margin. */
        private const val MAX_PREFERENCE_CHARS = 8000

        /**
         * Pure add/update used by [record]: keeps the input untouched, de-duplicates by id, puts the
         * fresh [item] first and trims to [cap]. Extracted so the ordering rule is unit-testable.
         */
        fun pureRecord(
            items: List<RecentlyPlayedEntry>,
            item: MediaItem,
            nowEpochMs: Long,
            cap: Int
        ): List<RecentlyPlayedEntry> {
            val entry = RecentlyPlayedEntry(
                id = item.id,
                title = item.title,
                url = item.url,
                durationMs = item.durationMs,
                playedAtEpochMs = nowEpochMs
            )
            val withoutDuplicate = items.filterNot { it.id == item.id }
            val limited = if (cap < 0) 0 else cap
            return (listOf(entry) + withoutDuplicate).take(limited)
        }

        /**
         * Pure removal used by [remove]: returns a new list without any entry whose id equals [id],
         * leaving the input untouched so the drop rule is unit-testable.
         */
        fun pureRemove(items: List<RecentlyPlayedEntry>, id: String): List<RecentlyPlayedEntry> =
            items.filterNot { it.id == id }
    }
}
