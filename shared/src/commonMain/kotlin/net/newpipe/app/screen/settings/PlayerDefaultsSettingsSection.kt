/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.preferences.PlayerPreferences
import net.newpipe.app.viewmodel.settings.PlayerDefaultsSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.player_default_speed_0_5
import newpipe.shared.generated.resources.player_default_speed_0_75
import newpipe.shared.generated.resources.player_default_speed_1_0
import newpipe.shared.generated.resources.player_default_speed_1_25
import newpipe.shared.generated.resources.player_default_speed_1_5
import newpipe.shared.generated.resources.player_default_speed_1_75
import newpipe.shared.generated.resources.player_default_speed_2_0
import newpipe.shared.generated.resources.player_default_speed_summary
import newpipe.shared.generated.resources.player_default_speed_title
import newpipe.shared.generated.resources.settings_category_playback_defaults_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** One selectable default-speed choice: its canonical stored [value] and its label resource. */
internal data class PlaybackSpeedOption(val value: String, val label: StringResource)

/**
 * Pure mapping from the supported presets to their canonical stored value and localized label.
 * Kept out of the composable so the value/label pairing is directly unit-testable.
 */
internal fun playbackSpeedOptions(): List<PlaybackSpeedOption> =
    PlayerPreferences.SUPPORTED_PLAYBACK_SPEEDS.map { speed ->
        PlaybackSpeedOption(
            value = PlaybackController.canonicalSpeed(speed),
            label = playbackSpeedLabel(speed)
        )
    }

/** Label resource for [speed], clamped to the nearest supported preset. */
internal fun playbackSpeedLabel(speed: Float): StringResource =
    when (PlaybackController.nearestSupportedSpeed(speed)) {
        0.5f -> Res.string.player_default_speed_0_5
        0.75f -> Res.string.player_default_speed_0_75
        1.0f -> Res.string.player_default_speed_1_0
        1.25f -> Res.string.player_default_speed_1_25
        1.5f -> Res.string.player_default_speed_1_5
        1.75f -> Res.string.player_default_speed_1_75
        else -> Res.string.player_default_speed_2_0
    }

/**
 * Playback-defaults settings section: picks the speed every newly started item plays at. The
 * stateless content is in [PlayerDefaultsSettingsSectionContent].
 */
@Composable
fun PlayerDefaultsSettingsSection(viewModel: PlayerDefaultsSettingsViewModel = koinViewModel()) {
    val defaultSpeed by viewModel.defaultSpeed.collectAsState()

    PlayerDefaultsSettingsSectionContent(
        defaultSpeed = defaultSpeed,
        onDefaultSpeedChange = viewModel::setDefaultSpeed
    )
}

@Composable
fun PlayerDefaultsSettingsSectionContent(
    defaultSpeed: String = PlaybackController.canonicalSpeed(PlayerPreferences.DEFAULT_PLAYBACK_SPEED),
    onDefaultSpeedChange: (String) -> Unit = {}
) {
    val options = playbackSpeedOptions()

    Column {
        PreferenceCategoryTitle(stringResource(Res.string.settings_category_playback_defaults_title))
        ListPreference(
            title = stringResource(Res.string.player_default_speed_title),
            entries = options.map { option ->
                ListPreferenceEntry(option.value, stringResource(option.label))
            },
            selectedValue = defaultSpeed,
            onValueSelected = onDefaultSpeedChange,
            summary = stringResource(Res.string.player_default_speed_summary)
        )
    }
}
