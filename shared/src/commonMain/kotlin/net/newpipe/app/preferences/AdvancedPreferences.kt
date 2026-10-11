/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

/**
 * Additive preference keys for the Advanced & diagnostics settings screen. These are new keys only;
 * no existing NewPipe preference key is renamed or removed.
 */
object AdvancedPreferences {
    /** Persisted kermit log level as a [co.touchlab.kermit.Severity] name, e.g. "Info". */
    const val KEY_LOG_LEVEL = "npp_log_level"
}
