/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Singleton

/**
 * Holds the provider playlist currently selected for the playlist screen. The app keeps navigation
 * state in singletons rather than route arguments (see [net.newpipe.app.navigation.Navigator]), so a
 * content list selects a playlist here and then navigates to
 * [net.newpipe.app.navigation.Destination.Playlist].
 *
 * Distinct from the local saved-playlists screen (`screen.playlists`), which is backed by
 * [net.newpipe.app.player.PlaylistStore] rather than a provider.
 */
@Singleton
class PlaylistNavigation {

    data class Selection(val url: String, val title: String)

    private val _selected = MutableStateFlow<Selection?>(null)

    /** The playlist selected for display, or null when none was chosen yet. */
    val selected: StateFlow<Selection?> = _selected.asStateFlow()

    /** Remember the playlist to show on the playlist screen. */
    fun select(url: String, title: String) {
        if (url.isBlank()) return
        _selected.value = Selection(url = url, title = title)
    }

    /** Forget the current selection (e.g. after navigating away). */
    fun clear() {
        _selected.value = null
    }
}
