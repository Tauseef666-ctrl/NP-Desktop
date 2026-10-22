/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.CompressionInterceptor
import okhttp3.Gzip
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.brotli.Brotli
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * An Android free [Downloader] implementation for the extraction engine.
 *
 * This is the desktop counterpart of the legacy `org.schabi.newpipe.DownloaderImpl`. The legacy
 * implementation is bound to `android.content.Context` and `androidx.preference` because it stores
 * the re-captcha cookie in the shared preferences; this one keeps the cookies in memory instead so
 * that it can run on a plain JVM.
 */
class JvmDownloader(
    builder: OkHttpClient.Builder = OkHttpClient.Builder()
) : Downloader() {

    private val client: OkHttpClient = builder
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(CompressionInterceptor(Brotli, Gzip))
        .build()

    private val cookies = ConcurrentHashMap<String, String>()

    /** The cookie stored under [key], or `null` when there is none. */
    fun cookie(key: String): String? = cookies[key]

    /** Stores a cookie under [key]. */
    fun setCookie(key: String, value: String) {
        cookies[key] = value
    }

    /** Removes the cookie stored under [key]. */
    fun removeCookie(key: String) {
        cookies.remove(key)
    }

    /**
     * The cookie header value for [url]. Cookies are service specific, so only the cookies that
     * belong to the host of [url] are sent.
     */
    fun cookiesFor(url: String): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return ""
        return cookies.entries
            .filter { (key, _) -> key.startsWith("$host|") }
            .map { (key, value) -> "${key.substringAfter('|')}=$value" }
            .distinct()
            .joinToString("; ")
    }

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val requestBuilder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", USER_AGENT)

        val cookies = cookiesFor(request.url())
        if (cookies.isNotEmpty()) {
            requestBuilder.header("Cookie", cookies)
        }

        request.headers().forEach { (name, values) ->
            requestBuilder.removeHeader(name)
            values.forEach { requestBuilder.addHeader(name, it) }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == HTTP_TOO_MANY_REQUESTS) {
                throw ReCaptchaException("reCaptcha challenge requested", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString()
            )
        }
    }

    /**
     * Issues a `HEAD` request to determine the size of the content behind [url].
     */
    fun contentLength(url: String): Long {
        val response = head(url)
        val header = response.getHeader("Content-Length")
            ?: throw IOException("Content length is not known for $url")
        return header.toLongOrNull() ?: throw IOException("Invalid content length for $url")
    }

    companion object {
        const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"

        private const val READ_TIMEOUT_SECONDS = 30L
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
