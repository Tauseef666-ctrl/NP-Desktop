/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.MediaStream
import kotlin.random.Random

/**
 * Runs the downloads of the application and reports their progress.
 *
 * A download can consist of several files (a video-only stream plus a separate audio file, for
 * example); the progress of one entry covers all of its files. The finished files are registered
 * in the [DownloadRepository] so that they can be played from the downloads section.
 */
object DownloadManager {

    /**
     * One active or finished download that is currently shown in the user interface.
     */
    data class Active(
        val id: String,
        val title: String,
        val label: String,
        val progress: Float
    )

    /**
     * A single file of a download.
     */
    private data class PlannedFile(
        val url: String,
        val fileName: String,
        val isAudio: Boolean
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = mutableMapOf<String, Job>()

    private val _active = MutableStateFlow<List<Active>>(emptyList())
    val active = _active.asStateFlow()

    /**
     * Enqueues a download of the given stream.
     *
     * @param item the library entry the download belongs to
     * @param title the display title of the download
     * @param label the quality and format description, for example `720p · mp4`
     * @param stream the video stream that is downloaded, or `null` for an audio only download
     * @param audio the separate audio file that is downloaded, or `null` when the video stream
     * already contains the audio or nothing but audio is requested
     * @param downloader the platform downloader that writes the files
     * @param repository the repository the finished files are registered in
     * @param onResult invoked exactly once with the path of the main file or the failure
     */
    fun start(
        item: ContentItem,
        title: String,
        label: String,
        stream: MediaStream?,
        audio: MediaStream?,
        downloader: MediaDownloader,
        repository: DownloadRepository,
        onResult: (Result<String>) -> Unit = {}
    ) {
        val files = buildList {
            stream?.let { add(PlannedFile(it.url, fileNameFor(title, it), false)) }
            audio?.let { add(PlannedFile(it.url, audioFileNameFor(title, it), true)) }
        }
        if (files.isEmpty()) {
            onResult(Result.failure(IllegalStateException("Nothing to download")))
            return
        }

        val id = "download-${Random.nextLong()}-${Random.nextInt()}"
        _active.update { current -> current + Active(id, title, label, 0f) }

        val audioPathForRepository: (List<String>) -> String? = { paths ->
            if (stream == null) {
                null
            } else {
                // The separate audio file is the one downloaded after the video stream.
                paths.getOrNull(1)
            }
        }

        jobs[id] = scope.launch {
            var error: Throwable? = null
            val paths = mutableListOf<String>()

            try {
                files.forEachIndexed { index, file ->
                    val path = downloader.download(
                        url = file.url,
                        fileName = file.fileName,
                        onProgress = { fraction ->
                            val overall = (index + fraction) / files.size
                            setActive(id, progress = overall.coerceIn(0f, 1f))
                        }
                    )
                    paths += path
                }
            } catch (exception: Throwable) {
                error = exception
            }

            _active.update { current -> current.filterNot { it.id == id } }
            jobs.remove(id)

            val failure = error
            val mainPath = paths.getOrNull(0)
            when {
                failure != null && mainPath == null -> {
                    Logger.w(messageString = "The download of '$title' failed", throwable = failure)
                    onResult(Result.failure(failure))
                }

                else -> {
                    if (mainPath != null) {
                        val quality = stream?.quality ?: audio?.quality ?: ""
                        val formatId = stream?.formatId ?: audio?.formatId ?: ""
                        repository.add(
                            item = item,
                            filePath = mainPath,
                            quality = quality,
                            formatId = formatId,
                            audioFilePath = audioPathForRepository(paths)
                        )
                    }
                    onResult(Result.success(mainPath ?: ""))
                }
            }
        }
    }

    private fun setActive(id: String, progress: Float) {
        _active.update { current ->
            current.map { if (it.id == id) it.copy(progress = progress) else it }
        }
    }

    /**
     * Builds the file name of a downloaded stream from its title and container.
     */
    fun fileNameFor(title: String, stream: MediaStream): String =
        "$title.${videoContainerFor(stream)}"

    /**
     * Builds the file name of a downloaded audio stream.
     */
    private fun audioFileNameFor(title: String, audio: MediaStream): String =
        "$title (audio).${audioContainerFor(audio)}"

    /**
     * Chooses the container extension of a video stream from its codec.
     */
    fun videoContainerFor(stream: MediaStream): String {
        val codec = stream.codec.lowercase()
        return when {
            codec.contains("h264") || codec.contains("avc") || codec.contains("mpeg4") -> "mp4"
            codec.contains("av01") || codec.contains("av1") -> "webm"
            codec.contains("vp9") || codec.contains("vp8") -> "webm"
            stream.formatId.contains("webm") -> "webm"
            codec.isEmpty() && stream.isVideoOnly -> "mp4"
            else -> "mp4"
        }
    }

    /**
     * Chooses the container extension of an audio stream from its codec.
     */
    fun audioContainerFor(audio: MediaStream): String {
        val codec = audio.codec.lowercase()
        return when {
            codec.contains("mp4a") || codec.contains("aac") -> "m4a"
            codec.contains("opus") || codec.contains("vorbis") -> "webm"
            streamFormatIsWebm(audio) -> "webm"
            else -> "m4a"
        }
    }

    private fun streamFormatIsWebm(stream: MediaStream) = stream.formatId.contains("webm")
}
