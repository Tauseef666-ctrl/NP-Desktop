/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.Json
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadSnapshot
import net.newpipe.app.download.DownloadStore
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlayQueueSnapshot
import net.newpipe.app.player.PlayQueueStore
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.PlaylistSnapshot
import net.newpipe.app.player.PlaylistStore
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.player.RepeatMode
import net.newpipe.app.player.SavedPlaylist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupContentTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun content(settings: MapSettings) = BackupContent(
        queueStore = PlayQueueStore(settings, json),
        playlistStore = PlaylistStore(settings, json),
        resumeStore = PlaybackResumeStore(settings),
        recentlyPlayedStore = RecentlyPlayedStore(settings, json),
        downloadStore = DownloadStore(settings, json),
        json = json
    )

    private fun seed(settings: MapSettings) {
        PlayQueueStore(settings, json).save(
            PlayQueueSnapshot(
                items = listOf(MediaItem("v1", "Video One", "https://x/v1", 60_000L)),
                currentIndex = 0,
                repeatMode = RepeatMode.OFF,
                isShuffled = false
            )
        )
        PlaylistStore(settings, json).saveAll(
            PlaylistSnapshot(listOf(SavedPlaylist("p1", "Favourites", listOf(MediaItem("v1", "Video One")))))
        )
        PlaybackResumeStore(settings).save(
            mediaId = "v1",
            positionMs = 5_000L,
            durationMs = 60_000L,
            title = "Video One",
            url = "https://x/v1",
            nowEpochMs = 123L
        )
        RecentlyPlayedStore(settings, json).record(
            MediaItem("v1", "Video One", "https://x/v1", 60_000L),
            nowEpochMs = 9L
        )
        DownloadStore(settings, json).save(
            DownloadSnapshot(listOf(DownloadTask(DownloadRequest("d1", "https://x/v1", "/tmp/f"))))
        )
        DownloadStore(settings, json).saveOrder(listOf("d1"))
    }

    @Test
    fun collectPacksEveryDomainThatHoldsData() {
        val settings = MapSettings()
        seed(settings)

        val blobs = content(settings).collectEntries()

        assertTrue(blobs.containsKey(BackupContent.ENTRY_QUEUE))
        assertTrue(blobs.containsKey(BackupContent.ENTRY_PLAYLISTS))
        assertTrue(blobs.containsKey(BackupContent.ENTRY_RESUME))
        assertTrue(blobs.containsKey(BackupContent.ENTRY_RECENTLY_PLAYED))
        assertTrue(blobs.containsKey(BackupContent.ENTRY_DOWNLOADS))
        assertTrue(blobs.containsKey(BackupContent.ENTRY_DOWNLOAD_ORDER))
    }

    @Test
    fun collectOmitsEmptyDomains() {
        val blobs = content(MapSettings()).collectEntries()
        assertTrue(blobs.isEmpty())
    }

    @Test
    fun restoreWritesRealBlobsBackIntoTheStores() {
        val source = MapSettings()
        seed(source)
        val blobs = content(source).collectEntries()

        val target = MapSettings()
        assertTrue(content(target).restoreEntries(blobs))

        assertEquals("v1", PlayQueueStore(target, json).load()?.items?.single()?.id)
        assertEquals("p1", PlaylistStore(target, json).loadAll()?.playlists?.single()?.id)
        assertEquals(5_000L, PlaybackResumeStore(target).load("v1"))
        assertEquals(listOf("v1"), RecentlyPlayedStore(target, json).entries.value.map { it.id })
        assertEquals("d1", DownloadStore(target, json).load()?.tasks?.single()?.id)
        assertEquals(listOf("d1"), DownloadStore(target, json).loadOrder())
    }

    @Test
    fun restoreRejectsCorruptBlobWithoutTouchingExistingData() {
        val target = MapSettings()
        PlayQueueStore(target, json).save(
            PlayQueueSnapshot(listOf(MediaItem("keep", "Keep")), 0, RepeatMode.OFF, false)
        )

        val result = content(target).restoreEntries(
            mapOf(BackupContent.ENTRY_QUEUE to "not json {".encodeToByteArray())
        )

        assertFalse(result)
        assertEquals("keep", PlayQueueStore(target, json).load()?.items?.single()?.id)
    }

    @Test
    fun restoreClearsDomainsMissingFromTheArchive() {
        val target = MapSettings()
        PlayQueueStore(target, json).save(
            PlayQueueSnapshot(listOf(MediaItem("old", "Old")), 0, RepeatMode.OFF, false)
        )

        assertTrue(content(target).restoreEntries(emptyMap()))

        assertNull(PlayQueueStore(target, json).load())
    }
}
