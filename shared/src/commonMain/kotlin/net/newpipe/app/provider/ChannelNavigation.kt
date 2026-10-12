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
 * Holds the channel currently selected for the channel screen. The app keeps navigation state in
 * singletons rather than route arguments (see [net.newpipe.app.navigation.Navigator]), so a content
 * list selects a channel here and then navigates to [net.newpipe.app.navigation.Destination.Channels].
 */
@Singleton
class ChannelNavigation {

    data class Selection(val url: String, val name: String)

    private val _selected = MutableStateFlow<Selection?>(null)

    /** The channel selected for display, or null when none was chosen yet. */
    val selected: StateFlow<Selection?> = _selected.asStateFlow()

    /** Remember the channel to show on the channel screen. */
    fun select(url: String, name: String) {
        if (url.isBlank()) return
        _selected.value = Selection(url = url, name = name)
    }

    /** Forget the current selection (e.g. after navigating away). */
    fun clear() {
        _selected.value = null
    }
}
