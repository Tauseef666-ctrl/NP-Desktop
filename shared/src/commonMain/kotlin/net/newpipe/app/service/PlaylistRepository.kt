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
import net.newpipe.app.model.SavedPlaylist

/**
 * Stores the playlists the user created locally.
 *
 * The extraction engine has no write access, so local playlists are persisted as JSON in the
 * platform settings. They survive restarts on every platform without a database and can contain
 * videos from any supported service.
 */
class PlaylistRepository(
    private val settings: Settings
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _playlists = MutableStateFlow(load())
    val playlists = _playlists.asStateFlow()

    /**
     * Creates a playlist with the given name and returns its id.
     */
    fun create(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1

        val id = (_playlists.value.maxOfOrNull { it.id } ?: 0L) + 1
        val updated = _playlists.value + SavedPlaylist(id = id, name = trimmed)
        _playlists.value = updated
        persist(updated)
        return id
    }

    /**
     * Returns the id of the playlist with the given name, creating it when it does not exist
     * yet. Used for the built in playlists such as "Watch later".
     */
    fun ensurePlaylist(name: String): Long {
        _playlists.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.let { return it.id }
        return create(name)
    }

    /**
     * Adds the item to the playlist, or removes it when it is already part of the playlist.
     */
    fun toggle(playlistId: Long, item: ContentItem) {
        val updated = _playlists.value.map { playlist ->
            if (playlist.id != playlistId) return@map playlist

            val contains = playlist.items.any { it.url == item.url }
            if (contains) {
                playlist.copy(items = playlist.items.filterNot { it.url == item.url })
            } else {
                playlist.copy(items = playlist.items + item)
            }
        }

        _playlists.value = updated
        persist(updated)
    }

    /**
     * Removes an entry from the playlist.
     */
    fun remove(playlistId: Long, url: String) {
        val updated = _playlists.value.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else {
                playlist.copy(items = playlist.items.filterNot { it.url == url })
            }
        }

        _playlists.value = updated
        persist(updated)
    }

    /**
     * Deletes the whole playlist.
     */
    fun delete(playlistId: Long) {
        val updated = _playlists.value.filterNot { it.id == playlistId }
        _playlists.value = updated
        persist(updated)
    }

    private fun load(): List<SavedPlaylist> {
        val stored = settings.getStringOrNull(KEY) ?: return emptyList()
        return try {
            json.decodeFromString<List<StoredPlaylist>>(stored).map { it.toPlaylist() }
        } catch (exception: Exception) {
            Logger.w(messageString = "The playlists could not be read", throwable = exception)
            emptyList()
        }
    }

    private fun persist(playlists: List<SavedPlaylist>) {
        try {
            settings.putString(
                KEY,
                json.encodeToString(playlists.map { StoredPlaylist.from(it) })
            )
        } catch (exception: Exception) {
            Logger.w(messageString = "The playlists could not be saved", throwable = exception)
        }
    }

    @Serializable
    private data class StoredPlaylist(
        val id: Long,
        val name: String,
        val items: List<StoredItem> = emptyList()
    ) {
        fun toPlaylist() = SavedPlaylist(
            id = id,
            name = name,
            items = items.map { it.toContentItem() }
        )

        companion object {
            fun from(playlist: SavedPlaylist) = StoredPlaylist(
                id = playlist.id,
                name = playlist.name,
                items = playlist.items.map { StoredItem.from(it) }
            )
        }
    }

    @Serializable
    private data class StoredItem(
        val serviceId: Int,
        val url: String,
        val title: String,
        val uploader: String? = null,
        val uploaderUrl: String? = null,
        val thumbnailUrl: String? = null,
        val durationSeconds: Long? = null
    ) {
        fun toContentItem() = ContentItem(
            serviceId = serviceId,
            url = url,
            title = title,
            type = ContentType.STREAM,
            uploader = uploader,
            uploaderUrl = uploaderUrl,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds
        )

        companion object {
            fun from(item: ContentItem) = StoredItem(
                serviceId = item.serviceId,
                url = item.url,
                title = item.title,
                uploader = item.uploader,
                uploaderUrl = item.uploaderUrl,
                thumbnailUrl = item.thumbnailUrl,
                durationSeconds = item.durationSeconds
            )
        }
    }

    companion object {
        private const val KEY = "local_playlists_v1"
    }
}
