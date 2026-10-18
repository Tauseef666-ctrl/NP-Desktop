/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * JVM [Downloader] for NewPipeExtractor backed by the JDK [HttpClient].
 *
 * This is deliberately minimal and stateless apart from the shared, lazily created client: it does
 * not log in, store cookies or retry, matching what a plain desktop client needs. It normalises the
 * transport so the extractor sees exactly what it expects:
 *
 *  * a desktop browser `User-Agent` (the extractor dereferences it and NPEs if it is missing),
 *  * transparent gzip/deflate decompression when the server honours `Accept-Encoding`,
 *  * captcha detection surfaced as [ReCaptchaException] rather than an opaque body, and
 *  * transport failures surfaced as [IOException].
 */
internal class JVMHttpDownloader : Downloader() {

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            // Force HTTP/1.1: the JDK's HTTP/2 implementation trips an internal AssertionError in
            // ResponseSubscribers while reading YouTube's response frames (IOException with no
            // message), which failed EVERY request on the test machine — the root cause behind the
            // desktop's "Something went wrong while loading content." h1.1 fetches the same pages
            // successfully.
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.ROOT)
            ?: "GET"
        val url = request.url()

        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))

        var hasUserAgent = false
        var hasAcceptEncoding = false
        for ((name, values) in request.headers()) {
            if (name.equals(USER_AGENT, ignoreCase = true)) hasUserAgent = true
            if (name.equals(ACCEPT_ENCODING, ignoreCase = true)) hasAcceptEncoding = true
            for (value in values) {
                try {
                    builder.header(name, value)
                } catch (_: IllegalArgumentException) {
                    // java.net.http forbids a handful of headers (Host, Content-Length, Connection,
                    // Transfer-Encoding, ...). The client sets those itself, so skipping is correct.
                }
            }
        }
        if (!hasUserAgent) builder.header(USER_AGENT, DESKTOP_USER_AGENT)
        // Only advertise what we can decode, and only if the caller did not pick an encoding.
        if (!hasAcceptEncoding) builder.header(ACCEPT_ENCODING, "gzip, deflate")

        val data = request.dataToSend()
        val publisher = if (data != null && data.isNotEmpty()) {
            HttpRequest.BodyPublishers.ofByteArray(data)
        } else {
            HttpRequest.BodyPublishers.noBody()
        }
        builder.method(method, publisher)

        val httpResponse = try {
            client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while requesting $url", e)
        }

        val raw = httpResponse.body() ?: ByteArray(0)
        val contentEncoding = httpResponse.headers().firstValue(CONTENT_ENCODING).orElse("")
        val body = String(decode(raw, contentEncoding), Charsets.UTF_8)
        val status = httpResponse.statusCode()

        if (status == 429 || looksLikeCaptcha(body)) {
            throw ReCaptchaException("reCAPTCHA challenge for $url (HTTP $status)", url)
        }

        return Response(
            status,
            reasonPhrase(status),
            httpResponse.headers().map(),
            body,
            httpResponse.uri()?.toString() ?: url
        )
    }

    private fun decode(body: ByteArray, contentEncoding: String): ByteArray {
        if (body.isEmpty()) return body
        val encoding = contentEncoding.substringBefore(',').trim().lowercase(Locale.ROOT)
        return when (encoding) {
            "gzip", "x-gzip" -> inflate(GZIPInputStream(ByteArrayInputStream(body)))
            "deflate" -> inflate(InflaterInputStream(ByteArrayInputStream(body)))
            else -> body
        }
    }

    private fun inflate(input: InputStream): ByteArray = input.use { it.readBytes() }

    private fun looksLikeCaptcha(body: String): Boolean =
        // Only genuine block pages trip this. A plain "recaptcha" match is far too loose: the
        // normal YouTube results page itself references recaptcha scripts, so the old check threw
        // ReCaptchaException on healthy responses.
        body.contains("sorry/index", ignoreCase = true) ||
            (body.contains("g-recaptcha", ignoreCase = true) &&
                body.contains("unusual traffic", ignoreCase = true))

    private fun reasonPhrase(status: Int): String = when (status) {
        200 -> "OK"
        204 -> "No Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        303 -> "See Other"
        304 -> "Not Modified"
        307 -> "Temporary Redirect"
        308 -> "Permanent Redirect"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        410 -> "Gone"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> ""
    }

    private companion object {
        const val USER_AGENT = "User-Agent"
        const val ACCEPT_ENCODING = "Accept-Encoding"
        const val CONTENT_ENCODING = "Content-Encoding"
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
