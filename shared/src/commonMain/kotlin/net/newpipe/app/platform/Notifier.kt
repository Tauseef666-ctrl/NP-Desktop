/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

/**
 * Platform seam for posting a user-visible notification (e.g. "download complete").
 *
 * Implementations must degrade honestly: when the host has no notification facility the
 * implementation reports [isSupported] `false` and [notify] is a no-op returning `false`, so the
 * UI can show an honest "unavailable" state instead of a dead toggle.
 */
interface Notifier {
    /** Whether this host can actually deliver notifications right now. */
    val isSupported: Boolean

    /**
     * Post a notification. Returns `true` when it was handed to the platform, `false` when the host
     * is unsupported or delivery failed. Never throws.
     */
    fun notify(title: String, body: String): Boolean
}

/** A [Notifier] that never delivers anything; used where no platform facility exists. */
object NoopNotifier : Notifier {
    override val isSupported: Boolean = false
    override fun notify(title: String, body: String): Boolean = false
}
