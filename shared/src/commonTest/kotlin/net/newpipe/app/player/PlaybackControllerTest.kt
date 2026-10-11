/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.newpipe.app.preferences.PlayerPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FakePlayerEngine : PlayerEngine {
    override val isAvailable: Boolean = true

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val prepared = mutableListOf<MediaItem>()

    /** Every speed passed to [setSpeed], in call order. */
    val speeds = mutableListOf<Float>()

    fun setState(state: PlaybackState) {
        _state.value = state
    }

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

    override fun setSpeed(speed: Float) {
        speeds += speed
    }

    override fun setVolume(volume: Float) = Unit

    override fun setMuted(muted: Boolean) {
        _state.value = _state.value.copy(isMuted = muted)
    }

    override fun setQuality(quality: Quality) = Unit
    override fun release() = Unit
}

class PlaybackControllerTest {

    private fun item(id: String) = MediaItem(id = id, title = id, url = "file:///$id")

    private fun controller(
        engine: FakePlayerEngine,
        settings: Settings = MapSettings()
    ): PlaybackController {
        val store = PlaybackResumeStore(settings)
        val queueStore = PlayQueueStore(settings, Json)
        return PlaybackController(engine, store, queueStore, settings)
    }

    @Test
    fun setQueuePreparesStartItemAndPublishesQueue() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)

        controller.setQueue(listOf(item("a"), item("b"), item("c")), startIndex = 1)

        assertEquals(listOf("b"), engine.prepared.map { it.id })
        assertEquals(3, controller.queueState.value.items.size)
        assertEquals(1, controller.queueState.value.currentIndex)
        assertEquals("b", controller.queueState.value.current?.id)
    }

    @Test
    fun nextAndPreviousAdvanceAndPrepare() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)
        controller.setQueue(listOf(item("a"), item("b"), item("c")))

        controller.next()
        controller.next()

        assertEquals(2, controller.queueState.value.currentIndex)
        assertEquals("c", controller.queueState.value.current?.id)
        assertEquals(listOf("a", "b", "c"), engine.prepared.map { it.id })

        controller.previous()
        assertEquals("b", controller.queueState.value.current?.id)
    }

    @Test
    fun nextStopsAtEndWithoutRepeat() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)
        controller.setQueue(listOf(item("a"), item("b")))

        controller.next()
        controller.next()

        assertEquals("b", controller.queueState.value.current?.id)
        assertEquals(listOf("a", "b"), engine.prepared.map { it.id })
    }

    @Test
    fun repeatAllWrapsAround() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)
        controller.setQueue(listOf(item("a"), item("b")))
        controller.setRepeatMode(RepeatMode.ALL)

        controller.next()
        controller.next()

        assertEquals("a", controller.queueState.value.current?.id)
        assertEquals(listOf("a", "b", "a"), engine.prepared.map { it.id })
    }

    @Test
    fun playItemEnqueuesWhenAbsent() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)
        controller.setQueue(listOf(item("a")))

        controller.playItem(item("z"))

        assertEquals(2, controller.queueState.value.items.size)
        assertEquals("z", controller.queueState.value.current?.id)
        assertEquals("z", engine.prepared.last().id)
    }

    @Test
    fun toggleShuffleKeepsCurrentSelected() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)
        controller.setQueue(listOf(item("a"), item("b"), item("c")), startIndex = 1)

        controller.toggleShuffle()

        assertTrue(controller.queueState.value.isShuffled)
        assertEquals("b", controller.queueState.value.current?.id)
    }

    @Test
    fun toggleMuteFlipsMutedState() {
        val engine = FakePlayerEngine()
        val controller = controller(engine)

        assertFalse(controller.state.value.isMuted)
        controller.toggleMute()
        assertTrue(controller.state.value.isMuted)
    }

    @Test
    fun prepareReplaysSavedResumePosition() = runBlocking {
        val engine = FakePlayerEngine()
        val settings = MapSettings()
        PlaybackResumeStore(settings).save("a", 4_200L, 90_000L)
        val controller = controller(engine, settings)

        controller.setQueue(listOf(item("a"), item("b")))

        assertEquals(4_200L, engine.prepared.last().resumePositionMs)
    }

    @Test
    fun resumeIsSkippedForShortClipsAndPositionCleared() = runBlocking {
        val engine = FakePlayerEngine()
        val settings = MapSettings()
        val store = PlaybackResumeStore(settings)
        store.save("clip", 4_200L, 20_000L)
        val controller = controller(engine, settings)

        controller.setQueue(listOf(item("clip").copy(durationMs = 20_000L)))

        assertEquals(0L, engine.prepared.last().resumePositionMs)
        assertEquals(0L, store.load("clip"))
    }

    @Test
    fun clearResumePositionsWipesAllStoredPositions() {
        val engine = FakePlayerEngine()
        val settings = MapSettings()
        val store = PlaybackResumeStore(settings)
        store.save("a", 4_200L, 90_000L)
        store.save("b", 8_000L, 90_000L)
        val controller = controller(engine, settings)

        controller.clearResumePositions()

        assertEquals(0L, store.load("a"))
        assertEquals(0L, store.load("b"))
    }

    @Test
    fun skipToNextAdvancesQueueWithoutCallerScope() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)
        controller.setQueue(listOf(item("a"), item("b")))

        controller.skipToNext()

        val current = controller.queueState.value.current
        assertEquals("b", current?.id)
        assertFalse(controller.queueState.value.isShuffled)
    }

    @Test
    fun resumeIsSkippedWhenDisabledInSettings() = runBlocking {
        val engine = FakePlayerEngine()
        val settings = MapSettings()
        settings.putBoolean(PlayerPreferences.KEY_RESUME_PLAYBACK, false)
        PlaybackResumeStore(settings).save("a", 4_200L, 90_000L)
        val controller = controller(engine, settings)

        controller.setQueue(listOf(item("a")))

        assertEquals(0L, engine.prepared.last().resumePositionMs)
    }

    @Test
    fun setResumeEnabledAppliesImmediatelyWithoutRestart() = runBlocking {
        val engine = FakePlayerEngine()
        val settings = MapSettings()
        val resumeStore = PlaybackResumeStore(settings)
        resumeStore.save("a", 4_200L, 90_000L)
        resumeStore.save("b", 9_100L, 90_000L)
        val controller = controller(engine, settings)

        controller.setResumeEnabled(false)
        controller.setQueue(listOf(item("a")))
        assertEquals(0L, engine.prepared.last().resumePositionMs)

        controller.setResumeEnabled(true)
        controller.setQueue(listOf(item("b")))
        assertEquals(9_100L, engine.prepared.last().resumePositionMs)
    }

    @Test
    fun resumeIsSkippedForLiveStreams() = runBlocking {
        val engine = FakePlayerEngine()
        val settings = MapSettings()
        PlaybackResumeStore(settings).save("live", 4_200L, 0L)
        val controller = controller(engine, settings)

        controller.setQueue(listOf(
            item("live").copy(isLive = true, durationMs = 0L)
        ))

        assertEquals(0L, engine.prepared.last().resumePositionMs)
    }

    @Test
    fun saveCurrentPositionPersistsMediaPosition() = runBlocking {
        val engine = FakePlayerEngine()
        val settings = MapSettings()
        val store = PlaybackResumeStore(settings)
        val controller = controller(engine, settings)

        controller.playItem(item("a"))
        engine.setState(
            PlaybackState(
                mediaId = "a",
                positionMs = 8_900L,
                durationMs = 60_000L
            )
        )

        controller.saveCurrentPosition()

        assertEquals(8_900L, store.load("a"))
    }

    @Test
    fun queueIsPersistedAndRestoredWithoutAutoPlay() = runBlocking {
        val settings = MapSettings()
        val queueStore = PlayQueueStore(settings, Json)
        queueStore.save(
            PlayQueueSnapshot(
                items = listOf(item("a"), item("b"), item("c")),
                currentIndex = 1,
                repeatMode = RepeatMode.ALL,
                isShuffled = true
            )
        )

        val engine = FakePlayerEngine()
        val controller = controller(engine, settings)

        val restored = controller.queueState.value
        assertEquals(listOf("a", "b", "c"), restored.items.map { it.id })
        assertEquals(1, restored.currentIndex)
        assertEquals(RepeatMode.ALL, restored.repeatMode)
        assertTrue(restored.isShuffled)
        assertTrue(engine.prepared.isEmpty(), "restoring must not auto-play")
    }

    @Test
    fun clearingQueueClearsPersistedState() = runBlocking {
        val settings = MapSettings()
        val queueStore = PlayQueueStore(settings, Json)
        val controller = controller(FakePlayerEngine(), settings)
        controller.setQueue(listOf(item("a"), item("b")))
        assertTrue(queueStore.load() != null)

        controller.clearQueue()

        assertTrue(controller.queueState.value.isEmpty)
        assertEquals(null, queueStore.load())
    }

    @Test
    fun moveItemIgnoresOutOfBoundsAndEqualIndexes() = runBlocking {
        val controller = controller(FakePlayerEngine())
        controller.setQueue(listOf(item("a"), item("b"), item("c")), startIndex = 1)

        controller.moveItem(0, 99)
        controller.moveItem(-1, 1)
        controller.moveItem(1, 1)

        assertEquals(listOf("a", "b", "c"), controller.queueState.value.items.map { it.id })
        assertEquals("b", controller.queueState.value.current?.id)
    }

    @Test
    fun moveItemReordersPersistsAndKeepsCurrentValid() = runBlocking {
        val settings = MapSettings()
        val queueStore = PlayQueueStore(settings, Json)
        val controller = controller(FakePlayerEngine(), settings)
        controller.setQueue(listOf(item("a"), item("b"), item("c")), startIndex = 1)

        controller.moveItem(0, 2)

        val state = controller.queueState.value
        assertEquals(listOf("b", "c", "a"), state.items.map { it.id })
        assertEquals("b", state.current?.id)
        assertEquals(0, state.currentIndex)
        val persisted = queueStore.load()
        assertEquals(listOf("b", "c", "a"), persisted?.items?.map { it.id })
        assertEquals(0, persisted?.currentIndex)
    }

    @Test
    fun removeItemDropsNonCurrentItemAndPersists() = runBlocking {
        val settings = MapSettings()
        val queueStore = PlayQueueStore(settings, Json)
        val controller = controller(FakePlayerEngine(), settings)
        controller.setQueue(listOf(item("a"), item("b"), item("c")), startIndex = 1)

        controller.removeItem(2)

        assertEquals(listOf("a", "b"), controller.queueState.value.items.map { it.id })
        assertEquals("b", controller.queueState.value.current?.id)
        assertEquals(listOf("a", "b"), queueStore.load()?.items?.map { it.id })
    }

    @Test
    fun removeItemOnCurrentItemKeepsPlaybackAliveAndQueueAdvances() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)
        controller.setQueue(listOf(item("a"), item("b"), item("c")), startIndex = 1)
        engine.play()

        controller.removeItem(1)

        assertEquals(listOf("a", "c"), controller.queueState.value.items.map { it.id })
        assertEquals("c", controller.queueState.value.current?.id)
        assertTrue(engine.state.value.isPlaying, "removing the current item must not stop playback")
    }

    @Test
    fun saveQueueAsPlaylistPersistsQueueAndFailsWhenEmpty() = runBlocking {
        val settings = MapSettings()
        val controller = controller(FakePlayerEngine(), settings)

        assertFalse(controller.saveQueueAsPlaylist(), "an empty queue cannot be saved")

        controller.setQueue(listOf(item("a"), item("b")))

        assertTrue(controller.saveQueueAsPlaylist())
        val stored = PlaylistStore(settings, Json).loadAll()?.playlists.orEmpty()
        assertEquals(1, stored.size)
        assertEquals(listOf("a", "b"), stored.first().items.map { it.id })
        assertEquals(1, controller.savedPlaylists.value.size)
    }

    @Test
    fun restorePlaylistReplacesQueueAndStartsFirstItem() = runBlocking {
        val settings = MapSettings()
        val saver = controller(FakePlayerEngine(), settings)
        saver.setQueue(listOf(item("a"), item("b")))
        saver.saveQueueAsPlaylist()
        val saved = saver.savedPlaylists.value.first()

        val engine = FakePlayerEngine()
        val restored = controller(engine, settings)
        restored.setQueue(listOf(item("x"), item("y")))

        restored.restorePlaylist(saved)

        assertEquals(listOf("a", "b"), restored.queueState.value.items.map { it.id })
        assertEquals("a", restored.queueState.value.current?.id)
        assertEquals("a", engine.prepared.last().id)
    }

    @Test
    fun skipUnavailableInQueueDropsMissingReferences() = runBlocking {
        val settings = MapSettings()
        val queueStore = PlayQueueStore(settings, Json)
        val controller = controller(FakePlayerEngine(), settings)
        val missing = MediaItem(id = "missing", title = "missing")
        controller.setQueue(listOf(item("a"), missing, item("c")))

        controller.skipUnavailableInQueue()

        assertEquals(listOf("a", "c"), controller.queueState.value.items.map { it.id })
        assertEquals("a", controller.queueState.value.current?.id)
        assertEquals(listOf("a", "c"), queueStore.load()?.items?.map { it.id })
    }

    @Test
    fun skipUnavailableInQueueReplaysNextWhenCurrentIsMissing() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)
        val missing = MediaItem(id = "missing", title = "missing")
        controller.setQueue(listOf(missing, item("a")))

        controller.skipUnavailableInQueue()

        assertEquals(listOf("a"), controller.queueState.value.items.map { it.id })
        assertEquals("a", controller.queueState.value.current?.id)
        assertEquals("a", engine.prepared.last().id)
    }

    @Test
    fun newItemsUsePersistedDefaultSpeed() = runBlocking {
        val engine = FakePlayerEngine()
        val settings = MapSettings()
        settings.putString(PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED, "2.0")
        val controller = controller(engine, settings)

        controller.setQueue(listOf(item("a")))

        assertEquals(2.0f, engine.speeds.last())
    }

    @Test
    fun defaultSpeedIsNormalWhenUnset() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)

        controller.playItem(item("a"))

        assertEquals(PlayerPreferences.DEFAULT_PLAYBACK_SPEED, engine.speeds.last())
    }

    @Test
    fun defaultSpeedAppliesToEveryNewItem() = runBlocking {
        val engine = FakePlayerEngine()
        val settings = MapSettings()
        settings.putString(PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED, "1.25")
        val controller = controller(engine, settings)

        controller.setQueue(listOf(item("a"), item("b"), item("c")))
        controller.next()
        controller.previous()

        assertEquals(listOf(1.25f, 1.25f, 1.25f), engine.speeds)
    }

    @Test
    fun newItemAfterSetDefaultSpeedUsesThatSpeed() = runBlocking {
        val engine = FakePlayerEngine()
        val controller = controller(engine)
        controller.setQueue(listOf(item("a")))

        controller.setDefaultSpeed(1.5f)
        controller.playItem(item("b"))

        assertEquals(1.5f, engine.speeds.last())
    }

    @Test
    fun setDefaultSpeedPersistsClampedCanonicalValue() {
        val settings = MapSettings()
        val controller = controller(FakePlayerEngine(), settings)

        controller.setDefaultSpeed(1.4f)

        assertEquals(
            "1.5",
            settings.getString(PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED, "")
        )
    }

    @Test
    fun corruptPersistedSpeedFallsBackToNormal() = runBlocking {
        val engine = FakePlayerEngine()
        val settings = MapSettings()
        settings.putString(PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED, "not-a-speed")
        val controller = controller(engine, settings)

        controller.setQueue(listOf(item("a")))

        assertEquals(PlayerPreferences.DEFAULT_PLAYBACK_SPEED, engine.speeds.last())
    }
}
