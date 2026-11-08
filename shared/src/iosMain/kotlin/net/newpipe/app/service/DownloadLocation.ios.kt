/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * iOS download folder. Folder picking needs a document picker integration and is added together
 * with the iOS download implementation.
 */
actual object DownloadLocation {

    actual var directory: String? = null

    actual fun resolve(): String = directory ?: "Downloads/NewPipe"

    actual val pickerSupported: Boolean = false

    actual suspend fun choose(): Boolean = false
}
