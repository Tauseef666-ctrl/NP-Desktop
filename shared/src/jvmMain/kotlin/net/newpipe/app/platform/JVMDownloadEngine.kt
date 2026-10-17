/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.download.DownloadEngine
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadSnapshot
import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadStore
import net.newpipe.app.download.DownloadTask
import org.koin.core.annotation.Singleton
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Desktop [DownloadEngine] using [HttpURLConnection] and a sibling `.part` file.
 *
 * Resumability: a paused mission keeps its `.part` file; the next [start] issues an HTTP `Range`
 * request from the current part length, so only the missing bytes are fetched. The final file is
 * only created (by rename) once every byte arrived, so [DownloadState.COMPLETED] always implies a
 * complete file on disk.
 */
@Singleton(binds = [DownloadEngine::class])
class JVMDownloadEngine(
    private val store: DownloadStore,
    private val notifier: Notifier,
    private val notifications: NotificationsPreferences
) : DownloadEngine {

    override val isAvailable: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tasks = MutableStateFlow(
        store.load()?.let { DownloadStore.restoreTasks(it) } ?: emptyList()
    )
    override val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val cancelled = mutableSetOf<String>()

    override fun enqueue(request: DownloadRequest): DownloadTask {
        _tasks.value.firstOrNull { it.id == request.id }?.let { return it }
        val task = DownloadTask(request = request)
        _tasks.value = _tasks.value + task
        persist()
        return task
    }

    override fun start(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (jobs[id]?.isActive == true) return
        if (task.status == DownloadState.COMPLETED) return
        cancelled.remove(id)
        update(id) { it.copy(status = DownloadState.DOWNLOADING, error = null) }
        persist()
        val job = scope.launch { runDownload(task.request) }
        jobs[id] = job
        job.invokeOnCompletion { if (jobs[id] === job) jobs.remove(id) }
    }

    override fun pause(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.status != DownloadState.DOWNLOADING) return
        jobs[id]?.cancel()
        update(id) { it.copy(status = DownloadState.PAUSED, bytesPerSecond = 0L) }
        persist()
    }

    override fun cancel(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        val job = jobs[id]
        if (job?.isActive == true) {
            // The running coroutine closes its file handles on cancellation, then deletes the part
            // file (deleting while still open fails on Windows).
            cancelled += id
            job.cancel()
        } else {
            partFile(task.request).delete()
        }
        _tasks.value = _tasks.value.filterNot { it.id == id }
        persist()
    }

    override fun remove(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (!task.isTerminal) return
        _tasks.value = _tasks.value.filterNot { it.id == id }
        persist()
    }

    override fun clearCompleted() {
        _tasks.value = _tasks.value.filterNot { it.status == DownloadState.COMPLETED }
        persist()
    }

    private suspend fun runDownload(request: DownloadRequest) {
        val destination = File(request.destinationPath)
        destination.parentFile?.mkdirs()
        val part = partFile(request)
        val offset = if (part.exists()) part.length() else 0L

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
            }

            val code = connection.responseCode
            val partial = code == HttpURLConnection.HTTP_PARTIAL
            if (code !in 200..299) throw IOException("Server responded with HTTP $code")

            val reportedLength = connection.contentLengthLong
            val total = when {
                partial && reportedLength >= 0L -> offset + reportedLength
                reportedLength >= 0L -> reportedLength
                else -> 0L
            }
            var written = if (partial) offset else 0L

            update(request.id) {
                it.copy(
                    status = DownloadState.DOWNLOADING,
                    totalBytes = total,
                    downloadedBytes = written,
                    error = null
                )
            }

            connection.inputStream.use { input ->
                RandomAccessFile(part, "rw").use { raf ->
                    if (partial) raf.seek(offset) else raf.setLength(0L)
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastSampleBytes = written
                    var lastSampleTime = System.currentTimeMillis()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        raf.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastSampleTime >= PROGRESS_INTERVAL_MS) {
                            val speed = ((written - lastSampleBytes) * 1000L) / (now - lastSampleTime)
                            lastSampleBytes = written
                            lastSampleTime = now
                            update(request.id) {
                                it.copy(downloadedBytes = written, bytesPerSecond = speed)
                            }
                        }
                    }
                }
            }

            if (destination.exists()) destination.delete()
            if (!part.renameTo(destination)) {
                throw IOException("Downloaded file could not be finalized at ${destination.absolutePath}")
            }
            val finalSize = destination.length()
            update(request.id) {
                it.copy(
                    status = DownloadState.COMPLETED,
                    downloadedBytes = finalSize,
                    totalBytes = if (total > 0L) total else finalSize,
                    bytesPerSecond = 0L,
                    filePath = destination.absolutePath,
                    error = null
                )
            }
            notifyCompleted(request)
        } catch (cancellation: CancellationException) {
            // File/stream handles are now closed by the `use` blocks above, so deletion succeeds.
            if (cancelled.remove(request.id)) part.delete()
            throw cancellation
        } catch (t: Throwable) {
            update(request.id) {
                it.copy(
                    status = DownloadState.FAILED,
                    bytesPerSecond = 0L,
                    error = t.message ?: t::class.simpleName ?: "Download failed"
                )
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun partFile(request: DownloadRequest): File = File(request.destinationPath + PART_SUFFIX)

    /**
     * Posts a system notification when a download finishes, honoring the user's notification toggle.
     * The injected [notifier] decides whether the host can actually deliver; an unsupported host
     * simply returns `false`. Never throws.
     */
    private fun notifyCompleted(request: DownloadRequest) {
        if (!notifications.isEnabled()) return
        runCatching {
            notifier.notify(NOTIFICATION_TITLE, request.title.ifBlank { request.destinationPath })
        }
    }

    private fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
        persist()
    }

    private fun persist() {
        val snapshot = DownloadSnapshot(_tasks.value)
        if (snapshot.tasks.isEmpty()) {
            store.clear()
        } else {
            store.save(snapshot)
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 100L
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val PART_SUFFIX = ".part"
        const val NOTIFICATION_TITLE = "Download complete"
    }
}
