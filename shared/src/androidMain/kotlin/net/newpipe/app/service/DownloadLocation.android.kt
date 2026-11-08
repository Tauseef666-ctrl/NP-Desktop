/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * Android download folder. The picker needs the storage access framework flow and is added
 * together with the Android download implementation; the standard app specific downloads
 * directory is used until then.
 */
actual object DownloadLocation {

    actual var directory: String? = null

    actual fun resolve(): String = directory ?: "Downloads/NewPipe"

    actual val pickerSupported: Boolean = false

    actual suspend fun choose(): Boolean = false
}
