/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.component.StatusChip
import net.newpipe.app.component.StatusKind
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.player.AUDIO_TRACK_AUTO
import net.newpipe.app.player.AUDIO_TRACK_NONE
import net.newpipe.app.player.PlaybackState
import net.newpipe.app.player.Quality
import net.newpipe.app.player.RepeatMode
import net.newpipe.app.player.VideoSettings
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.theme.spaceXXSmall
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.player_active_settings
import newpipe.shared.generated.resources.player_audio_track
import newpipe.shared.generated.resources.player_autoplay_next
import newpipe.shared.generated.resources.player_collapse_settings
import newpipe.shared.generated.resources.player_expand_settings
import newpipe.shared.generated.resources.player_loop
import newpipe.shared.generated.resources.player_none_option
import newpipe.shared.generated.resources.player_quick_settings
import newpipe.shared.generated.resources.player_subtitles
import newpipe.shared.generated.resources.player_track_auto
import newpipe.shared.generated.resources.player_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * Always-visible quick-settings summary plus the expandable advanced panel.
 *
 * The summary chips are pure read-outs of the real state; the advanced panel holds the controls
 * that actually change it. Engine-rendered controls (subtitles, audio track) stay disabled and are
 * labelled "Unavailable" whenever the platform has no working playback engine.
 *
 * @param expanded Whether the advanced panel is shown; owned by the caller so it survives recomposition.
 * @param onToggleExpanded Flips [expanded].
 */
@Composable
fun PlayerQuickSettings(
    state: PlaybackState,
    repeatMode: RepeatMode,
    settings: VideoSettings,
    isEngineAvailable: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    onRepeatMode: (RepeatMode) -> Unit = {},
    onAutoplayNext: (Boolean) -> Unit = {},
    onSubtitles: (Boolean) -> Unit = {},
    onAudioTrack: (String) -> Unit = {},
    onSpeed: (Float) -> Unit = {},
    onVolume: (Float) -> Unit = {},
    onToggleMute: () -> Unit = {},
    onQuality: (Quality) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(Res.string.player_quick_settings))
        Text(
            text = stringResource(Res.string.player_active_settings),
            style = MaterialTheme.typography.labelSmall,
            color = plusColors().textMuted,
            modifier = Modifier.padding(horizontal = spaceLarge)
        )
        Spacer(Modifier.height(spaceSmall))
        QuickSettingsIndicators(
            state = state,
            repeatMode = repeatMode,
            settings = settings,
            isEngineAvailable = isEngineAvailable
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spaceLarge, vertical = spaceSmall),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onToggleExpanded) {
                Text(
                    stringResource(
                        if (expanded) Res.string.player_collapse_settings
                        else Res.string.player_expand_settings
                    )
                )
            }
        }
        if (expanded) {
            PlayerAdvancedSettings(
                settings = settings,
                repeatMode = repeatMode,
                isEngineAvailable = isEngineAvailable,
                state = state,
                onRepeatMode = onRepeatMode,
                onAutoplayNext = onAutoplayNext,
                onSubtitles = onSubtitles,
                onAudioTrack = onAudioTrack,
                onSpeed = onSpeed,
                onVolume = onVolume,
                onToggleMute = onToggleMute,
                onQuality = onQuality
            )
        }
    }
}

/** Active-state read-outs for speed / quality / loop / autoplay / subtitles / audio track. */
@Composable
private fun QuickSettingsIndicators(
    state: PlaybackState,
    repeatMode: RepeatMode,
    settings: VideoSettings,
    isEngineAvailable: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spaceLarge),
        horizontalArrangement = Arrangement.spacedBy(spaceNormal),
        verticalAlignment = Alignment.Top
    ) {
        SettingIndicator(
            label = "Speed",
            value = "${formatSpeedLabel(state.playbackSpeed)}×",
            active = state.playbackSpeed != 1f
        )
        SettingIndicator(
            label = "Quality",
            value = state.quality.label,
            active = state.quality != Quality.AUTO
        )
        SettingIndicator(
            label = stringResource(Res.string.player_loop),
            value = repeatLabel(repeatMode),
            active = repeatMode != RepeatMode.OFF
        )
        SettingIndicator(
            label = stringResource(Res.string.player_autoplay_next),
            value = boolLabel(settings.autoplayNextEnabled),
            active = settings.autoplayNextEnabled
        )
        SettingIndicator(
            label = stringResource(Res.string.player_subtitles),
            value = if (isEngineAvailable) {
                boolLabel(settings.subtitlesEnabled)
            } else {
                stringResource(Res.string.player_unavailable)
            },
            active = isEngineAvailable && settings.subtitlesEnabled,
            unavailable = !isEngineAvailable
        )
        SettingIndicator(
            label = stringResource(Res.string.player_audio_track),
            value = if (isEngineAvailable) {
                audioTrackLabel(settings.audioTrack)
            } else {
                stringResource(Res.string.player_unavailable)
            },
            active = isEngineAvailable && settings.audioTrack != AUDIO_TRACK_AUTO,
            unavailable = !isEngineAvailable
        )
    }
}

@Composable
private fun SettingIndicator(
    label: String,
    value: String,
    active: Boolean,
    unavailable: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = plusColors().textSecondary
        )
        Spacer(Modifier.height(spaceXXSmall))
        StatusChip(
            text = value,
            kind = when {
                unavailable -> StatusKind.WARNING
                active -> StatusKind.LIVE
                else -> StatusKind.INFO
            }
        )
    }
}

/** Expandable advanced controls: speed, volume, quality, loop, autoplay, subtitles and audio track. */
@Composable
fun PlayerAdvancedSettings(
    settings: VideoSettings,
    repeatMode: RepeatMode,
    isEngineAvailable: Boolean,
    modifier: Modifier = Modifier,
    state: PlaybackState = PlaybackState(),
    onRepeatMode: (RepeatMode) -> Unit = {},
    onAutoplayNext: (Boolean) -> Unit = {},
    onSubtitles: (Boolean) -> Unit = {},
    onAudioTrack: (String) -> Unit = {},
    onSpeed: (Float) -> Unit = {},
    onVolume: (Float) -> Unit = {},
    onToggleMute: () -> Unit = {},
    onQuality: (Quality) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = spaceLarge)) {
        Text(
            text = "Speed",
            style = MaterialTheme.typography.labelMedium,
            color = plusColors().textSecondary
        )
        Spacer(Modifier.height(spaceSmall))
        SettingsChipRow(
            options = SpeedOptions.map { "${formatSpeedLabel(it)}×" },
            selectedIndex = SpeedOptions.indexOfFirst { it == state.playbackSpeed }
                .coerceAtLeast(0),
            enabled = true,
            onSelect = { onSpeed(SpeedOptions[it]) }
        )
        Spacer(Modifier.height(spaceNormal))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Volume",
                style = MaterialTheme.typography.labelMedium,
                color = plusColors().textSecondary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onToggleMute) {
                Text(if (state.isMuted) "Unmute" else "Mute")
            }
        }
        Slider(
            value = state.volume,
            onValueChange = onVolume,
            valueRange = 0f..1f,
            enabled = !state.isMuted
        )
        Spacer(Modifier.height(spaceNormal))
        Text(
            text = "Quality",
            style = MaterialTheme.typography.labelMedium,
            color = plusColors().textSecondary
        )
        Spacer(Modifier.height(spaceSmall))
        SettingsChipRow(
            options = Quality.entries.map { it.label },
            selectedIndex = Quality.entries.indexOf(state.quality).coerceAtLeast(0),
            enabled = true,
            onSelect = { onQuality(Quality.entries[it]) }
        )
        Spacer(Modifier.height(spaceNormal))
        Text(
            text = stringResource(Res.string.player_loop),
            style = MaterialTheme.typography.labelMedium,
            color = plusColors().textSecondary
        )
        Spacer(Modifier.height(spaceSmall))
        SettingsChipRow(
            options = RepeatMode.entries.map(::repeatLabel),
            selectedIndex = repeatMode.ordinal,
            enabled = true,
            onSelect = { onRepeatMode(RepeatMode.entries[it]) }
        )
        Spacer(Modifier.height(spaceNormal))
        SwitchPreference(
            title = stringResource(Res.string.player_autoplay_next),
            checked = settings.autoplayNextEnabled,
            onCheckedChange = onAutoplayNext
        )
        SwitchPreference(
            title = stringResource(Res.string.player_subtitles),
            summary = if (isEngineAvailable) null else stringResource(Res.string.player_unavailable),
            checked = settings.subtitlesEnabled,
            enabled = isEngineAvailable,
            onCheckedChange = onSubtitles
        )
        Spacer(Modifier.height(spaceNormal))
        Text(
            text = stringResource(Res.string.player_audio_track),
            style = MaterialTheme.typography.labelMedium,
            color = plusColors().textSecondary
        )
        Spacer(Modifier.height(spaceSmall))
        val options = audioTrackOptions(settings.audioTrack)
        SettingsChipRow(
            options = options.map { audioTrackLabel(it) },
            selectedIndex = options.indexOf(settings.audioTrack).coerceAtLeast(0),
            enabled = isEngineAvailable,
            onSelect = { onAudioTrack(options[it]) }
        )
        if (!isEngineAvailable) {
            Spacer(Modifier.height(spaceXXSmall))
            StatusChip(
                text = stringResource(Res.string.player_unavailable),
                kind = StatusKind.WARNING
            )
        }
    }
}

@Composable
private fun SettingsChipRow(
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    val colors = plusColors()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spaceSmall)
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                enabled = enabled,
                label = { Text(option) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceElevated,
                    labelColor = colors.textSecondary,
                    selectedContainerColor = colors.accentMuted,
                    selectedLabelColor = colors.accent
                )
            )
        }
    }
}

@Composable
private fun audioTrackLabel(track: String): String = when (track) {
    AUDIO_TRACK_AUTO -> stringResource(Res.string.player_track_auto)
    AUDIO_TRACK_NONE -> stringResource(Res.string.player_none_option)
    else -> track
}

private fun audioTrackOptions(current: String): List<String> {
    val base = listOf(AUDIO_TRACK_AUTO, AUDIO_TRACK_NONE)
    return if (current.isBlank() || current in base) base else base + current
}

private fun repeatLabel(mode: RepeatMode): String =
    mode.name.lowercase().replaceFirstChar { it.uppercase() }

private fun boolLabel(value: Boolean): String = if (value) "On" else "Off"

private fun formatSpeedLabel(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

/** Playback speeds offered by the advanced panel (and cycled by the overlay speed button). */
private val SpeedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlayerQuickSettingsPreview() {
    PlayerQuickSettings(
        state = PlaybackState(playbackSpeed = 1.5f, quality = Quality.Q720),
        repeatMode = RepeatMode.ALL,
        settings = VideoSettings(
            autoplayNextEnabled = true,
            subtitlesEnabled = true,
            audioTrack = AUDIO_TRACK_AUTO
        ),
        isEngineAvailable = true,
        expanded = true,
        onToggleExpanded = {}
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlayerAdvancedSettingsUnavailablePreview() {
    PlayerAdvancedSettings(
        settings = VideoSettings(
            autoplayNextEnabled = true,
            subtitlesEnabled = true,
            audioTrack = AUDIO_TRACK_NONE
        ),
        repeatMode = RepeatMode.ONE,
        isEngineAvailable = false
    )
}
