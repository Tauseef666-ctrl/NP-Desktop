/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType

/**
 * Stores the streams the user has played, most recent first.
 *
 * The history drives the personalised feed on the home screen and will also back the history
 * screen. It is persisted as JSON in the platform settings, so it survives restarts on every
 * platform without a database.
 */
class WatchHistoryRepository(
    private val settings: Settings
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _items = MutableStateFlow(load())
    val items = _items.asStateFlow()

    /**
     * Records a stream as watched, moving it to the front of the list.
     *
     * The stored progress of a previous entry of the same stream is kept when the new item does
     * not carry a position itself.
     */
    fun record(item: ContentItem) {
        if (item.type != ContentType.STREAM) return

        val previous = _items.value.firstOrNull { it.url == item.url }
        val withProgress = if (item.resumePositionMs == null && previous != null) {
            item.copy(resumePositionMs = previous.resumePositionMs)
        } else {
            item
        }

        val updated = (listOf(withProgress) + _items.value.filterNot { it.url == item.url })
            .take(MAX_ENTRIES)

        _items.value = updated
        persist(updated)
    }

    /**
     * Saves the playback position of the stream with the given url as the resume point.
     *
     * Positions close to the very start or the very end are not stored: the start would look
     * like nothing was watched and the end means the stream was watched completely, so the next
     * playthrough should begin at the beginning again.
     */
    fun updateProgress(url: String, positionMs: Long, durationMs: Long = 0) {
        if (positionMs < RESUME_MIN_MS) return
        if (durationMs > 0 && positionMs >= durationMs - RESUME_NEAR_END_MS) return

        val current = _items.value
        val index = current.indexOfFirst { it.url == url }
        if (index < 0) return

        val entry = current[index]
        if (entry.resumePositionMs == positionMs) return

        val updated = current.toMutableList()
        updated[index] = entry.copy(resumePositionMs = positionMs)
        _items.value = updated
        persist(updated)
    }

    /**
     * The position at which the stream with the given url can be resumed, or `0`.
     */
    fun resumePosition(url: String): Long =
        _items.value.firstOrNull { it.url == url }?.resumePositionMs ?: 0L

    /**
     * Removes all entries from the history.
     */
    fun clear() {
        _items.value = emptyList()
        settings.remove(KEY)
    }

    private fun load(): List<ContentItem> {
        val stored = settings.getStringOrNull(KEY) ?: return emptyList()
        return try {
            json.decodeFromString<List<StoredWatch>>(stored).map { it.toContentItem() }
        } catch (exception: Exception) {
            Logger.w(messageString = "The watch history could not be read", throwable = exception)
            emptyList()
        }
    }

    private fun persist(items: List<ContentItem>) {
        try {
            settings.putString(KEY, json.encodeToString(items.map { StoredWatch.from(it) }))
        } catch (exception: Exception) {
            Logger.w(messageString = "The watch history could not be saved", throwable = exception)
        }
    }

    @Serializable
    private data class StoredWatch(
        val serviceId: Int,
        val url: String,
        val title: String,
        val uploader: String? = null,
        val uploaderUrl: String? = null,
        val thumbnailUrl: String? = null,
        val durationSeconds: Long? = null,
        val resumePositionMs: Long? = null
    ) {
        fun toContentItem() = ContentItem(
            serviceId = serviceId,
            url = url,
            title = title,
            type = ContentType.STREAM,
            uploader = uploader,
            uploaderUrl = uploaderUrl,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            resumePositionMs = resumePositionMs
        )

        companion object {
            fun from(item: ContentItem) = StoredWatch(
                serviceId = item.serviceId,
                url = item.url,
                title = item.title,
                uploader = item.uploader,
                uploaderUrl = item.uploaderUrl,
                thumbnailUrl = item.thumbnailUrl,
                durationSeconds = item.durationSeconds,
                resumePositionMs = item.resumePositionMs
            )
        }
    }

    companion object {
        private const val KEY = "watch_history_v1"
        private const val MAX_ENTRIES = 100
        private const val RESUME_MIN_MS = 5_000L
        private const val RESUME_NEAR_END_MS = 15_000L
    }
}
