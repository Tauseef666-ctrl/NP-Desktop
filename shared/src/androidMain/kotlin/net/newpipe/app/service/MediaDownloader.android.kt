/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * Downloads are not implemented on Android in the shared desktop build yet.
 */
actual class MediaDownloader actual constructor() {

    actual suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): String = error("Downloads are not supported on this platform yet")
}
