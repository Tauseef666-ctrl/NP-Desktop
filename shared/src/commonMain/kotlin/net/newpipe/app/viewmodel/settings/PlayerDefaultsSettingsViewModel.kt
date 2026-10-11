/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.player.PlaybackController
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the playback-defaults settings section: the default speed applied whenever a new item
 * starts. The choice is persisted through [PlaybackController] and surfaced as the canonical
 * speed string (e.g. `1.0`) so the UI can select it directly.
 */
@KoinViewModel
class PlayerDefaultsSettingsViewModel(
    private val playback: PlaybackController
) : ViewModel() {

    val defaultSpeed: StateFlow<String>
        field = MutableStateFlow(
            PlaybackController.canonicalSpeed(playback.defaultPlaybackSpeed)
        )

    fun setDefaultSpeed(value: String) {
        val speed = PlaybackController.resolveDefaultSpeed(value)
        playback.setDefaultSpeed(speed)
        defaultSpeed.value = PlaybackController.canonicalSpeed(speed)
    }
}
