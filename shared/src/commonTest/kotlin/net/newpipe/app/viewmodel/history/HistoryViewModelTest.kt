/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.history

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlayQueueStore
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.PlaybackState
import net.newpipe.app.player.PlaybackStatus
import net.newpipe.app.player.PlayerEngine
import net.newpipe.app.player.Quality
import net.newpipe.app.player.RecentlyPlayedEntry
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.screen.history.HistoryBucket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
class HistoryViewModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun media(id: String) = MediaItem(id = id, title = "Title $id", url = "file:///$id")

    private fun controller(
        engine: FakePlayerEngine,
        settings: MapSettings = MapSettings()
    ): PlaybackController =
        PlaybackController(
            engine,
            PlaybackResumeStore(settings),
            PlayQueueStore(settings, json),
            settings
        )

    @Test
    fun groupsReflectStoreEntries() {
        val store = RecentlyPlayedStore(MapSettings(), json)
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        store.record(media("a"), nowEpochMs = now - 2 * day)
        store.record(media("b"), nowEpochMs = now - 30 * day)
        val viewModel = HistoryViewModel(store, controller(FakePlayerEngine()))

        val groups = viewModel.groups.value
        assertEquals(2, groups.size)
        assertEquals(HistoryBucket.THIS_WEEK, groups[0].bucket)
        assertEquals(listOf("a"), groups[0].entries.map { it.id })
        assertEquals(HistoryBucket.EARLIER, groups[1].bucket)
        assertEquals("b", groups[1].entries.single().id)
        assertEquals(false, viewModel.isEmpty.value)
    }

    @Test
    fun emptyStoreExposesEmptyState() {
        val viewModel = HistoryViewModel(
            RecentlyPlayedStore(MapSettings(), json),
            controller(FakePlayerEngine())
        )

        assertTrue(viewModel.isEmpty.value)
        assertTrue(viewModel.groups.value.isEmpty())
    }

    @Test
    fun removeForwardsToStore() {
        val store = RecentlyPlayedStore(MapSettings(), json)
        val now = System.currentTimeMillis()
        store.record(media("a"), nowEpochMs = now)
        store.record(media("b"), nowEpochMs = now)
        val viewModel = HistoryViewModel(store, controller(FakePlayerEngine()))

        viewModel.remove("a")

        assertEquals(listOf("b"), store.entries.value.map { it.id })
        assertEquals(listOf("b"), viewModel.groups.value.single().entries.map { it.id })
    }

    @Test
    fun clearAllEmptiesEverything() {
        val store = RecentlyPlayedStore(MapSettings(), json)
        store.record(media("a"))
        store.record(media("b"))
        val viewModel = HistoryViewModel(store, controller(FakePlayerEngine()))

        viewModel.clearAll()

        assertTrue(store.entries.value.isEmpty())
        assertTrue(viewModel.groups.value.isEmpty())
        assertTrue(viewModel.isEmpty.value)
    }

    @Test
    fun playForwardsEntryToPlaybackController() = runTest {
        val store = RecentlyPlayedStore(MapSettings(), json)
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        val viewModel = HistoryViewModel(store, playback)
        val entry = RecentlyPlayedEntry(
            id = "a",
            title = "Title a",
            url = "file:///a",
            durationMs = 90_000L
        )

        viewModel.play(entry)

        assertEquals("a", playback.queueState.value.current?.id)
        assertEquals(90_000L, engine.prepared.last().durationMs)
    }

    @Test
    fun playIgnoresEntriesWithoutAUrl() = runTest {
        val store = RecentlyPlayedStore(MapSettings(), json)
        val engine = FakePlayerEngine()
        val playback = controller(engine)
        val viewModel = HistoryViewModel(store, playback)

        viewModel.play(
            RecentlyPlayedEntry(id = "x", title = "No url", url = "", durationMs = 0L)
        )

        assertTrue(engine.prepared.isEmpty())
    }
}