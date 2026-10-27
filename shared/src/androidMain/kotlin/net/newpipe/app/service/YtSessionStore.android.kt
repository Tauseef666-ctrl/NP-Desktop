/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * The Android build uses the legacy application's downloader for now, so there is no session
 * store yet.
 */
actual fun ytSessionStore(): YtSessionStore = object : YtSessionStore {

    override fun sessionExists(): Boolean = false

    override fun saveSession(paste: String): Int = 0

    override fun signOut() = Unit
}
