/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * The folder downloads are saved in.
 *
 * The user picks the folder once in the download dialog; the choice is stored on the platform and
 * used for every following download. When no folder was chosen, each platform falls back to its
 * default (on the desktop `Videos/NewPipe` in the user home).
 */
expect object DownloadLocation {

    /**
     * The folder the user picked, or `null` when the platform default is used.
     */
    var directory: String?

    /**
     * The folder downloads are written to right now.
     */
    fun resolve(): String

    /**
     * Opens the platform folder picker and stores the choice.
     *
     * @return `true` when a folder was picked (and stored), `false` when picking was cancelled
     * or the platform has no picker
     */
    suspend fun choose(): Boolean

    /**
     * Whether the platform offers a folder picker; the change button of the download dialog is
     * only shown when this is `true`.
     */
    val pickerSupported: Boolean
}
