/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.preferences.HistoryPreferences
import net.newpipe.app.preferences.PlayerPreferences
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the History & privacy settings screen. The "Remember watch history" toggle gates
 * recording into [RecentlyPlayedStore] (read live by the player), the resume toggle reuses the same
 * `enable_playback_resume` key as Android, and the clear actions have real, immediate effects on the
 * stored history and resume positions.
 */
@KoinViewModel
class HistorySettingsViewModel(
    private val settings: Settings,
    private val recentlyPlayed: RecentlyPlayedStore,
    private val resumeStore: PlaybackResumeStore,
    private val playback: PlaybackController
) : ViewModel() {

    private val _watchHistoryEnabled = MutableStateFlow(
        settings.getBoolean(
            HistoryPreferences.KEY_WATCH_HISTORY,
            HistoryPreferences.DEFAULT_WATCH_HISTORY
        )
    )
    val watchHistoryEnabled: StateFlow<Boolean> = _watchHistoryEnabled.asStateFlow()

    private val _resumeEnabled = MutableStateFlow(
        settings.getBoolean(
            PlayerPreferences.KEY_RESUME_PLAYBACK,
            PlayerPreferences.DEFAULT_RESUME_PLAYBACK
        )
    )
    val resumeEnabled: StateFlow<Boolean> = _resumeEnabled.asStateFlow()

    // Dedicated scope (not viewModelScope) so mapping the live history works on any platform
    // dispatcher and does not require a Main dispatcher to merely construct the ViewModel.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** Live size of the stored "Recently played" history. */
    val historyCount: StateFlow<Int> = recentlyPlayed.entries
        .map { it.size }
        .stateIn(scope, SharingStarted.Eagerly, recentlyPlayed.entries.value.size)

    private val _resumeCount = MutableStateFlow(resumeStore.entries().size)
    val resumeCount: StateFlow<Int> = _resumeCount.asStateFlow()

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    /** Persist the watch-history recording toggle and apply it immediately. */
    fun setWatchHistoryEnabled(enabled: Boolean) {
        settings.putBoolean(HistoryPreferences.KEY_WATCH_HISTORY, enabled)
        _watchHistoryEnabled.value = enabled
    }

    /** Persist the resume toggle (same key as Android) and apply it to the running controller. */
    fun setResumeEnabled(enabled: Boolean) {
        playback.setResumeEnabled(enabled)
        _resumeEnabled.value = enabled
    }

    /** Forget the entire "Recently played" history. */
    fun clearWatchHistory() {
        recentlyPlayed.clear()
    }

    /** Forget every stored resume position. */
    fun clearResumePositions() {
        playback.clearResumePositions()
        _resumeCount.value = resumeStore.entries().size
    }

    /** Re-read the stored resume-position count (e.g. after returning to the screen). */
    fun refresh() {
        _resumeCount.value = resumeStore.entries().size
    }
}
