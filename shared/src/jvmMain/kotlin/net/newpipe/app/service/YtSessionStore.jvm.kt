/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * The desktop session store backed by the file under the user data folder.
 */
actual fun ytSessionStore(): YtSessionStore = object : YtSessionStore {

    override fun sessionExists(): Boolean = YtSession.exists()

    override fun saveSession(paste: String): Int = YtSession.save(paste)

    override fun signOut() = YtSession.signOut()
}
