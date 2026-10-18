/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Live network probe for the desktop provider stack: trending, search, resolve and metadata are
 * requested from real YouTube exactly the way the app requests them, and every outcome (success
 * counts or failure reason + detail) is printed so the real cause behind the user-visible
 * "Something went wrong while loading content." becomes visible.
 *
 * The probe is a no-op unless the test process environment contains `NEWPIPE_LIVE_PROBE=1`, so CI
 * and normal local runs never touch the network. Run it locally with:
 *
 *     NEWPIPE_LIVE_PROBE=1 gradlew :shared:jvmTest --tests "*ExtractorLiveProbeTest*"
 */
class ExtractorLiveProbeTest {

    @Test
    fun probeTrendingSearchResolveAndMetadata() {
        if (System.getenv("NEWPIPE_LIVE_PROBE") != "1") {
            println("[probe] skipped — set NEWPIPE_LIVE_PROBE=1 to run against live YouTube")
            return
        }

        val provider = ExtractorMediaProvider()
        println("[probe] isAvailable=${provider.isAvailable}")

        runBlocking {
            val trending = provider.trending(pageToken = null)
            report("trending", trending)

            val nextToken = (trending as? ProviderResult.Success)?.value?.nextPageToken
            if (nextToken != null) {
                report("trending page 2", provider.trending(pageToken = nextToken))
            } else {
                println("[probe] trending exposed no next-page token — cannot probe pagination")
            }

            val search = provider.search(query = "newpipe", pageToken = null)
            report("search", search)

            val searchToken = (search as? ProviderResult.Success)?.value?.nextPageToken
            if (searchToken != null) {
                report("search page 2", provider.search(query = "newpipe", pageToken = searchToken))
            }

            val firstUrl = (trending as? ProviderResult.Success)
                ?.value?.items?.firstOrNull()?.url
            if (firstUrl != null) {
                report("resolve", provider.resolve(firstUrl))
                report("metadata", provider.streamMetadata(firstUrl))
            } else {
                println("[probe] no trending item available to resolve")
            }
        }
    }

    private fun report(name: String, result: ProviderResult<*>) {
        when (result) {
            is ProviderResult.Success -> when (val value = result.value) {
                is ProviderContentPage -> println(
                    "[probe] $name OK items=${value.items.size} nextPage=${value.nextPageToken != null}"
                )
                is ResolvedMedia -> println(
                    "[probe] $name OK live=${value.isLive} audioOnly=${value.audioOnly} " +
                        "streamUrl=${value.streamUrl.take(80)}"
                )
                is StreamMetadata -> println(
                    "[probe] $name OK title=${value.title} uploader=${value.uploaderName}"
                )
                else -> println("[probe] $name OK")
            }
            is ProviderResult.Failure -> println(
                "[probe] $name FAILED reason=${result.reason} detail=${result.detail}"
            )
        }
    }

    /**
     * Fetches the pages the extractors need directly through [JVMHttpDownloader] and reports what
     * the JDK HttpClient actually receives (status, length, marker presence, head of the body) so a
     * transport-level block (consent page, bot page, empty/garbled body) is distinguishable from an
     * extractor parsing bug.
     */
    @Test
    fun probeRawPagesThroughJvmHttpDownloader() {
        if (System.getenv("NEWPIPE_LIVE_PROBE") != "1") {
            println("[probe] skipped — set NEWPIPE_LIVE_PROBE=1 to run against live YouTube")
            return
        }

        val downloader = JVMHttpDownloader()
        val urls = listOf(
            "https://www.youtube.com/results?search_query=newpipe",
            "https://www.youtube.com/feed/trending",
            "https://www.youtube.com/"
        )
        for (url in urls) {
            try {
                val response = downloader.execute(
                    Request(
                        "GET",
                        url,
                        emptyMap<String, List<String>>(),
                        null,
                        Localization.DEFAULT,
                        false
                    )
                )
                val body = response.responseBody()
                println(
                    "[probe] GET $url -> HTTP ${response.responseCode()} len=${body.length} " +
                        "ytInitialData=${body.contains("ytInitialData")} " +
                        "consent=${body.contains("consent")} " +
                        "recaptcha=${body.contains("recaptcha", ignoreCase = true)}"
                )
                println("[probe]   head=${body.take(300).replace('\n', ' ')}")
            } catch (e: Exception) {
                println("[probe] GET $url -> EXCEPTION ${e.javaClass.name}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
