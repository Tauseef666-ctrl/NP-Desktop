/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.newpipe.app.download.DownloadEngine
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.provider.MediaPlayback
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderPlaybackSession
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.ProviderResult
import net.newpipe.app.screen.home.HomeRailItem
import net.newpipe.app.screen.home.HomeSection
import net.newpipe.app.screen.home.buildHomeSections
import org.koin.core.annotation.KoinViewModel

/** What the home trending rail can honestly show right now. */
sealed interface HomeTrendingState {
    /** The first load has not been issued yet (previews, transient start). */
    data object Idle : HomeTrendingState

    /** The provider's trending page is being requested. */
    data object Loading : HomeTrendingState

    /** The provider delivered a ranked page; these items render as the front-page rail. */
    data class Items(val items: List<ProviderContentItem>) : HomeTrendingState

    /** Loading failed with a machine-readable reason for localized, honest UI text. */
    data class Failure(val reason: ProviderFailureReason) : HomeTrendingState
}

/**
 * Feeds the home rails from two honest sources: the live provider's trending page (the front-page
 * online content, requested once on construction) and the persisted stores — resume positions
 * (re-read via [refresh] since [PlaybackResumeStore] is not observable), recently-played history,
 * completed downloads and saved playlists. Only non-empty rails are shown.
 */
@KoinViewModel
class HomeViewModel(
    recentlyPlayedStore: RecentlyPlayedStore,
    private val resumeStore: PlaybackResumeStore,
    downloadEngine: DownloadEngine,
    private val playback: PlaybackController,
    private val repository: ProviderContentRepository,
    mediaPlayback: MediaPlayback
) : ViewModel() {

    private val resumeEntries = MutableStateFlow(resumeStore.entries())

    /** Non-empty local rails, recomputed whenever any persisted source changes. */
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

    /** True when every local store is empty, i.e. home has no local rails to show. */
    val isEmpty: StateFlow<Boolean> = sections
        .map { it.isEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = sections.value.isEmpty()
        )

    private val _trending = MutableStateFlow<HomeTrendingState>(HomeTrendingState.Loading)
    val trending: StateFlow<HomeTrendingState> = _trending.asStateFlow()

    private val playbackSession = ProviderPlaybackSession(mediaPlayback, viewModelScope)

    /** State of the most recent trending-tap play request, for navigation and honest errors. */
    val playbackState: StateFlow<ProviderPlaybackState> = playbackSession.state

    private var trendingJob: Job? = null

    init {
        loadTrending()
    }

    /**
     * Request the provider's trending page for the front-page rail. A load already in flight is
     * ignored so a rapid double-trigger cannot race; failures keep their reason so the UI can show
     * localized, honest text with a retry.
     */
    fun loadTrending() {
        if (trendingJob?.isActive == true) return
        _trending.value = HomeTrendingState.Loading
        trendingJob = viewModelScope.launch {
            _trending.value = when (val result = repository.trending()) {
                is ProviderResult.Success -> HomeTrendingState.Items(result.value.items)
                is ProviderResult.Failure -> HomeTrendingState.Failure(result.reason)
            }
        }
    }

    /** Retry the front-page trending load after a failure; re-queries the provider. */
    fun retryTrending() = loadTrending()

    /** Resolve and start [item] (a trending tap) through the shared playback session. */
    fun playTrending(item: ProviderContentItem) = playbackSession.play(item)

    /** Return trending playback state to idle after the UI handled a launched/failed request. */
    fun acknowledgePlayback() = playbackSession.acknowledge()

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
