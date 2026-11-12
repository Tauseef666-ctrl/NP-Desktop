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
     * The empty playback rounds a connection rides out with the same session before it ends.
     * Brief gaps in the server's segment delivery are normal pacing, but each session's
     * pot-less media budget is finite and a stretch of empty rounds means the generation is
     * done: ending the connection keeps every byte it delivered valid, and the player
     * reconnects for a fresh generation.
     */
    private const val MAX_EMPTY_ROUNDS = 3

    /** The pause between empty playback rounds before the connection is given up. */
    private const val EMPTY_ROUND_DELAY_MS = 500L

    /**
     * How often a connection may throw the cached stream info away and fetch a fresh one when
     * YouTube walls the session before any media is served. Each fresh extraction carries a new
     * streaming url and host, and a fresh session regularly streams where the cached one was
     * denied; nothing has leaked to the player at that point, so the restart is byte safe.
     */
    private const val MAX_FRESH_EXTRACTION_ATTEMPTS = 2

    /**
     * The consecutive failed fresh extractions after which the relay stops trying them. When
     * YouTube's bot wall throttles the player requests themselves, every fresh extraction is
     * doomed and only burns seconds per connection before the connection can fail; once the
     * breaker is open the connection answers the player immediately. A success resets it.
     */
    private const val FRESH_EXTRACTION_FAILURE_LIMIT = 2

    @Volatile
    private var freshExtractionFailures = 0

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

    /**
     * When the most recent connection for a video ended without serving a single media byte:
     * YouTube walled the route. The wall is session wide, so it is tracked per video rather
     * than per track; the timestamp (0 when the route is healthy) lets the playback controller
     * tell a fresh wall from an old one.
     */
    private val walledVideos = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()

    /**
     * The video part of a relay [url] path: `videoId_itag` without the itag suffix. The itag
     * never contains an underscore, so the last segment always is the itag.
     */
    private fun videoKeyOf(url: String): String =
        url.substringAfter("/sabr/", "").substringBeforeLast("_")

    /**
     * Records that the video addressed by a relay url path [videoKey] is walled right now.
     */
    fun markVideoWalled(videoKey: String) {
        walledVideos.computeIfAbsent(videoKey) {
            java.util.concurrent.atomic.AtomicLong(0L)
        }.set(System.currentTimeMillis())
    }

    /**
     * Records that the video addressed by a full relay [url] is walled right now. The
     * playback controller calls this when its watchdog rescues a walled start: the relay's
     * own round may still be inside its long retry budget, but the rescue is the verdict.
     */
    fun markTrackWalled(url: String) {
        markVideoWalled(videoKeyOf(url))
    }

    /**
     * Whether the last connection for the video addressed by a relay [url] was walled.
     */
    fun isTrackWalled(url: String): Boolean =
        walledVideos[videoKeyOf(url)]?.get()?.let { it > 0L } ?: false

    /**
     * How long ago the video addressed by a relay [url] was walled, or null when its last
     * connection delivered media (or was never connected).
     */
    fun walledAgeMs(url: String): Long? =
        walledVideos[videoKeyOf(url)]?.get()
            ?.takeIf { it > 0L }
            ?.let { System.currentTimeMillis() - it }

    private fun ensureStarted() {
        if (started.compareAndSet(false, true)) {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            // VLC opens one connection per track (video and audio) and reconnects on hiccups;
        // four threads stalled on slow upstream responses once starved a fifth connection.
        server.executor = Executors.newFixedThreadPool(8) { runnable ->
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
        val bitrate = format.bitrate.takeIf { it > 0 } ?: 500_000

        var headersSent = false
        var clientGone = false
        // The media bytes (everything except the initialization segment) this connection has
        // delivered; the health signal that decides between a partial flush and a hard failure.
        var mediaBytes = 0L
        val preBuffer = java.io.ByteArrayOutputStream()
        var buffered = 0L

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
            } catch (failure: IOException) {
                // The player went away; the relay loop must not keep streaming to nobody.
                clientGone = true
                throw failure
            }
        }

        try {
            var activeInfo = info
            var freshAttempts = 0
            attempt@ while (true) {
                var currentMs = (offset * 8_000.0 / bitrate).toLong()
                var lastSequence = -1
                var rounds = 0
                var emptyRounds = 0
                var initDelivered = false
                // The segment timeline parsed from the initialization segment; the SABR protocol
                // needs it (and the highest buffered sequence) so the server knows where the
                // player is.
                var timeline: org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormatTimeline? =
                    null
                // Everything pre-buffered so far belongs to the previous session attempt; the
                // player has not seen any of it (the headers are still unsent), so it is dropped.
                preBuffer.reset()
                buffered = 0L

                val session = YoutubeSabrSession(activeInfo)
                installReloader(session)

                fun acceptInit(segment: SabrMediaSegment) {
                    // The initialization segment is only ever served from the file start, and
                    // only once per connection.
                    if (offset != 0L) return
                    if (!initDelivered) {
                        deliver(segment.data, 0, segment.data.size)
                        initDelivered = true
                    }
                    timeline = runCatching {
                        org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrFormatTimeline
                            .parse(format, segment.data)
                    }.getOrNull()
                    // A range request starts mid-file: snap the playback time to the exact start
                    // of the segment the timeline places there, instead of trusting the bitrate
                    // estimate.
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

                // A session can die in the preparation round already (YouTube's attestation
                // wall), so its outcome decides whether the playback rounds run at all.
                var walled = runCatching { prepareSession() }.isFailure
                if (!walled) {
                    while (rounds++ < MAX_SEGMENT_ROUNDS) {
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
                                        // The segment straddles the requested offset: skip its
                                        // leading bytes.
                                        val skip = (offset - startRange).toInt()
                                        mediaBytes += segment.length - skip
                                        deliver(segment.data, skip, segment.length - skip)
                                    }
                                    else -> {
                                        mediaBytes += segment.data.size
                                        deliver(segment.data, 0, segment.data.size)
                                    }
                                }
                            }
                        }
                        if (outcome.isFailure) {
                            val failure = outcome.exceptionOrNull()!!
                            if (!clientGone) {
                                Logger.d(
                                    messageString = "SABR relay round failed: " +
                                        (failure.message ?: failure.javaClass.simpleName)
                                )
                            }
                            // The stream generation is over (usually YouTube's attestation wall).
                            // The bytes already delivered stay valid because they all come from
                            // this one session; the player reconnects for the next generation.
                            // Restarting the session here instead would splice misaligned segment
                            // bytes into the response and corrupt the stream for the demuxer.
                            walled = true
                            // The wall is known the moment a round fails without any media: the
                            // connection can stay open through a long retry tail, but the
                            // playback controller needs the health signal immediately (a fresh
                            // quality pick must fall back instantly instead of dead-loading).
                            if (mediaBytes <= 0L) {
                                markVideoWalled(videoKeyOf(key))
                            }
                            break
                        }
                        val result = outcome.getOrThrow()
                        val backoff = result.backoffMs
                        if (backoff > 0) Thread.sleep(backoff.toLong())
                        if (result.segmentCount == 0) {
                            // The stream only truly ends when the parsed segment timeline is
                            // exhausted; a few empty rounds in between are the server's pacing.
                            // They are ridden out with the same session — switching the session
                            // would splice misaligned bytes into the response — and after that
                            // the connection ends so the player can reconnect for a fresh
                            // generation.
                            val reachedEnd = timeline != null &&
                                lastSequence >= timeline!!.endSequence &&
                                lastSequence > 0
                            if (reachedEnd || emptyRounds >= MAX_EMPTY_ROUNDS) break
                            emptyRounds++
                            Thread.sleep(EMPTY_ROUND_DELAY_MS)
                        } else {
                            emptyRounds = 0
                        }
                    }
                }
                if (!walled || clientGone) break@attempt
                // The session died without the player receiving anything (headers unsent and no
                // media delivered): nothing has leaked to the demuxer, so restarting is byte
                // safe. A fresh player response carries a fresh streaming url and host, and a
                // fresh session regularly gets a generation that streams where the cached one
                // was walled.
                if (headersSent || mediaBytes > 0 ||
                    freshAttempts >= MAX_FRESH_EXTRACTION_ATTEMPTS ||
                    freshExtractionFailures >= FRESH_EXTRACTION_FAILURE_LIMIT
                ) {
                    break@attempt
                }
                freshAttempts++
                val videoId = activeInfo.videoId
                if (videoId.isNullOrEmpty()) break@attempt
                Logger.d(
                    messageString = "SABR session walled before any media; " +
                        "fetching a fresh stream info (attempt $freshAttempts/" +
                        "$MAX_FRESH_EXTRACTION_ATTEMPTS)"
                )
                val fresh = runCatching {
                    org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
                        .fetchSabrInfoForReload(videoId, "")
                }.getOrNull()
                if (fresh == null || fresh.formats.none { it.itag == format.itag }) {
                    freshExtractionFailures++
                    break@attempt
                }
                freshExtractionFailures = 0
                // The fresh info also becomes the cached track info, so the player's own
                // reconnects start from it instead of the walled one.
                activeInfo = fresh
                tracks[key] = fresh to format
            }
            // The honest per-video health signal for the playback controller: whether this
            // connection ended without serving a single media byte (a walled route fails for
            // every track of the video, so the wall is recorded per video).
            walledVideos.computeIfAbsent(videoKeyOf(key)) {
                java.util.concurrent.atomic.AtomicLong(0L)
            }.set(if (mediaBytes <= 0L) System.currentTimeMillis() else 0L)
            if (!headersSent && !clientGone) {
                if (mediaBytes > 0) {
                    // A short track may finish inside the pre-buffer; flush whatever was
                    // collected.
                    sendHeaders()
                    preBuffer.writeTo(body)
                    preBuffer.reset()
                } else {
                    // Not a single media byte could be served: answering an empty stream would
                    // leave the player demuxing a headerless file forever (an endless black
                    // screen without an error), so the request fails visibly and the player
                    // retries or reports the error.
                    Logger.d(messageString = "SABR relay served no media for $key; answering 502")
                    exchange.sendResponseHeaders(502, -1)
                    return
                }
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
