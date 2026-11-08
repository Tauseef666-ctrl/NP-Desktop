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
import kotlin.time.TimeSource

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
        val progress: Float,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val speedBytesPerSec: Long = 0L,
        val etaSeconds: Long? = null,
        val merging: Boolean = false
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
        if (stream == null && audio == null) {
            onResult(Result.failure(IllegalStateException("Nothing to download")))
            return
        }

        // A video only stream together with audio: both files are downloaded first and then
        // merged into a single playable video file by the platform.
        val merging = stream != null && audio != null
        val mergeOutputName = if (merging) {
            "${title}.${mergedContainerFor(stream!!, audio!!)}"
        } else {
            null
        }

        val files = buildList {
            stream?.let {
                val videoName = if (merging) {
                    // The "(video)" file is only an intermediate and is removed after the merge
                    "$title (video).${videoContainerFor(it)}"
                } else {
                    fileNameFor(title, it)
                }
                add(PlannedFile(it.url, videoName, false))
            }
            audio?.let { add(PlannedFile(it.url, audioFileNameFor(title, it), true)) }
        }

        val speed = SpeedTracker()
        val id = "download-${Random.nextLong()}-${Random.nextInt()}"
        _active.update { current -> current + Active(id, title, label, 0f) }

        jobs[id] = scope.launch {
            var error: Throwable? = null
            val paths = mutableListOf<String>()

            try {
                files.forEachIndexed { index, file ->
                    speed.startFile()
                    val path = downloader.download(
                        url = file.url,
                        fileName = file.fileName,
                        onProgress = { fraction ->
                            val overall = (index + fraction) / files.size
                            setActive(id, progress = overall.coerceIn(0f, 1f))
                            applySpeed(id, speed)
                        },
                        onBytes = { copied, total ->
                            speed.observe(total)
                            speed.sample(copied)
                        }
                    )
                    paths += path
                }
            } catch (exception: Throwable) {
                error = exception
            }

            val failure = error
            var mainPath = paths.getOrNull(0)
            // Without a merge the registered entry points to the separate audio file, so both
            // files stay reachable in the downloads section.
            var registeredAudioPath: String? = if (stream != null && audio != null) {
                paths.getOrNull(1)
            } else {
                null
            }

            if (failure == null && mergeOutputName != null) {
                setActive(id, progress = 1f, merging = true)
                downloader.merge(
                    videoPath = paths.getOrNull(0) ?: "",
                    audioPath = paths.getOrNull(1) ?: "",
                    outputName = mergeOutputName
                )?.let { mergedPath ->
                    // The single file replaces the two intermediate files
                    mainPath = mergedPath
                    registeredAudioPath = null
                }
            }

            _active.update { current -> current.filterNot { it.id == id } }
            jobs.remove(id)

            when {
                failure != null && mainPath == null -> {
                    Logger.w(messageString = "The download of '$title' failed", throwable = failure)
                    onResult(Result.failure(failure))
                }

                mainPath == null -> {
                    onResult(Result.failure(IllegalStateException("Nothing was downloaded")))
                }

                else -> {
                    val quality = stream?.quality ?: audio?.quality ?: ""
                    val formatId = stream?.formatId ?: audio?.formatId ?: ""
                    repository.add(
                        item = item,
                        filePath = mainPath,
                        quality = quality,
                        formatId = formatId,
                        audioFilePath = registeredAudioPath
                    )
                    onResult(Result.success(mainPath))
                }
            }
        }
    }

    private fun setActive(id: String, progress: Float, merging: Boolean = false) {
        _active.update { current ->
            current.map { if (it.id == id) it.copy(progress = progress, merging = merging) else it }
        }
    }

    private fun applySpeed(id: String, tracker: SpeedTracker) {
        _active.update { current ->
            current.map { active ->
                if (active.id != id) {
                    active
                } else {
                    active.copy(
                        downloadedBytes = tracker.downloadedBytes,
                        totalBytes = tracker.totalKnownBytes,
                        speedBytesPerSec = tracker.speedBytesPerSec,
                        etaSeconds = tracker.etaSeconds
                    )
                }
            }
        }
    }

    /**
     * Accumulates the byte level statistics of one download: how many bytes are already on
     * disk, how fast they arrive (an exponential moving average over the last samples) and how
     * long the download is likely to take.
     */
    private class SpeedTracker {

        private var baseBytes = 0L        // bytes of the files finished before the current one
        private var fileTotal = 0L        // size of the current file when the server reports it
        private var downloaded = 0L       // bytes across all files at the last sample
        private var lastBytes = 0L
        private var lastMark: TimeSource.Monotonic.ValueTimeMark? = null
        private var emaBytesPerSec = 0L
        private var counted = 0L          // samples accumulated into the moving average

        /**
         * Call once before each file of the download starts: the size of the previous file
         * becomes part of the base bytes.
         */
        fun startFile() {
            baseBytes += fileTotal
            fileTotal = 0L
            lastBytes = 0L
            lastMark = null
        }

        /** Remembers the size of the current file, reported with the first bytes. */
        fun observe(total: Long) {
            if (fileTotal == 0L) fileTotal = total
        }

        /**
         * Registers the copied bytes of the current file. The moving average is refreshed at
         * most twice per second so that noisy per-buffer samples do not flicker.
         */
        fun sample(copied: Long) {
            downloaded = baseBytes + copied
            val now = TimeSource.Monotonic.markNow()
            val mark = lastMark
            if (mark == null) {
                lastMark = now
                lastBytes = downloaded
                return
            }
            val elapsedMs = mark.elapsedNow().inWholeMilliseconds
            if (elapsedMs < 500) return
            val delta = downloaded - lastBytes
            if (delta <= 0L) return
            val instantaneous = delta * 1000L / elapsedMs
            val carrier = if (counted < 4L) counted + 1L else 4L
            emaBytesPerSec = (emaBytesPerSec * (carrier - 1L) + instantaneous) / carrier
            counted = carrier
            lastMark = now
            lastBytes = downloaded
        }

        val downloadedBytes: Long get() = downloaded

        /** Bytes of the files whose size is known; files not started are still unknown. */
        val totalKnownBytes: Long get() = baseBytes + fileTotal

        val speedBytesPerSec: Long get() = emaBytesPerSec

        val etaSeconds: Long?
            get() {
                val speed = emaBytesPerSec
                if (speed <= 0L) return null
                val remaining = totalKnownBytes - downloaded
                if (remaining <= 0L) return 0L
                return remaining / speed
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

    /**
     * Chooses the container of the merged file that combines a video only stream and its audio
     * file: matching pairs stay in their native container, everything else goes into the
     * matroska container which accepts every codec combination.
     */
    fun mergedContainerFor(video: MediaStream, audio: MediaStream): String {
        val video = videoContainerFor(video)
        val audio = audioContainerFor(audio)
        return when {
            video == "mp4" && audio == "m4a" -> "mp4"
            video == "webm" && audio == "webm" -> "webm"
            else -> "mkv"
        }
    }

    private fun streamFormatIsWebm(stream: MediaStream) = stream.formatId.contains("webm")
}
