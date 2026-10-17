/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadStore
import net.newpipe.app.download.DownloadTask
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingNotifier : Notifier {
    override val isSupported: Boolean = true
    val calls = mutableListOf<Pair<String, String>>()

    override fun notify(title: String, body: String): Boolean {
        calls += title to body
        return true
    }
}

class JVMDownloadEngineTest {

    private val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempFile(name: String = "out.bin"): File {
        val dir = Files.createTempDirectory("np-download").toFile().also { tempDirs += it }
        return dir.resolve(name)
    }

    private fun server(chunkSize: Int = 64 * 1024, delayMs: Long = 0L): RawHttpServer =
        RawHttpServer(payload, chunkSize, delayMs).also { it.start() }

    private fun request(id: String, server: RawHttpServer, dest: File) = DownloadRequest(
        id = id,
        url = "http://127.0.0.1:${server.port}/file.bin",
        destinationPath = dest.absolutePath,
        title = id
    )

    private suspend fun JVMDownloadEngine.awaitTask(
        id: String,
        timeoutMs: Long,
        predicate: (DownloadTask) -> Boolean
    ): DownloadTask = withTimeout(timeoutMs) {
        tasks.first { list -> list.any { it.id == id && predicate(it) } }
            .first { it.id == id }
    }

    private fun engine(
        settings: MapSettings = MapSettings(),
        notifier: Notifier = NoopNotifier,
        notifications: NotificationsPreferences = NotificationsPreferences(settings)
    ): JVMDownloadEngine = JVMDownloadEngine(DownloadStore(settings, Json), notifier, notifications)

    @Test
    fun downloadsWholeFileAndOnlyThenCompletes() {
        val server = server()
        try {
            val dest = tempFile()
            val engine = engine()
            engine.enqueue(request("t1", server, dest))

            engine.start("t1")

            val done = runBlocking { engine.awaitTask("t1", 10_000) { it.status == DownloadState.COMPLETED } }
            assertEquals(payload.size.toLong(), done.totalBytes)
            assertEquals(dest.absolutePath, done.filePath)
            assertTrue(dest.readBytes().contentEquals(payload))
        } finally {
            server.close()
        }
    }

    @Test
    fun postsNotificationOnCompletion() {
        val server = server()
        try {
            val dest = tempFile()
            val notifier = RecordingNotifier()
            val engine = engine(notifier = notifier)
            engine.enqueue(request("t9", server, dest))

            engine.start("t9")

            runBlocking { engine.awaitTask("t9", 10_000) { it.status == DownloadState.COMPLETED } }
            val deadline = System.currentTimeMillis() + 2_000
            while (notifier.calls.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10)
            }
            assertEquals(1, notifier.calls.size, "completion must notify exactly once")
            assertEquals("Download complete", notifier.calls.single().first)
            assertEquals("t9", notifier.calls.single().second)
        } finally {
            server.close()
        }
    }

    @Test
    fun skipsNotificationWhenDisabled() {
        val server = server()
        try {
            val dest = tempFile()
            val notifier = RecordingNotifier()
            val preferences = NotificationsPreferences(MapSettings()).apply { setEnabled(false) }
            val engine = engine(notifier = notifier, notifications = preferences)
            engine.enqueue(request("t10", server, dest))

            engine.start("t10")

            runBlocking { engine.awaitTask("t10", 10_000) { it.status == DownloadState.COMPLETED } }
            Thread.sleep(100)
            assertTrue(notifier.calls.isEmpty(), "disabled notifications must not be posted")
        } finally {
            server.close()
        }
    }

    @Test
    fun resumesFromExistingPartFileViaRange() {        val server = server()
        try {
            val dest = tempFile()
            File(dest.absolutePath + ".part").writeBytes(payload.copyOfRange(0, 20_000))
            val engine = engine()
            engine.enqueue(request("t2", server, dest))

            engine.start("t2")

            runBlocking { engine.awaitTask("t2", 10_000) { it.status == DownloadState.COMPLETED } }
            assertTrue(dest.readBytes().contentEquals(payload), "resumed file must match the source")
        } finally {
            server.close()
        }
    }

    @Test
    fun pauseKeepsPartFileAndResumeCompletesIt() {
        val server = server(chunkSize = 4096, delayMs = 20)
        try {
            val dest = tempFile()
            val engine = engine()
            engine.enqueue(request("t3", server, dest))
            engine.start("t3")

            runBlocking { engine.awaitTask("t3", 10_000) { it.downloadedBytes > 0L } }
            engine.pause("t3")

            val paused = engine.tasks.value.first { it.id == "t3" }
            assertEquals(DownloadState.PAUSED, paused.status)
            val part = File(dest.absolutePath + ".part")
            assertTrue(part.exists() && part.length() > 0L, "paused download must keep its partial file")

            engine.start("t3")
            runBlocking { engine.awaitTask("t3", 15_000) { it.status == DownloadState.COMPLETED } }
            assertTrue(dest.readBytes().contentEquals(payload))
        } finally {
            server.close()
        }
    }

    @Test
    fun cancelStopsMissionAndDeletesPartialFile() {
        val server = server(chunkSize = 4096, delayMs = 20)
        try {
            val dest = tempFile()
            val engine = engine()
            engine.enqueue(request("t4", server, dest))
            engine.start("t4")

            runBlocking { engine.awaitTask("t4", 10_000) { it.downloadedBytes > 0L } }
            engine.cancel("t4")

            assertTrue(engine.tasks.value.none { it.id == "t4" }, "cancelled mission must be removed")
            val part = File(dest.absolutePath + ".part")
            val deadline = System.currentTimeMillis() + 5_000
            while (part.exists() && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            assertFalse(part.exists(), "cancelled download must delete its partial file")
            assertFalse(dest.exists(), "cancelled download must not leave a final file")
        } finally {
            server.close()
        }
    }

    @Test
    fun failedServerResponseIsReported() {
        val engine = engine()
        val dest = tempFile()
        engine.enqueue(
            DownloadRequest(
                id = "t5",
                url = "http://127.0.0.1:1/nothing",
                destinationPath = dest.absolutePath
            )
        )
        engine.start("t5")

        val failed = runBlocking { engine.awaitTask("t5", 20_000) { it.status == DownloadState.FAILED } }
        assertTrue(failed.error != null)
        assertFalse(dest.exists())
    }

    @Test
    fun pausedTaskSurvivesEngineRestart() {
        val server = server(chunkSize = 4096, delayMs = 20)
        try {
            val dest = tempFile()
            val settings = MapSettings()
            val first = engine(settings)
            first.enqueue(request("r1", server, dest))
            first.start("r1")

            runBlocking { first.awaitTask("r1", 10_000) { it.downloadedBytes > 0L } }
            first.pause("r1")
            assertEquals(DownloadState.PAUSED, first.tasks.value.first { it.id == "r1" }.status)

            val second = engine(settings)
            val restored = second.tasks.value.first { it.id == "r1" }
            assertEquals(DownloadState.PAUSED, restored.status, "paused mission must come back paused")
            assertTrue(restored.downloadedBytes > 0L, "recorded byte count must survive restart")
            assertTrue(File(dest.absolutePath + ".part").exists(), "partial file must survive restart")

            second.start("r1")
            runBlocking { second.awaitTask("r1", 15_000) { it.status == DownloadState.COMPLETED } }
            assertTrue(dest.readBytes().contentEquals(payload))
        } finally {
            server.close()
        }
    }

    @Test
    fun removingTaskAlsoClearsPersistedList() {
        val settings = MapSettings()
        val engine = engine(settings)
        val dest = tempFile()
        engine.enqueue(
            DownloadRequest(id = "r2", url = "http://127.0.0.1:1/x", destinationPath = dest.absolutePath)
        )
        engine.start("r2")
        runBlocking { engine.awaitTask("r2", 20_000) { it.status == DownloadState.FAILED } }
        engine.remove("r2")

        assertTrue(DownloadStore(settings, Json).load() == null, "empty mission list must clear storage")
    }
}

/**
 * Minimal HTTP/1.1 server for tests: serves [body] in [chunkSize] chunks with an optional delay and
 * honours `Range: bytes=start-` with `206 Partial Content`, so resume behaviour is exercised for
 * real (no mocking of the transfer).
 */
private class RawHttpServer(
    private val body: ByteArray,
    private val chunkSize: Int,
    private val chunkDelayMs: Long
) : AutoCloseable {

    private val serverSocket = ServerSocket(0)

    @Volatile
    private var running = true

    val port: Int get() = serverSocket.localPort

    fun start() {
        Thread {
            while (running) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: IOException) {
                    break
                }
                Thread { handle(socket) }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            try {
                val input = BufferedInputStream(client.getInputStream())
                readLine(input) ?: return
                var rangeStart = 0L
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeStart = line.substringAfter("bytes=").substringBefore('-').trim().toLongOrNull() ?: 0L
                    }
                }

                val start = rangeStart.coerceIn(0L, body.size.toLong())
                val slice = body.copyOfRange(start.toInt(), body.size)
                val out = BufferedOutputStream(client.getOutputStream())
                val headers = buildString {
                    append(if (start > 0L) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK").append("\r\n")
                    if (start > 0L) {
                        append("Content-Range: bytes $start-${body.size - 1}/${body.size}\r\n")
                    }
                    append("Content-Length: ${slice.size}\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(headers.toByteArray())

                var index = 0
                while (index < slice.size) {
                    val end = minOf(index + chunkSize, slice.size)
                    out.write(slice, index, end - index)
                    out.flush()
                    index = end
                    if (chunkDelayMs > 0L) Thread.sleep(chunkDelayMs)
                }
                out.flush()
            } catch (_: IOException) {
                // Client disconnected (pause/cancel) — nothing to do.
            }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (builder.isEmpty()) null else builder.toString()
            if (byte == '\n'.code) {
                if (builder.isNotEmpty() && builder.last() == '\r') builder.deleteCharAt(builder.length - 1)
                return builder.toString()
            }
            builder.append(byte.toChar())
        }
    }

    override fun close() {
        running = false
        serverSocket.close()
    }
}
