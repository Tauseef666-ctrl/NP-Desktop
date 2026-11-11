/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

/**
 * A tiny localhost media server that turns the extraction engine's SABR streaming (which hands
 * out media as protocol segments instead of plain urls) into a plain progressive HTTP stream a
 * media player can open.
 *
 * YouTube forces its SABR delivery on several of its player clients — including the `mweb`
 * client that is the only one which still opens "made for kids" videos without a browser
 * attestation. The extraction engine can drive such a stream segment by segment, but the player
 * only understands urls; this server bridges the two by driving a real SABR session per open
 * connection and relaying the received bytes, so that every SABR stream (kids videos included)
 * becomes a playable media url on `127.0.0.1`.
 *
 * The byte layout of the relayed segments is exactly the layout of the underlying media file:
 * the segment headers declare their byte ranges in the full file, which is what makes plain
 * HTTP range requests servable here.
 */
object SabrMediaServer {

    private val started = AtomicBoolean(false)

    @Volatile
    private var port = 0

    /** The SABR infos and formats that stream urls have been requested for, by track key. */
    private val tracks = ConcurrentHashMap<String, Pair<YoutubeSabrInfo, YoutubeSabrInfo.Format>>()

    /** The upper bound on relayed segment rounds per connection; VOD streams end on their own. */
    private const val MAX_SEGMENT_ROUNDS = 4_000

    /**
     * How often a connection may recycle its SABR session. YouTube demands a browser
     * attestation ("PO token") after a few streamed rounds; a fresh session streams again
     * without one, so the relay keeps restarting sessions to finish a video pot-less.
     */
    private const val MAX_SESSION_RESTARTS = 120

    /**
     * The bytes collected before the response starts: warming a SABR session up takes the
     * engine several seconds of YouTube round trips, so the exchange only answers once enough
     * media is in hand — from then on the stream pours at LAN speed and the player never sees
     * the slow start.
     */
    private const val PRE_BUFFER_BYTES = 1_500_000L

    /**
     * Returns a local media url that plays [format] out of the SABR stream described by
     * [info], starting the server on first use.
     */
    fun urlFor(info: YoutubeSabrInfo, format: YoutubeSabrInfo.Format): String {
        ensureStarted()
        val videoId = info.videoId.orEmpty().ifEmpty { "unknown" }
        val key = "${videoId}_${format.itag}"
        tracks[key] = info to format
        return "http://127.0.0.1:$port/sabr/$key"
    }

    /**
     * The media bytes the relay has delivered so far for the track addressed by a relay [url].
     * This is the honest health metric of the SABR route (the media really flowing to the
     * player) and is read by playback diagnostics and the live probes.
     */
    fun deliveredBytesFor(url: String): Long =
        url.substringAfter("/sabr/", "").let { key ->
            deliveredBytes[key]?.get() ?: 0L
        }

    private val deliveredBytes = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()

    private fun ensureStarted() {
        if (started.compareAndSet(false, true)) {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.executor = Executors.newFixedThreadPool(4) { runnable ->
                Thread(runnable, "sabr-media-relay").apply { isDaemon = true }
            }
            server.createContext("/sabr") { exchange -> handle(exchange) }
            server.start()
            port = server.address.port
        }
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val key = exchange.requestURI.path.removePrefix("/sabr/").takeIf { it.isNotBlank() }
                ?: throw IOException("no track requested")
            val (info, format) = tracks[key]
                ?: throw IOException("track $key was never registered")
            val offset = requestedOffset(exchange) ?: 0L
            relay(exchange, key, info, format, offset)
        } catch (failure: IOException) {
            // The player closed the connection; every relay below already handles its cleanup.
            Logger.d(messageString = "SABR relay closed: ${failure.message}")
        } catch (failure: Exception) {
            Logger.w(messageString = "SABR relay failed", throwable = failure)
            runCatching {
                if (exchange.responseCode == -1) {
                    exchange.sendResponseHeaders(502, -1)
                }
            }
        } finally {
            runCatching { exchange.close() }
        }
    }

    /**
     * Drives a fresh SABR session for this connection and writes the received media bytes to
     * the exchange body, from the requested byte [offset] onwards. The response headers go out
     * only after the first [PRE_BUFFER_BYTES] are collected, so the player sees a stream that
     * flows continuously instead of the SABR session's slow start.
     */
    private fun relay(
        exchange: HttpExchange,
        key: String,
        info: YoutubeSabrInfo,
        format: YoutubeSabrInfo.Format,
        offset: Long
    ) {
        val body = exchange.responseBody
        val byteCounter = deliveredBytes.computeIfAbsent(key) {
            java.util.concurrent.atomic.AtomicLong()
        }
        var session = YoutubeSabrSession(info)
        installReloader(session)
        val bitrate = format.bitrate.takeIf { it > 0 } ?: 500_000
        var currentMs = (offset * 8_000.0 / bitrate).toLong()
        var lastSequence = -1
        var rounds = 0
        var restarts = 0
        var consecutiveFailures = 0
        var headersSent = false
        var initDelivered = false
        var clientGone = false
        val preBuffer = java.io.ByteArrayOutputStream()
        var buffered = 0L
        // The segment timeline parsed from the initialization segment; the SABR protocol needs
        // it (and the highest buffered sequence) so the server knows where the player is.
        var timeline: org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormatTimeline? =
            null

        fun sendHeaders() {
            exchange.responseHeaders.set("Content-Type", contentTypeOf(format))
            exchange.responseHeaders.set("Accept-Ranges", "bytes")
            if (offset > 0L) {
                exchange.responseHeaders.set("Content-Range", "bytes $offset-/*")
                exchange.sendResponseHeaders(206, 0)
            } else {
                exchange.sendResponseHeaders(200, 0)
            }
            headersSent = true
        }

        fun deliver(bytes: ByteArray, from: Int, length: Int) {
            byteCounter.addAndGet(length.toLong())
            try {
                if (!headersSent) {
                    preBuffer.write(bytes, from, length)
                    buffered += length
                    if (buffered >= PRE_BUFFER_BYTES) {
                        sendHeaders()
                        preBuffer.writeTo(body)
                        preBuffer.reset()
                        buffered = 0L
                    }
                } else {
                    body.write(bytes, from, length)
                }
                consecutiveFailures = 0
            } catch (failure: IOException) {
                // The player went away; the relay loop must not keep streaming to nobody.
                clientGone = true
                throw failure
            }
        }

        fun acceptInit(segment: SabrMediaSegment) {
            // The initialization segment is only ever served from the file start, and only
            // once per connection even when the SABR session is recycled mid-stream.
            if (offset != 0L) return
            if (!initDelivered) {
                deliver(segment.data, 0, segment.data.size)
                initDelivered = true
            }
            timeline = runCatching {
                org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormatTimeline
                    .parse(format, segment.data)
            }.getOrNull()
            // A range request starts mid-file: snap the playback time to the exact start of the
            // segment the timeline places there, instead of trusting the bitrate estimate.
            timeline?.let { parsed ->
                val sequence = parsed.getSequenceAt(currentMs)
                parsed.getStartMs(sequence).takeIf { it >= 0 }?.let { currentMs = it }
            }
        }

        fun prepareSession() {
            session.requestOnce(
                YoutubeSabrRequest.preparation(0L, listOf(format))
            ) { segment ->
                if (segment.header.isInitSegment) {
                    acceptInit(segment)
                }
            }
        }

        try {
            prepareSession()
            while (rounds++ < MAX_SEGMENT_ROUNDS && restarts < MAX_SESSION_RESTARTS) {
                val track = YoutubeSabrRequest.Track.of(format, timeline, lastSequence.coerceAtLeast(0))
                val outcome = runCatching {
                    session.requestOnce(
                        YoutubeSabrRequest.playback(currentMs, 1.0f, listOf(track))
                    ) { segment ->
                    if (segment.hasFailed()) return@requestOnce
                    if (segment.header.isInitSegment) {
                        acceptInit(segment)
                        return@requestOnce
                    }
                    if (segment.header.sequenceNumber <= lastSequence) return@requestOnce
                    lastSequence = segment.header.sequenceNumber
                    val duration = segment.header.durationMs
                    if (duration > 0) {
                        currentMs = segment.header.startMs + duration
                    }
                    val startRange = segment.header.startRange
                    when {
                        startRange + segment.length <= offset -> return@requestOnce
                        startRange < offset -> {
                            // The segment straddles the requested offset: skip its leading bytes.
                            val skip = (offset - startRange).toInt()
                            deliver(segment.data, skip, segment.length - skip)
                        }
                        else -> deliver(segment.data, 0, segment.data.size)
                    }
                }
                }
                if (outcome.isFailure) {
                    val failure = outcome.exceptionOrNull()!!
                    Logger.d(
                        messageString = "SABR relay round failed: " +
                            (failure.message ?: failure.javaClass.simpleName)
                    )
                    if (clientGone) break
                    // The session died (typically "SABR attestation required" after a few
                    // rounds); a fresh session streams again pot-less. Delivery position is
                    // preserved, so the player sees one continuous stream.
                    restarts++
                    consecutiveFailures++
                    if (restarts >= MAX_SESSION_RESTARTS || consecutiveFailures > 5) break
                    runCatching {
                        // A wall usually means this stream generation is exhausted: reload the
                        // stream info (a fresh player response with a fresh streaming url) and
                        // restart on it; falling back to the stale info still gives the session
                        // host rotation and a clean protocol state.
                        val fresh = runCatching {
                            org.schabi.newpipe.extractor.services.youtube.extractors
                                .YoutubeStreamExtractor.fetchSabrInfoForReload(
                                    info.videoId,
                                    ""
                                )
                        }.getOrNull()
                        session = YoutubeSabrSession(fresh ?: info)
                        installReloader(session)
                        prepareSession()
                    }
                    continue
                }
                val result = outcome.getOrThrow()
                val backoff = result.backoffMs
                if (backoff > 0) Thread.sleep(backoff.toLong())
                if (result.segmentCount == 0) {
                    // Empty rounds are the server's pacing wall, not the end of the video: the
                    // stream only truly ends when the parsed segment timeline is exhausted.
                    val reachedEnd = timeline != null &&
                        lastSequence >= timeline!!.endSequence &&
                        lastSequence > 0
                    if (reachedEnd) break
                    restarts++
                    consecutiveFailures++
                    if (restarts >= MAX_SESSION_RESTARTS || consecutiveFailures > 8) break
                    runCatching {
                        session = YoutubeSabrSession(info)
                        prepareSession()
                    }
                }
            }
            // A short track may finish inside the pre-buffer; flush whatever was collected.
            if (!headersSent) {
                sendHeaders()
                preBuffer.writeTo(body)
                preBuffer.reset()
            }
            // Closing the body signals the end of the file to the player.
            runCatching { body.flush() }
        } finally {
            runCatching { body.close() }
        }
    }

    /** The byte offset of a `Range` request header, or null when the head is requested. */
    private fun requestedOffset(exchange: HttpExchange): Long? {
        val range = exchange.requestHeaders.getFirst("Range") ?: return null
        val match = Regex("bytes=(\\d+)-").find(range) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    /**
     * Installs the callback the engine uses when the server requests a player reload: a fresh
     * player response is fetched (which carries a fresh streaming url) and hot-swapped into the
     * running session, keeping its playback state. Without this the session cannot survive the
     * server's mid-stream reload requests.
     */
    private fun installReloader(session: YoutubeSabrSession) {
        session.setInfoReloader { videoId, reloadToken ->
            org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
                .fetchSabrInfoForReload(videoId, reloadToken)
        }
    }

    /** The media type of the relayed track, mirroring what the SABR format declares. */
    private fun contentTypeOf(format: YoutubeSabrInfo.Format): String =
        format.mimeType?.takeIf { it.contains('/') }?.substringBefore(';')
            ?: when (format.toItagItem().mediaFormat) {
                org.schabi.newpipe.extractor.MediaFormat.M4A -> "audio/mp4"
                org.schabi.newpipe.extractor.MediaFormat.WEBMA,
                org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS -> "audio/webm"
                org.schabi.newpipe.extractor.MediaFormat.WEBM -> "video/webm"
                else -> "video/mp4"
            }
}
