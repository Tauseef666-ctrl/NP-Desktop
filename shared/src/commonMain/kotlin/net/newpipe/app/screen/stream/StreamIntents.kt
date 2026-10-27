/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.stream

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

    /**
     * Asks the watch page to carry out the action on the stream with the given url after it has
     * loaded the details.
     */
    fun request(url: String, action: Action) {
        pendingUrl = url
        pendingAction = action
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
