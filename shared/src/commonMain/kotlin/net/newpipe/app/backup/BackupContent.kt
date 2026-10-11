/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.newpipe.app.download.DownloadSnapshot
import net.newpipe.app.download.DownloadStore
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlayQueueSnapshot
import net.newpipe.app.player.PlayQueueStore
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.PlaylistSnapshot
import net.newpipe.app.player.PlaylistStore
import net.newpipe.app.player.RecentlyPlayedSnapshot
import net.newpipe.app.player.RecentlyPlayedStore
import org.koin.core.annotation.Singleton

/** Serializable mirror of a [PlaybackResumeStore] entry, which is not itself serializable. */
@Serializable
data class BackupResumeEntry(
    val id: String,
    val title: String = "",
    val url: String = "",
    val positionMs: Long,
    val updatedAtEpochMs: Long = 0L,
    val durationMs: Long = 0L
)

/** Whole resume store snapshot, persisted as one archive entry. */
@Serializable
data class BackupResumeSnapshot(
    val entries: List<BackupResumeEntry> = emptyList()
)

/**
 * Reads and writes the real persisted application blobs through the existing stores. This is the
 * only place that knows how the backup payload maps onto storage, so the backup is always exactly
 * the data the app itself persists — never a hand-built or fabricated copy.
 *
 * [collectEntries] returns one byte blob per domain that currently holds data (a domain with no data
 * is simply omitted). [restoreEntries] follows a strict replace contract: every blob is decoded and
 * validated up-front, and nothing is written unless all present blobs are valid, so a corrupt
 * archive cannot half-overwrite local data.
 */
@Singleton
class BackupContent(
    private val queueStore: PlayQueueStore,
    private val playlistStore: PlaylistStore,
    private val resumeStore: PlaybackResumeStore,
    private val recentlyPlayedStore: RecentlyPlayedStore,
    private val downloadStore: DownloadStore,
    private val json: Json
) {

    /** Serializes every domain that currently holds data into its archive entry. */
    fun collectEntries(): Map<String, ByteArray> = buildMap {
        queueStore.load()?.let { snapshot ->
            put(ENTRY_QUEUE, json.encodeToString(snapshot).encodeToByteArray())
        }
        playlistStore.loadAll()?.let { snapshot ->
            put(ENTRY_PLAYLISTS, json.encodeToString(snapshot).encodeToByteArray())
        }
        resumeStore.entries().takeIf { it.isNotEmpty() }?.let { entries ->
            val snapshot = BackupResumeSnapshot(
                entries.map { entry ->
                    BackupResumeEntry(
                        id = entry.id,
                        title = entry.title,
                        url = entry.url,
                        positionMs = entry.positionMs,
                        updatedAtEpochMs = entry.updatedAtEpochMs,
                        durationMs = entry.durationMs
                    )
                }
            )
            put(ENTRY_RESUME, json.encodeToString(snapshot).encodeToByteArray())
        }
        recentlyPlayedStore.entries.value.takeIf { it.isNotEmpty() }?.let { entries ->
            put(ENTRY_RECENTLY_PLAYED, json.encodeToString(RecentlyPlayedSnapshot(entries)).encodeToByteArray())
        }
        downloadStore.load()?.let { snapshot ->
            put(ENTRY_DOWNLOADS, json.encodeToString(snapshot).encodeToByteArray())
        }
        downloadStore.loadOrder().takeIf { it.isNotEmpty() }?.let { order ->
            put(ENTRY_DOWNLOAD_ORDER, order.joinToString("\n").encodeToByteArray())
        }
    }

    /**
     * Replaces the local domains with the archive payload. Returns `false` (writing nothing) when any
     * present blob is malformed. Domains absent from [entries] are cleared, mirroring the confirm
     * dialog's "current local data will be replaced" contract.
     */
    fun restoreEntries(entries: Map<String, ByteArray>): Boolean {
        val queue = entries[ENTRY_QUEUE]?.let { decode<PlayQueueSnapshot>(it) ?: return false }
        val playlists = entries[ENTRY_PLAYLISTS]?.let { decode<PlaylistSnapshot>(it) ?: return false }
        val resume = entries[ENTRY_RESUME]?.let { decode<BackupResumeSnapshot>(it) ?: return false }
        val recentlyPlayed = entries[ENTRY_RECENTLY_PLAYED]?.let { decode<RecentlyPlayedSnapshot>(it) ?: return false }
        val downloads = entries[ENTRY_DOWNLOADS]?.let { decode<DownloadSnapshot>(it) ?: return false }
        val downloadOrder = entries[ENTRY_DOWNLOAD_ORDER]?.decodeToString()
            ?.split('\n')
            ?.filter { it.isNotBlank() }

        if (queue != null) queueStore.save(queue) else queueStore.clear()

        if (playlists != null) playlistStore.saveAll(playlists) else playlistStore.clear()

        resumeStore.clearAll()
        resume?.entries?.forEach { entry ->
            resumeStore.save(
                mediaId = entry.id,
                positionMs = entry.positionMs,
                durationMs = entry.durationMs,
                title = entry.title,
                url = entry.url,
                nowEpochMs = entry.updatedAtEpochMs
            )
        }

        recentlyPlayedStore.clear()
        recentlyPlayed?.items?.asReversed()?.forEach { entry ->
            recentlyPlayedStore.record(
                MediaItem(
                    id = entry.id,
                    title = entry.title,
                    url = entry.url,
                    durationMs = entry.durationMs
                ),
                nowEpochMs = entry.playedAtEpochMs
            )
        }

        if (downloads != null) downloadStore.save(downloads) else downloadStore.clear()
        downloadStore.saveOrder(downloadOrder ?: emptyList())

        return true
    }

    private inline fun <reified T> decode(bytes: ByteArray): T? = try {
        json.decodeFromString<T>(bytes.decodeToString())
    } catch (_: Exception) {
        null
    }

    companion object {
        const val ENTRY_QUEUE = "queue.json"
        const val ENTRY_PLAYLISTS = "playlists.json"
        const val ENTRY_RESUME = "resume.json"
        const val ENTRY_RECENTLY_PLAYED = "recently_played.json"
        const val ENTRY_DOWNLOADS = "downloads.json"
        const val ENTRY_DOWNLOAD_ORDER = "download_order.txt"
    }
}
