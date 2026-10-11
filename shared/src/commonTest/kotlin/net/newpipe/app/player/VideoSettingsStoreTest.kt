/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoSettingsStoreTest {

    private fun store(settings: MapSettings = MapSettings()) =
        VideoSettingsStore(settings, Json)

    @Test
    fun saveAndLoadRoundTrip() {
        val s = store()
        val video = VideoSettings(speed = 1.5f, volumePercent = 40, muted = true, quality = Quality.Q720)
        s.save("m1", video)

        assertEquals(video, s.load("m1"))
    }

    @Test
    fun savingOneMediaDoesNotClobberAnother() {
        val s = store()
        s.save("m1", VideoSettings(speed = 1.25f))
        s.save("m2", VideoSettings(volumePercent = 30))

        assertEquals(1.25f, s.load("m1")?.speed ?: 0f, 0.001f)
        assertEquals(30, s.load("m2")?.volumePercent)
        assertEquals(setOf("m1", "m2"), s.loadAll().keys)
    }

    @Test
    fun updatingExistingMediaReplacesValues() {
        val s = store()
        s.save("m1", VideoSettings(speed = 1.5f, volumePercent = 20))
        s.save("m1", VideoSettings(speed = 2.0f, volumePercent = 80, muted = true))

        val loaded = s.load("m1")
        assertEquals(2.0f, loaded?.speed ?: 0f, 0.001f)
        assertEquals(80, loaded?.volumePercent)
        assertEquals(true, loaded?.muted)
        assertEquals(1, s.loadAll().size)
    }

    @Test
    fun clearRemovesOnlyThatMedia() {
        val s = store()
        s.save("m1", VideoSettings(volumePercent = 10))
        s.save("m2", VideoSettings(volumePercent = 90))

        s.clear("m1")

        assertNull(s.load("m1"))
        assertEquals(90, s.load("m2")?.volumePercent)
    }

    @Test
    fun clearAllRemovesEverything() {
        val settings = MapSettings()
        val s = VideoSettingsStore(settings, Json)
        s.save("m1", VideoSettings())
        s.save("m2", VideoSettings())
        s.clearAll()

        assertTrue(s.loadAll().isEmpty())
        assertNull(s.load())
        assertEquals("", settings.getString(VideoSettingsStore.KEY, ""))
    }

    @Test
    fun loadReturnsNullForBlankOrCorruptData() {
        val settings = MapSettings()
        settings.putString(VideoSettingsStore.KEY, "")
        assertNull(VideoSettingsStore(settings, Json).load())

        settings.putString(VideoSettingsStore.KEY, "not json {")
        assertNull(VideoSettingsStore(settings, Json).load())
    }

    @Test
    fun normalizeClampsSpeedAndVolume() {
        assertEquals(0.25f, normalize(VideoSettings(speed = 0.1f)).speed, 0.001f)
        assertEquals(3.0f, normalize(VideoSettings(speed = 5f)).speed, 0.001f)
        assertEquals(0, normalize(VideoSettings(volumePercent = 0)).volumePercent)
        assertEquals(100, normalize(VideoSettings(volumePercent = 150)).volumePercent)
    }

    @Test
    fun newPreferencesDefaultToNeutral() {
        val defaults = VideoSettings()
        assertEquals(false, defaults.autoplayNextEnabled)
        assertEquals(false, defaults.subtitlesEnabled)
        assertEquals(AUDIO_TRACK_AUTO, defaults.audioTrack)
    }

    @Test
    fun newPreferencesRoundTripThroughStore() {
        val s = store()
        val video = VideoSettings(
            autoplayNextEnabled = true,
            subtitlesEnabled = true,
            audioTrack = "en"
        )
        s.save("m1", video)

        val loaded = s.load("m1")
        assertEquals(true, loaded?.autoplayNextEnabled)
        assertEquals(true, loaded?.subtitlesEnabled)
        assertEquals("en", loaded?.audioTrack)
    }

    @Test
    fun normalizeFallsBackToAutoForBlankAudioTrack() {
        assertEquals(AUDIO_TRACK_AUTO, normalize(VideoSettings(audioTrack = "")).audioTrack)
        assertEquals(AUDIO_TRACK_AUTO, normalize(VideoSettings(audioTrack = "   ")).audioTrack)
        assertEquals("en", normalize(VideoSettings(audioTrack = " en ")).audioTrack)
    }
}
