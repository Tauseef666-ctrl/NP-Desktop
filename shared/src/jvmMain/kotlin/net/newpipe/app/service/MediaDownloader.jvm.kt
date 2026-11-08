/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Downloads media streams with `HttpURLConnection` into the chosen download folder.
 *
 * A video only stream and its audio file are merged into a single playable file with the static
 * ffmpeg binary that ships with JAVE2: the streams are copied without a re-encode, so the merge
 * only takes a few seconds.
 */
actual class MediaDownloader actual constructor() {

    actual suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit,
        onBytes: (copied: Long, total: Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val directory = File(DownloadLocation.resolve())
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
                            onBytes(copied, total)
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

    actual suspend fun merge(
        videoPath: String,
        audioPath: String,
        outputName: String
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(DownloadLocation.resolve())
            if (!directory.exists() && !directory.mkdirs()) {
                return@runCatching null
            }

            val video = File(videoPath)
            val audio = File(audioPath)
            if (!video.exists() || !audio.exists()) {
                return@runCatching null
            }

            val target = uniqueFile(directory, sanitize(outputName))
            val faststart = target.name.endsWith(".mp4")
            val command = mutableListOf(
                DefaultFFMPEGLocator().getExecutablePath(),
                "-nostdin",
                "-y",
                "-hide_banner",
                "-loglevel", "error",
                "-i", video.absolutePath,
                "-i", audio.absolutePath,
                "-c", "copy"
            )
            // The mp4 index is moved to the front, so the file starts playing at once
            if (faststart) {
                command += listOf("-movflags", "+faststart")
            }
            command += target.absolutePath

            Logger.i(messageString = "Merging into ${target.absolutePath}")

            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            // Reading the output before waiting for the exit avoids a pipe full of stream
            // blocking the process
            val output = process.inputStream.bufferedReader().readText()
            val exited = process.waitFor(180, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                Logger.w(messageString = "The merge did not finish in time")
                target.delete()
                return@runCatching null
            }

            val success = process.exitValue() == 0 && target.exists() && target.length() > 0L
            if (!success) {
                Logger.w(
                    messageString = "The merge failed with exit ${process.exitValue()}: $output"
                )
                target.delete()
                return@runCatching null
            }

            video.delete()
            audio.delete()
            Logger.i(messageString = "Merged video and audio into ${target.absolutePath}")
            target.absolutePath
        }.onFailure { failure ->
            Logger.w(messageString = "The merge failed", throwable = failure)
        }.getOrNull()
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
