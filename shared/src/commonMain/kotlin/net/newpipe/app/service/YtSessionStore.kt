/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * The stored YouTube web session, used to lift the anonymous access limit of the service.
 */
interface YtSessionStore {

    /**
     * Whether a session is stored.
     */
    fun sessionExists(): Boolean

    /**
     * Parses a pasted Cookie header or cookies.txt blob and stores it; returns the number of
     * the stored cookies.
     */
    fun saveSession(paste: String): Int

    /**
     * Clears the stored session.
     */
    fun signOut()
}

/**
 * Creates the session store for the current platform.
 */
expect fun ytSessionStore(): YtSessionStore
