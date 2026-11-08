/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import java.io.File

private val MEDIA_EXTENSIONS = setOf("mp4", "mkv", "webm", "m4a")

/**
 * Scans the configured downloads folder on the desktop and imports the videos of every previous
 * version of the app (the naming scheme has been stable across releases) into the local library:
 * plain `title.mp4` files, duplicate `title (1).mp4` copies and the intermediate
 * `title (video).webm` + `title (audio).m4a` pairs of an interrupted merge.
 */
actual suspend fun synchronizeDownloadLibrary(repository: DownloadRepository): Int =
    withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(DownloadLocation.resolve())
            if (!directory.exists()) return@runCatching 0

            val candidates = directory
                .listFiles { file -> file.isFile && file.extension.lowercase() in MEDIA_EXTENSIONS }
                ?.sortedByDescending { it.lastModified() }
                ?: return@runCatching 0

            val known = knownPaths(repository)
            var imported = 0

            // "(audio)" files of an interrupted merge are attached to their video sibling; only
            // audio files without a video stay in the list as stand-alone audio downloads.
            val audioFiles = candidates
                .filter { it.nameWithoutExtension.endsWith(" (audio)") }
                .associateBy { it.nameWithoutExtension.removeSuffix(" (audio)") }
            val pairedAudio = mutableSetOf<String>()

            val videos = candidates.filter { !it.nameWithoutExtension.endsWith(" (audio)") }
            for (file in videos) {
                val base = file.nameWithoutExtension
                val videoBase = base.removeSuffix(" (video)")
                val audio = audioFiles[videoBase]?.let { videoPath(it, pairedAudio) }

                import(repository, known, file.absolutePath, videoBase) { audio }
                imported++
            }

            audioFiles.forEach { (base, file) ->
                if (file.absolutePath !in pairedAudio) {
                    import(repository, known, file.absolutePath, base) { null }
                    imported++
                }
            }

            if (imported > 0) {
                Logger.i(messageString = "Recognised $imported downloads from the folder")
            }
            imported
        }.getOrDefault(0)
    }

private fun videoPath(audio: File, paired: MutableSet<String>): String {
    paired += audio.absolutePath
    return audio.absolutePath
}

private fun import(
    repository: DownloadRepository,
    known: MutableSet<String>,
    filePath: String,
    baseName: String,
    audio: () -> String?
) {
    if (filePath in known) return
    val extension = filePath.substringAfterLast('.', "")
    repository.add(
        item = ContentItem(
            serviceId = 0,
            url = "",
            title = cleanTitle(baseName),
            type = ContentType.STREAM
        ),
        filePath = filePath,
        quality = extension.uppercase(),
        formatId = extension,
        audioFilePath = audio()
    )
    known += filePath
}

private fun knownPaths(repository: DownloadRepository): MutableSet<String> {
    val paths = mutableSetOf<String>()
    repository.downloads.value.forEach { download ->
        paths += download.filePath
        download.audioFilePath?.let { paths += it }
    }
    return paths
}

/**
 * Rebuilds a display title from a stored file base name: the "(video)"/"(audio)" markers and the
 * "(1)" style duplicate counters of the downloaded file names are stripped.
 */
private fun cleanTitle(base: String): String {
    var title = base
        .replace(Regex(""" \((audio|video)\)$"""), "")
        .replace(Regex(""" \(\d+\)$"""), "")
    if (title.isBlank()) title = base
    return title
}
