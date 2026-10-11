/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.PlaybackState
import net.newpipe.app.player.PlaybackStatus
import net.newpipe.app.player.PlayerQueueState
import net.newpipe.app.player.PlayerShortcuts
import net.newpipe.app.player.Quality
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.player.RepeatMode
import net.newpipe.app.preferences.VideoAudioPreferences
import org.koin.core.annotation.KoinViewModel

/**
 * Thin screen-facing wrapper over the shared [PlaybackController]. Queue and engine live in the
 * controller so playback survives navigation between the player and other screens.
 *
 * Also the recording point for the "Recently played" history: the first time a new media id starts
 * PLAYING it is recorded once (via [RecentlyPlayedStore]) and its display metadata is remembered for
 * the "Continue watching" view (via [PlaybackResumeStore.rememberMetadata]).
 */
@KoinViewModel
class PlayerViewModel(
    private val playback: PlaybackController,
    private val settings: Settings,
    private val recentlyPlayed: RecentlyPlayedStore,
    private val resumeStore: PlaybackResumeStore
) : ViewModel() {

    val isEngineAvailable: Boolean = playback.isEngineAvailable
    val state: StateFlow<PlaybackState> = playback.state
    val queueState: StateFlow<PlayerQueueState> = playback.queueState

    private var lastRecordedId: String? = null

    // Dedicated scope (not viewModelScope) so recording works on any platform dispatcher and does
    // not require a Main dispatcher to merely construct the ViewModel.
    private val recordScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    init {
        recordScope.launch {
            playback.state.collect { current ->
                if (current.status != PlaybackStatus.PLAYING) return@collect
                val id = current.mediaId ?: return@collect
                if (id == lastRecordedId) return@collect
                // Prefer the queue's MediaItem for title/url/duration; skip until it is available.
                val item = playback.queueState.value.items.firstOrNull { it.id == id } ?: return@collect
                recentlyPlayed.record(item)
                resumeStore.rememberMetadata(item.id, item.title, item.url)
                lastRecordedId = id
            }
        }
    }

    override fun onCleared() {
        recordScope.cancel()
        super.onCleared()
    }

    /** Milliseconds per seek (F2): the configured `seek_duration`, Shift = large skip. */
    fun seekStepMillis(isShiftPressed: Boolean): Long {
        val configuredMs = settings.getString(
            VideoAudioPreferences.KEY_SEEK_DURATION,
            VideoAudioPreferences.DEFAULT_SEEK_DURATION_MS
        ).toLongOrNull() ?: PlayerShortcuts.DEFAULT_SEEK_STEP_MS
        return PlayerShortcuts.seekStepMillis(isShiftPressed, configuredMs)
    }

    fun play() = playback.play()
    fun pause() = playback.pause()
    fun stop() = playback.stop()
    fun togglePlayPause() = playback.togglePlayPause()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun setSpeed(speed: Float) = playback.setSpeed(speed)
    fun setVolume(volume: Float) = playback.setVolume(volume)
    fun setMuted(muted: Boolean) = playback.setMuted(muted)
    fun toggleMute() = playback.toggleMute()
    fun setQuality(quality: Quality) = playback.setQuality(quality)

    fun setQueue(items: List<MediaItem>, startIndex: Int = 0) {
        viewModelScope.launch { playback.setQueue(items, startIndex) }
    }

    fun playItem(item: MediaItem) {
        viewModelScope.launch { playback.playItem(item) }
    }

    fun next() {
        viewModelScope.launch { playback.next() }
    }

    fun previous() {
        viewModelScope.launch { playback.previous() }
    }

    fun toggleShuffle() = playback.toggleShuffle()

    fun setRepeatMode(mode: RepeatMode) = playback.setRepeatMode(mode)
}
