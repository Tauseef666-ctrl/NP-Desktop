/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.SavedPlaylist
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the local playlists screen: lists the playlists saved from the queue (F6), plays one by
 * restoring it into the shared [PlaybackController] queue, and deletes playlists.
 */
@KoinViewModel
class PlaylistsViewModel(private val playback: PlaybackController) : ViewModel() {

    /** Saved playlists, mirroring [PlaybackController.savedPlaylists]. */
    val playlists: StateFlow<List<SavedPlaylist>> = playback.savedPlaylists

    /** Restore [saved] into the queue and start its first item. */
    fun play(saved: SavedPlaylist) {
        viewModelScope.launch { playback.restorePlaylist(saved) }
    }

    /** Remove the playlist with [id]. Other playlists are untouched. */
    fun delete(id: String) = playback.deletePlaylist(id)
}
