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
import net.newpipe.app.model.DownloadedVideo

/**
 * Stores the videos that were downloaded to the local storage.
 *
 * Only the metadata is persisted; the video file itself lives in the downloads directory of the
 * platform. The entry keeps the absolute file path, so the same player that plays network streams
 * can play the local file from the downloads screen.
 */
class DownloadRepository(
    private val settings: Settings
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _downloads = MutableStateFlow(load())
    val downloads = _downloads.asStateFlow()

    /**
     * Adds a downloaded video, replacing an older entry for the same file.
     */
    fun add(
        item: ContentItem,
        filePath: String,
        quality: String,
        formatId: String,
        audioFilePath: String? = null
    ) {
        val entry = DownloadedVideo(
            id = filePath,
            item = item,
            filePath = filePath,
            quality = quality,
            formatId = formatId,
            audioFilePath = audioFilePath,
            downloadedAt = 0L
        )

        val updated = listOf(entry) + _downloads.value.filterNot { it.id == filePath }
        _downloads.value = updated
        persist(updated)
    }

    /**
     * Removes the entry from the library. The file on disk is left untouched.
     */
    fun remove(id: String) {
        val updated = _downloads.value.filterNot { it.id == id }
        _downloads.value = updated
        persist(updated)
    }

    private fun load(): List<DownloadedVideo> {
        val stored = settings.getStringOrNull(KEY) ?: return emptyList()
        return try {
            json.decodeFromString<List<StoredDownload>>(stored).map { it.toDownload() }
        } catch (exception: Exception) {
            Logger.w(messageString = "The downloads could not be read", throwable = exception)
            emptyList()
        }
    }

    private fun persist(downloads: List<DownloadedVideo>) {
        try {
            settings.putString(
                KEY,
                json.encodeToString(downloads.map { StoredDownload.from(it) })
            )
        } catch (exception: Exception) {
            Logger.w(messageString = "The downloads could not be saved", throwable = exception)
        }
    }

    @Serializable
    private data class StoredDownload(
        val id: String,
        val item: StoredItem,
        val filePath: String,
        val quality: String,
        val formatId: String,
        val audioFilePath: String? = null,
        val downloadedAt: Long
    ) {
        fun toDownload() = DownloadedVideo(
            id = id,
            item = item.toContentItem(),
            filePath = filePath,
            quality = quality,
            formatId = formatId,
            audioFilePath = audioFilePath,
            downloadedAt = downloadedAt
        )

        companion object {
            fun from(download: DownloadedVideo) = StoredDownload(
                id = download.id,
                item = StoredItem.from(download.item),
                filePath = download.filePath,
                quality = download.quality,
                formatId = download.formatId,
                audioFilePath = download.audioFilePath,
                downloadedAt = download.downloadedAt
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
        private const val KEY = "downloads_v1"
    }
}
