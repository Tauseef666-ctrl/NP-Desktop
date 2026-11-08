/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.stream

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.model.ContentItem

/**
 * An action requested outside of the watch page, for example from the right-click menu on a video
 * card. The watch page picks the intent up after it has loaded the stream details and applies the
 * requested action.
 */
object StreamIntents {

    /** The actions that are carried out by the watch page. */
    enum class Action { PLAY, POPUP, BACKGROUND, DOWNLOAD }

    @Volatile
    private var pendingUrl: String? = null

    @Volatile
    private var pendingAction: Action? = null

    private val _pendingDownload = MutableStateFlow<ContentItem?>(null)

    /**
     * A download requested directly on a video card, without opening the watch page. The shell
     * picks it up, loads the stream details and opens the download options dialog.
     */
    val pendingDownload: StateFlow<ContentItem?> = _pendingDownload.asStateFlow()

    /**
     * Asks the watch page to carry out the action on the stream with the given url after it has
     * loaded the details.
     */
    fun request(url: String, action: Action) {
        pendingUrl = url
        pendingAction = action
    }

    /**
     * Requests the download options dialog for the video without opening the watch page.
     */
    fun requestDownload(item: ContentItem) {
        _pendingDownload.value = item
    }

    /**
     * Marks the download request as handled.
     */
    fun consumeDownload() {
        _pendingDownload.value = null
    }

    /**
     * Consumes the pending intent, if it matches the given url.
     */
    fun take(url: String): Action? {
        if (pendingUrl != url) return null
        val action = pendingAction
        clear()
        return action
    }

    /**
     * Drops every pending intent.
     */
    fun clear() {
        pendingUrl = null
        pendingAction = null
    }
}
