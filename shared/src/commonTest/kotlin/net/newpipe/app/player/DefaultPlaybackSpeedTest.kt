/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import net.newpipe.app.preferences.PlayerPreferences
import net.newpipe.app.screen.settings.playbackSpeedLabel
import net.newpipe.app.screen.settings.playbackSpeedOptions
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.player_default_speed_0_5
import newpipe.shared.generated.resources.player_default_speed_0_75
import newpipe.shared.generated.resources.player_default_speed_1_0
import newpipe.shared.generated.resources.player_default_speed_1_25
import newpipe.shared.generated.resources.player_default_speed_1_5
import newpipe.shared.generated.resources.player_default_speed_1_75
import newpipe.shared.generated.resources.player_default_speed_2_0

class DefaultPlaybackSpeedTest {

    @Test
    fun canonicalFormattingMatchesSupportedPresets() {
        assertEquals("0.5", PlaybackController.canonicalSpeed(0.5f))
        assertEquals("0.75", PlaybackController.canonicalSpeed(0.75f))
        assertEquals("1.0", PlaybackController.canonicalSpeed(1.0f))
        assertEquals("1.25", PlaybackController.canonicalSpeed(1.25f))
        assertEquals("1.5", PlaybackController.canonicalSpeed(1.5f))
        assertEquals("1.75", PlaybackController.canonicalSpeed(1.75f))
        assertEquals("2.0", PlaybackController.canonicalSpeed(2.0f))
    }

    @Test
    fun canonicalFormattingClampsToNearestPreset() {
        assertEquals("0.5", PlaybackController.canonicalSpeed(0.1f))
        assertEquals("1.5", PlaybackController.canonicalSpeed(1.4f))
        assertEquals("2.0", PlaybackController.canonicalSpeed(9f))
    }

    @Test
    fun readParsesSupportedValues() {
        assertEquals(0.5f, PlaybackController.resolveDefaultSpeed("0.5"))
        assertEquals(0.75f, PlaybackController.resolveDefaultSpeed("0.75"))
        assertEquals(1.25f, PlaybackController.resolveDefaultSpeed("1.25"))
        assertEquals(2.0f, PlaybackController.resolveDefaultSpeed("2.0"))
    }

    @Test
    fun readFallsBackToDefaultForBlankCorruptOrUnsupported() {
        assertEquals(
            PlayerPreferences.DEFAULT_PLAYBACK_SPEED,
            PlaybackController.resolveDefaultSpeed(null)
        )
        assertEquals(
            PlayerPreferences.DEFAULT_PLAYBACK_SPEED,
            PlaybackController.resolveDefaultSpeed("")
        )
        assertEquals(
            PlayerPreferences.DEFAULT_PLAYBACK_SPEED,
            PlaybackController.resolveDefaultSpeed("   ")
        )
        assertEquals(
            PlayerPreferences.DEFAULT_PLAYBACK_SPEED,
            PlaybackController.resolveDefaultSpeed("not-a-speed")
        )
        assertEquals(
            PlayerPreferences.DEFAULT_PLAYBACK_SPEED,
            PlaybackController.resolveDefaultSpeed("3.0")
        )
        assertEquals(
            PlayerPreferences.DEFAULT_PLAYBACK_SPEED,
            PlaybackController.resolveDefaultSpeed("-1")
        )
    }

    @Test
    fun nearestClampsToSupportedPreset() {
        assertEquals(0.5f, PlaybackController.nearestSupportedSpeed(0.1f))
        assertEquals(0.75f, PlaybackController.nearestSupportedSpeed(0.8f))
        assertEquals(1.0f, PlaybackController.nearestSupportedSpeed(1.05f))
        assertEquals(1.5f, PlaybackController.nearestSupportedSpeed(1.4f))
        assertEquals(2.0f, PlaybackController.nearestSupportedSpeed(9f))
        assertEquals(0.5f, PlaybackController.nearestSupportedSpeed(-4f))
        assertEquals(
            PlayerPreferences.DEFAULT_PLAYBACK_SPEED,
            PlaybackController.nearestSupportedSpeed(Float.NaN)
        )
    }

    @Test
    fun canonicalRoundTripsThroughResolve() {
        PlayerPreferences.SUPPORTED_PLAYBACK_SPEEDS.forEach { speed ->
            val canonical = PlaybackController.canonicalSpeed(speed)
            assertEquals(speed, PlaybackController.resolveDefaultSpeed(canonical))
        }
    }

    @Test
    fun speedOptionsPairCanonicalValuesWithLabels() {
        val options = playbackSpeedOptions()

        assertEquals(
            listOf("0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0"),
            options.map { it.value }
        )
        assertEquals(Res.string.player_default_speed_0_5, playbackSpeedLabel(0.5f))
        assertEquals(Res.string.player_default_speed_0_75, playbackSpeedLabel(0.75f))
        assertEquals(Res.string.player_default_speed_1_0, playbackSpeedLabel(1.0f))
        assertEquals(Res.string.player_default_speed_1_25, playbackSpeedLabel(1.25f))
        assertEquals(Res.string.player_default_speed_1_5, playbackSpeedLabel(1.5f))
        assertEquals(Res.string.player_default_speed_1_75, playbackSpeedLabel(1.75f))
        assertEquals(Res.string.player_default_speed_2_0, playbackSpeedLabel(2.0f))
    }

    @Test
    fun speedLabelsClampOutOfRangeValues() {
        assertEquals(Res.string.player_default_speed_0_5, playbackSpeedLabel(0.1f))
        assertEquals(Res.string.player_default_speed_2_0, playbackSpeedLabel(9f))
    }
}
