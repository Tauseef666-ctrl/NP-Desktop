/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.RecentlyPlayedEntry
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.screen.history.HistoryGroup
import net.newpipe.app.screen.history.groupHistory
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the History screen from the real [RecentlyPlayedStore]: day-grouped lists, per-item removal
 * and clear-all, plus replaying an entry through the shared [PlaybackController].
 */
@KoinViewModel
class HistoryViewModel(
    private val recentlyPlayedStore: RecentlyPlayedStore,
    private val playback: PlaybackController
) : ViewModel() {

    // Dedicated scope (not viewModelScope) so mapping live history works on any platform dispatcher
    // and does not require a Main dispatcher to merely construct the ViewModel.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** Day-grouped history, descending from TODAY to EARLIER, refreshed as entries change. */
    val groups: StateFlow<List<HistoryGroup>> = recentlyPlayedStore.entries
        .map { groupHistory(it, System.currentTimeMillis()) }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            groupHistory(recentlyPlayedStore.entries.value, System.currentTimeMillis())
        )

    /** True when the store holds no entries at all. */
    val isEmpty: StateFlow<Boolean> = recentlyPlayedStore.entries
        .map { it.isEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, recentlyPlayedStore.entries.value.isEmpty())

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    /** Forget the single history entry with [id]. */
    fun remove(id: String) = recentlyPlayedStore.remove(id)

    /** Forget the entire history. */
    fun clearAll() = recentlyPlayedStore.clear()

    /** Replay [entry] unless it has no playable url. */
    fun play(entry: RecentlyPlayedEntry) {
        if (entry.url.isBlank()) return
        viewModelScope.launch {
            playback.playItem(
                MediaItem(
                    id = entry.id,
                    title = entry.title,
                    url = entry.url,
                    durationMs = entry.durationMs
                )
            )
        }
    }
}