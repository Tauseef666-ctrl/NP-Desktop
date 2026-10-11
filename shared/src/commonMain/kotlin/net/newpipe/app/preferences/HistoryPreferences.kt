/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

/**
 * Additive preference keys for watch-history recording. These are new keys only; no existing
 * NewPipe preference key is renamed or removed.
 */
object HistoryPreferences {
    /** Whether new plays are recorded into Recently played (F9/privacy). */
    const val KEY_WATCH_HISTORY = "npp_watch_history_enabled"
    const val DEFAULT_WATCH_HISTORY = true
}
