/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * Downloads a media stream to the local storage.
 *
 * The implementation is platform specific: the desktop writes the file to the videos folder of the
 * user, the other platforms currently do not support downloads.
 */
expect class MediaDownloader() {

    /**
     * Downloads [url] into the downloads folder and returns the absolute path of the saved file.
     *
     * @param fileName the preferred file name, including the extension
     * @param onProgress receives the progress between 0 and 1
     */
    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): String
}
