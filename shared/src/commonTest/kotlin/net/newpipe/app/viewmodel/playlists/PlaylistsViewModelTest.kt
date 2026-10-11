/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.playlists

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlayQueueStore
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.PlaybackState
import net.newpipe.app.player.PlaybackStatus
import net.newpipe.app.player.PlayerEngine
import net.newpipe.app.player.PlaylistSnapshot
import net.newpipe.app.player.PlaylistStore
import net.newpipe.app.player.Quality
import net.newpipe.app.player.SavedPlaylist

private class FakePlayerEngine : PlayerEngine {
    override val isAvailable: Boolean = true

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val prepared = mutableListOf<MediaItem>()

    override suspend fun prepare(item: MediaItem) {
        prepared += item
        _state.value = _state.value.copy(mediaId = item.id, status = PlaybackStatus.READY)
    }

    override fun play() {
        _state.value = _state.value.copy(isPlaying = true)
    }

    override fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) {
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    override fun setSpeed(speed: Float) = Unit
    override fun setVolume(volume: Float) = Unit

    override fun setMuted(muted: Boolean) {
        _state.value = _state.value.copy(isMuted = muted)
    }

    override fun setQuality(quality: Quality) = Unit
    override fun release() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun item(id: String) = MediaItem(id = id, title = id, url = "file:///$id")

    private fun playlist(id: String) =
        SavedPlaylist(id = id, name = "List $id", items = listOf(item(id)))

    private fun controller(
        engine: FakePlayerEngine,
        settings: MapSettings = MapSettings()
    ): PlaybackController =
        PlaybackController(
            engine,
            PlaybackResumeStore(settings),
            PlayQueueStore(settings, Json),
            settings
        )

    @Test
    fun saveQueueAsPlaylistExposesEntryInSavedPlaylists() = runBlocking {
        val settings = MapSettings()
        val controller = controller(FakePlayerEngine(), settings)

        controller.setQueue(listOf(item("a"), item("b")))
        assertTrue(controller.saveQueueAsPlaylist())

        val saved = controller.savedPlaylists.value
        assertEquals(1, saved.size)
        assertEquals(listOf("a", "b"), saved.first().items.map { it.id })
    }

    @Test
    fun deletePlaylistRemovesItAndUpdatesSavedPlaylists() = runBlocking {
        val settings = MapSettings()
        val controller = controller(FakePlayerEngine(), settings)
        controller.setQueue(listOf(item("a")))
        controller.saveQueueAsPlaylist()
        val id = controller.savedPlaylists.value.first().id

        controller.deletePlaylist(id)

        assertTrue(controller.savedPlaylists.value.isEmpty())
        assertEquals(null, PlaylistStore(settings, Json).loadAll())
    }

    @Test
    fun deletePlaylistOnlyRemovesTargetedPlaylist() {
        val settings = MapSettings()
        PlaylistStore(settings, Json).saveAll(
            PlaylistSnapshot(listOf(playlist("a"), playlist("b"), playlist("c")))
        )
        val controller = controller(FakePlayerEngine(), settings)

        controller.deletePlaylist("b")

        assertEquals(listOf("a", "c"), controller.savedPlaylists.value.map { it.id })
        val stored = PlaylistStore(settings, Json).loadAll()?.playlists.orEmpty()
        assertEquals(listOf("a", "c"), stored.map { it.id })
    }

    @Test
    fun deletePlaylistUnknownIdIsNoOp() {
        val settings = MapSettings()
        PlaylistStore(settings, Json).saveAll(PlaylistSnapshot(listOf(playlist("a"))))
        val controller = controller(FakePlayerEngine(), settings)

        controller.deletePlaylist("missing")

        assertEquals(listOf("a"), controller.savedPlaylists.value.map { it.id })
    }

    @Test
    fun viewModelPlayRestoresQueueAndStartsFirstItem() = runTest {
        val settings = MapSettings()
        val saver = controller(FakePlayerEngine(), settings)
        saver.setQueue(listOf(item("a"), item("b")))
        saver.saveQueueAsPlaylist()
        val saved = saver.savedPlaylists.value.first()

        val engine = FakePlayerEngine()
        val playback = controller(engine, settings)
        playback.setQueue(listOf(item("x")))
        val viewModel = PlaylistsViewModel(playback)

        viewModel.play(saved)

        assertEquals(listOf("a", "b"), playback.queueState.value.items.map { it.id })
        assertEquals("a", playback.queueState.value.current?.id)
        assertEquals("a", engine.prepared.last().id)
    }

    @Test
    fun viewModelDeleteForwardsToController() = runBlocking {
        val settings = MapSettings()
        val controller = controller(FakePlayerEngine(), settings)
        controller.setQueue(listOf(item("a")))
        controller.saveQueueAsPlaylist()
        val id = controller.savedPlaylists.value.first().id
        val viewModel = PlaylistsViewModel(controller)

        viewModel.delete(id)

        assertTrue(viewModel.playlists.value.isEmpty())
    }
}
