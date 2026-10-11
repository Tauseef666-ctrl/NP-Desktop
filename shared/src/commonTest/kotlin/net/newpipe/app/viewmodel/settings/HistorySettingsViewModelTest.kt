/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.PlaybackState
import net.newpipe.app.player.PlayerEngine
import net.newpipe.app.player.PlayQueueStore
import net.newpipe.app.player.Quality
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.preferences.HistoryPreferences
import net.newpipe.app.preferences.PlayerPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakePlayerEngine : PlayerEngine {
    override val isAvailable: Boolean = true

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override suspend fun prepare(item: MediaItem) = Unit
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

class HistorySettingsViewModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun item(id: String) = MediaItem(id = id, title = id, url = "file:///$id")

    private fun viewModel(
        settings: MapSettings = MapSettings(),
        recentlyPlayed: RecentlyPlayedStore = RecentlyPlayedStore(settings, json),
        resumeStore: PlaybackResumeStore = PlaybackResumeStore(settings)
    ): HistorySettingsViewModel {
        val controller = PlaybackController(
            FakePlayerEngine(),
            resumeStore,
            PlayQueueStore(settings, json),
            settings
        )
        return HistorySettingsViewModel(settings, recentlyPlayed, resumeStore, controller)
    }

    @Test
    fun watchHistoryDefaultsToEnabled() {
        val vm = viewModel()

        assertTrue(vm.watchHistoryEnabled.value)
    }

    @Test
    fun setWatchHistoryEnabledPersistsToSettings() {
        val settings = MapSettings()
        val vm = viewModel(settings = settings)

        vm.setWatchHistoryEnabled(false)
        assertFalse(vm.watchHistoryEnabled.value)
        assertFalse(
            settings.getBoolean(
                HistoryPreferences.KEY_WATCH_HISTORY,
                HistoryPreferences.DEFAULT_WATCH_HISTORY
            )
        )

        vm.setWatchHistoryEnabled(true)
        assertTrue(
            settings.getBoolean(
                HistoryPreferences.KEY_WATCH_HISTORY,
                HistoryPreferences.DEFAULT_WATCH_HISTORY
            )
        )
    }

    @Test
    fun resumeTogglePersistsViaPlayerPreferencesKey() {
        val settings = MapSettings()
        val vm = viewModel(settings = settings)

        assertTrue(vm.resumeEnabled.value)

        vm.setResumeEnabled(false)

        assertFalse(vm.resumeEnabled.value)
        assertFalse(
            settings.getBoolean(
                PlayerPreferences.KEY_RESUME_PLAYBACK,
                PlayerPreferences.DEFAULT_RESUME_PLAYBACK
            )
        )
    }

    @Test
    fun clearWatchHistoryEmptiesEntriesAndCount() {
        val settings = MapSettings()
        val recentlyPlayed = RecentlyPlayedStore(settings, json)
        recentlyPlayed.record(item("a"))
        recentlyPlayed.record(item("b"))
        val vm = viewModel(settings = settings, recentlyPlayed = recentlyPlayed)
        assertEquals(2, vm.historyCount.value)

        vm.clearWatchHistory()

        assertTrue(recentlyPlayed.entries.value.isEmpty())
        assertEquals(0, vm.historyCount.value)
    }

    @Test
    fun clearResumePositionsClearsStoreAndCount() {
        val settings = MapSettings()
        val resumeStore = PlaybackResumeStore(settings)
        resumeStore.save("a", 4_200L, 90_000L)
        resumeStore.save("b", 9_100L, 90_000L)
        val vm = viewModel(settings = settings, resumeStore = resumeStore)
        assertEquals(2, vm.resumeCount.value)

        vm.clearResumePositions()

        assertTrue(resumeStore.entries().isEmpty())
        assertEquals(0, vm.resumeCount.value)
    }

    @Test
    fun historyCountReflectsRecordedItems() {
        val recentlyPlayed = RecentlyPlayedStore(MapSettings(), json)
        val vm = viewModel(recentlyPlayed = recentlyPlayed)
        assertEquals(0, vm.historyCount.value)

        recentlyPlayed.record(item("a"))
        assertEquals(1, vm.historyCount.value)

        recentlyPlayed.record(item("b"))
        recentlyPlayed.record(item("c"))
        assertEquals(3, vm.historyCount.value)
    }
}
