/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * The iOS build has no extraction engine yet, so there is no session store.
 */
actual fun ytSessionStore(): YtSessionStore = object : YtSessionStore {

    override fun sessionExists(): Boolean = false

    override fun saveSession(paste: String): Int = 0

    override fun signOut() = Unit
}
