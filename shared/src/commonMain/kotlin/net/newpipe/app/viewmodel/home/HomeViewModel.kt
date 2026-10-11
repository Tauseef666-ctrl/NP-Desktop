/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.newpipe.app.download.DownloadEngine
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.screen.home.HomeRailItem
import net.newpipe.app.screen.home.HomeSection
import net.newpipe.app.screen.home.buildHomeSections
import org.koin.core.annotation.KoinViewModel

/**
 * Feeds the home rails from the persisted stores: resume positions (re-read via [refresh] since
 * [PlaybackResumeStore] is not observable), recently-played history, completed downloads and saved
 * playlists. Only non-empty rails are shown.
 */
@KoinViewModel
class HomeViewModel(
    recentlyPlayedStore: RecentlyPlayedStore,
    private val resumeStore: PlaybackResumeStore,
    downloadEngine: DownloadEngine,
    private val playback: PlaybackController
) : ViewModel() {

    private val resumeEntries = MutableStateFlow(resumeStore.entries())

    /** Non-empty home rails, recomputed whenever any persisted source changes. */
    val sections: StateFlow<List<HomeSection>> =
        combine(
            recentlyPlayedStore.entries,
            resumeEntries,
            downloadEngine.tasks,
            playback.savedPlaylists
        ) { recent, resume, tasks, playlists ->
            buildHomeSections(resume, recent, tasks, playlists)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildHomeSections(
                resume = resumeEntries.value,
                recent = recentlyPlayedStore.entries.value,
                downloads = downloadEngine.tasks.value,
                playlists = playback.savedPlaylists.value
            )
        )

    /** True when every source is empty, i.e. home has nothing honest to show. */
    val isEmpty: StateFlow<Boolean> = sections
        .map { it.isEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = sections.value.isEmpty()
        )

    /** Re-reads the (non-observable) resume store, e.g. when home is shown. */
    fun refresh() {
        resumeEntries.value = resumeStore.entries()
    }

    /**
     * Start playback of [item] when it has a real [HomeRailItem.url]; blank-url items are a no-op
     * (the resume store re-applies any saved position through the controller).
     */
    fun play(item: HomeRailItem) {
        if (item.url.isBlank()) return
        viewModelScope.launch {
            playback.playItem(
                MediaItem(
                    id = item.id,
                    title = item.title,
                    url = item.url
                )
            )
        }
    }
}