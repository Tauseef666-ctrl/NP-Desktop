/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.player

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackState
import net.newpipe.app.player.PlayerEngine
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.PlayQueueStore
import net.newpipe.app.player.PlaybackStatus
import net.newpipe.app.player.Quality
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.player.VideoSettings
import net.newpipe.app.player.VideoSettingsStore
import net.newpipe.app.preferences.VideoAudioPreferences

private class StubPlayerEngine : PlayerEngine {
    override val isAvailable: Boolean = true
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    override suspend fun prepare(item: MediaItem) {
        _state.value = PlaybackState(mediaId = item.id, status = PlaybackStatus.READY)
    }

    override fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun setSpeed(speed: Float) = Unit
    override fun setVolume(volume: Float) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun setQuality(quality: Quality) = Unit
    override fun release() = Unit
}

private fun playback(settings: MapSettings): PlaybackController =
        PlaybackController(
            StubPlayerEngine(),
            PlaybackResumeStore(settings),
            PlayQueueStore(settings, Json),
            settings
        )

private fun playerViewModel(
    settings: MapSettings,
    videoSettingsStore: VideoSettingsStore = VideoSettingsStore(settings, Json)
): PlayerViewModel =
        PlayerViewModel(
            playback(settings),
            settings,
            RecentlyPlayedStore(settings, Json),
            PlaybackResumeStore(settings),
            videoSettingsStore
        )

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun seekStepUsesConfiguredSkipIntervalFromSettings() {
        val settings = MapSettings()
        settings.putString(VideoAudioPreferences.KEY_SEEK_DURATION, "25000")
        val viewModel = playerViewModel(settings)

        assertEquals(25_000L, viewModel.seekStepMillis(isShiftPressed = false))
        assertEquals(60_000L, viewModel.seekStepMillis(isShiftPressed = true))
    }

    @Test
    fun seekStepFallsBackToDefaultWhenPreferenceIsUnsetOrGarbage() {
        val empty = MapSettings()
        assertEquals(10_000L, playerViewModel(empty).seekStepMillis(isShiftPressed = false))

        val garbage = MapSettings()
        garbage.putString(VideoAudioPreferences.KEY_SEEK_DURATION, "not-a-number")
        assertEquals(10_000L, playerViewModel(garbage).seekStepMillis(isShiftPressed = false))
    }

    @Test
    fun setAutoplayNextPersistsThroughStoreAndUpdatesFlow() {
        val settings = MapSettings()
        val store = VideoSettingsStore(settings, Json)
        val viewModel = playerViewModel(settings, store)

        viewModel.setAutoplayNext(true)

        assertEquals(true, viewModel.settings.value.autoplayNextEnabled)
        assertEquals(
            true,
            store.load(VideoSettingsStore.GLOBAL_MEDIA_ID)?.autoplayNextEnabled
        )
    }

    @Test
    fun settingsFlowIsSeededFromStore() {
        val settings = MapSettings()
        val store = VideoSettingsStore(settings, Json)
        store.save(
            VideoSettingsStore.GLOBAL_MEDIA_ID,
            VideoSettings(subtitlesEnabled = true, audioTrack = "en")
        )

        val viewModel = playerViewModel(settings, store)

        assertEquals(true, viewModel.settings.value.subtitlesEnabled)
        assertEquals("en", viewModel.settings.value.audioTrack)
    }

    @Test
    fun setSubtitlesPersistsThroughStore() {
        val settings = MapSettings()
        val store = VideoSettingsStore(settings, Json)
        val viewModel = playerViewModel(settings, store)

        viewModel.setSubtitles(true)

        assertEquals(true, viewModel.settings.value.subtitlesEnabled)
        assertEquals(true, store.load(VideoSettingsStore.GLOBAL_MEDIA_ID)?.subtitlesEnabled)
    }

    @Test
    fun setAudioTrackPersistsThroughStore() {
        val settings = MapSettings()
        val store = VideoSettingsStore(settings, Json)
        val viewModel = playerViewModel(settings, store)

        viewModel.setAudioTrack("en")

        assertEquals("en", viewModel.settings.value.audioTrack)
        assertEquals("en", store.load(VideoSettingsStore.GLOBAL_MEDIA_ID)?.audioTrack)
    }

    @Test
    fun panelExpandedDefaultsToCollapsed() {
        val viewModel = playerViewModel(MapSettings())

        assertEquals(false, viewModel.panelExpanded.value)
    }

    @Test
    fun togglePanelExpandedFlipsState() {
        val viewModel = playerViewModel(MapSettings())

        viewModel.togglePanelExpanded()
        assertEquals(true, viewModel.panelExpanded.value)

        viewModel.togglePanelExpanded()
        assertEquals(false, viewModel.panelExpanded.value)
    }

    @Test
    fun setPanelExpandedSetsAbsoluteState() {
        val viewModel = playerViewModel(MapSettings())

        viewModel.setPanelExpanded(true)
        assertEquals(true, viewModel.panelExpanded.value)

        viewModel.setPanelExpanded(false)
        assertEquals(false, viewModel.panelExpanded.value)
    }
}
