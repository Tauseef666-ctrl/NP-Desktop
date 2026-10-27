/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Downloads media streams with `HttpURLConnection` into `Videos/NewPipe` of the user home.
 */
actual class MediaDownloader actual constructor() {

    actual suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val directory = File(System.getProperty("user.home"), "Videos${File.separator}NewPipe")
        if (!directory.exists() && !directory.mkdirs()) {
            error("The downloads folder could not be created: ${directory.absolutePath}")
        }

        val target = uniqueFile(directory, sanitize(fileName))
        val partial = File(target.absolutePath + ".part")

        Logger.i(messageString = "Downloading $url to ${target.absolutePath}")

        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            setRequestProperty("Referer", "https://www.youtube.com/")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("The server answered with HTTP $responseCode")
            }

            val total = connection.contentLengthLong
            var copied = 0L

            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }

            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }

            Logger.i(messageString = "Download finished: ${target.absolutePath}")
            target.absolutePath
        } catch (exception: Exception) {
            partial.delete()
            Logger.e(messageString = "The download failed", throwable = exception)
            throw exception
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Removes characters that are not allowed in Windows file names.
     */
    private fun sanitize(name: String): String {
        val cleaned = name
            .replace(Regex("""[<>:"/\\|?*\u0000-\u001F]"""), "_")
            .trim()
            .trimEnd('.')
        return if (cleaned.isEmpty()) "video" else cleaned
    }

    /**
     * Adds a counter when a file with the same name already exists.
     */
    private fun uniqueFile(directory: File, name: String): File {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        val base = if (extension.isEmpty()) name else name.removeSuffix(".$extension")

        var candidate = File(directory, name)
        var index = 1
        while (candidate.exists()) {
            val suffix = if (extension.isEmpty()) "" else ".$extension"
            candidate = File(directory, "$base ($index)$suffix")
            index++
        }
        return candidate
    }
}
