/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

/**
 * Additive preference keys for downloads and storage. These are new keys only; no existing NewPipe
 * preference key is renamed or removed.
 */
object DownloadsPreferences {
    /** Persisted override for the download directory; blank = use the platform default. */
    const val KEY_DOWNLOAD_DIRECTORY = "npp_default_download_directory"
    const val DEFAULT_DOWNLOAD_DIRECTORY = ""
}
