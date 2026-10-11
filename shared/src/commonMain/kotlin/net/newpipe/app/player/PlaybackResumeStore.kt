/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import com.russhwolf.settings.Settings
import net.newpipe.app.preferences.PlayerPreferences
import org.koin.core.annotation.Singleton

/**
 * A stored resume position plus the minimum metadata needed to present it as a "Continue watching"
 * row. [title] and [url] are best-effort: they are blank when the media was saved before metadata
 * was remembered. Nothing is ever fabricated — callers must fall back to [id] when [title] is blank.
 */
data class ResumeEntry(
    val id: String,
    val title: String = "",
    val url: String = "",
    val positionMs: Long,
    val updatedAtEpochMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Persists per-media playback positions so a paused/stopped item can resume on the next load.
 *
 * Honest rules: nothing is stored for unknown durations or blank ids; a position is dropped as soon
 * as it reaches (or nearly reaches) the end, so finished media is never "resumed".
 */
@Singleton
class PlaybackResumeStore(
    private val settings: Settings
) {

    fun save(
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        title: String = "",
        url: String = "",
        nowEpochMs: Long = System.currentTimeMillis()
    ) {
        if (mediaId.isBlank()) return
        if (positionMs <= 0L) return
        if (durationMs > 0L && positionMs >= durationMs - NEAR_END_MS) {
            clear(mediaId)
            return
        }
        settings.putLong(positionKey(mediaId), positionMs)
        if (durationMs > 0L) settings.putLong(durationKey(mediaId), durationMs)
        settings.putLong(updatedKey(mediaId), nowEpochMs)
        if (title.isNotBlank()) settings.putString(titleKey(mediaId), title)
        if (url.isNotBlank()) settings.putString(urlKey(mediaId), url)
    }

    /**
     * Remember only the display metadata for [mediaId], so a resume row can show a real title once
     * a position is saved. Safe to call repeatedly; blank values are ignored (never faked).
     */
    fun rememberMetadata(mediaId: String, title: String, url: String = "") {
        if (mediaId.isBlank()) return
        if (title.isNotBlank()) settings.putString(titleKey(mediaId), title)
        if (url.isNotBlank()) settings.putString(urlKey(mediaId), url)
    }

    /** Returns the stored position (ms) or 0 when there is none / it is invalid. */
    fun load(mediaId: String): Long {
        if (mediaId.isBlank()) return 0L
        val position = settings.getLong(positionKey(mediaId), 0L)
        if (position <= 0L) return 0L
        val duration = settings.getLong(durationKey(mediaId), 0L)
        if (duration > 0L && position >= duration - NEAR_END_MS) {
            clear(mediaId)
            return 0L
        }
        return position
    }

    fun clear(mediaId: String) {
        settings.remove(positionKey(mediaId))
        settings.remove(durationKey(mediaId))
        settings.remove(updatedKey(mediaId))
        settings.remove(titleKey(mediaId))
        settings.remove(urlKey(mediaId))
    }

    /** Remove every stored resume position (privacy/clear-history). */
    fun clearAll() {
        settings.keys
            .filter { key ->
                key.startsWith(PlayerPreferences.PREFIX_RESUME_POSITION) ||
                    key.startsWith(PlayerPreferences.PREFIX_RESUME_DURATION) ||
                    key.startsWith(PREFIX_RESUME_UPDATED) ||
                    key.startsWith(PREFIX_RESUME_TITLE) ||
                    key.startsWith(PREFIX_RESUME_URL)
            }
            .forEach { settings.remove(it) }
    }

    /** Number of media items that currently have a stored resume position. */
    fun count(): Int = settings.keys.count { it.startsWith(PlayerPreferences.PREFIX_RESUME_POSITION) }

    /**
     * All stored resume entries, for the "Continue watching" view. Finished entries are skipped;
     * [ResumeEntry.title]/[ResumeEntry.url] are blank when metadata was never remembered.
     */
    fun entries(): List<ResumeEntry> =
        settings.keys
            .filter { it.startsWith(PlayerPreferences.PREFIX_RESUME_POSITION) }
            .mapNotNull { key ->
                val id = key.removePrefix(PlayerPreferences.PREFIX_RESUME_POSITION)
                val position = settings.getLong(key, 0L)
                if (position <= 0L) return@mapNotNull null
                val duration = settings.getLong(durationKey(id), 0L)
                if (duration > 0L && position >= duration - NEAR_END_MS) return@mapNotNull null
                ResumeEntry(
                    id = id,
                    title = settings.getStringOrNull(titleKey(id)) ?: "",
                    url = settings.getStringOrNull(urlKey(id)) ?: "",
                    positionMs = position,
                    updatedAtEpochMs = settings.getLong(updatedKey(id), 0L),
                    durationMs = duration
                )
            }

    private fun positionKey(mediaId: String): String =
        PlayerPreferences.PREFIX_RESUME_POSITION + mediaId

    private fun durationKey(mediaId: String): String =
        PlayerPreferences.PREFIX_RESUME_DURATION + mediaId

    private fun updatedKey(mediaId: String): String = PREFIX_RESUME_UPDATED + mediaId

    private fun titleKey(mediaId: String): String = PREFIX_RESUME_TITLE + mediaId

    private fun urlKey(mediaId: String): String = PREFIX_RESUME_URL + mediaId

    private companion object {
        /** Positions closer than this to the end count as finished. */
        const val NEAR_END_MS = 8_000L

        const val PREFIX_RESUME_UPDATED = "npp_resume_at_"
        const val PREFIX_RESUME_TITLE = "npp_resume_title_"
        const val PREFIX_RESUME_URL = "npp_resume_url_"
    }
}