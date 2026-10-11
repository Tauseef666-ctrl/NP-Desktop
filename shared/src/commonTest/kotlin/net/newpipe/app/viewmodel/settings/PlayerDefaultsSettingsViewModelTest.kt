/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import net.newpipe.app.player.FakePlayerEngine
import net.newpipe.app.player.PlayQueueStore
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.preferences.PlayerPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerDefaultsSettingsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun playback(settings: MapSettings): PlaybackController = PlaybackController(
        engine = FakePlayerEngine(),
        resumeStore = PlaybackResumeStore(settings),
        queueStore = PlayQueueStore(settings, Json),
        settings = settings
    )

    @Test
    fun initialValueReadsPersistedSetting() {
        val settings = MapSettings()
        settings.putString(PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED, "1.75")
        val vm = PlayerDefaultsSettingsViewModel(playback(settings))

        assertEquals("1.75", vm.defaultSpeed.value)
    }

    @Test
    fun initialValueFallsBackToNormalWhenUnset() {
        val vm = PlayerDefaultsSettingsViewModel(playback(MapSettings()))

        assertEquals("1.0", vm.defaultSpeed.value)
    }

    @Test
    fun initialValueFallsBackToNormalForCorruptSetting() {
        val settings = MapSettings()
        settings.putString(PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED, "corrupt")
        val vm = PlayerDefaultsSettingsViewModel(playback(settings))

        assertEquals("1.0", vm.defaultSpeed.value)
    }

    @Test
    fun setDefaultSpeedUpdatesFlowAndPersists() {
        val settings = MapSettings()
        val vm = PlayerDefaultsSettingsViewModel(playback(settings))

        vm.setDefaultSpeed("2.0")

        assertEquals("2.0", vm.defaultSpeed.value)
        assertEquals(
            "2.0",
            settings.getString(PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED, "")
        )
    }

    @Test
    fun setDefaultSpeedWithUnsupportedValueFallsBackToNormal() {
        val settings = MapSettings()
        val vm = PlayerDefaultsSettingsViewModel(playback(settings))

        vm.setDefaultSpeed("3.0")

        assertEquals("1.0", vm.defaultSpeed.value)
        assertEquals(
            "1.0",
            settings.getString(PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED, "")
        )
    }
}
