/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.newpipe.app.model.ChannelDetails
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.MediaStream
import net.newpipe.app.model.PlaylistDetails
import net.newpipe.app.model.ResolvedContent
import net.newpipe.app.model.ServiceInfo
import net.newpipe.app.model.StreamDetails
import net.newpipe.app.model.SubtitleTrack
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrRequest
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession
import org.schabi.newpipe.extractor.services.youtube.sabr.media.SabrMediaSegment
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor

/**
 * A [ContentRepository] backed by the real NewPipe extraction engine.
 *
 * Every call is executed on [Dispatchers.IO] because the extraction engine performs blocking
 * network requests.
 */
class ExtractorContentRepository(
    private val registry: ServiceRegistry = ExtractorServiceRegistry()
) : ContentRepository {

    init {
        ExtractorInitializer.ensureInitialized()
    }

    /**
     * Remembers the last fetched page of a search so that scrolling to the next page does not
     * require re-fetching all previous pages.
     */
    private val searchCursors = ConcurrentHashMap<String, SearchCursor>()

    /**
     * Short-lived caches that make repeated navigation feel instant: revisiting a stream,
     * channel, playlist or the home feed inside the cache window costs no network round trip at
     * all, and two concurrent requests for the same item share a single fetch.
     *
     * Stream metadata is cached for the shortest time, because the extracted playback URLs of
     * some services expire; lists live a little longer. Values are consumed read-only by the
     * user interface, so sharing them across callers is safe.
     */
    private val streamCache = ResultsCache<CacheKey, StreamDetails>(ttlMillis = 10 * 60_000L)
    private val channelCache = ResultsCache<CacheKey, ChannelDetails>(ttlMillis = 10 * 60_000L)
    private val playlistCache = ResultsCache<CacheKey, PlaylistDetails>(ttlMillis = 10 * 60_000L)
    private val kioskCache = ResultsCache<CacheKey, List<ContentItem>>(ttlMillis = 10 * 60_000L)
    private val searchCache = ResultsCache<CacheKey, List<ContentItem>>(ttlMillis = 5 * 60_000L)

    override suspend fun search(request: SearchRequest): List<ContentItem> = withContext(Dispatchers.IO) {
        guard {
            if (request.page != 0) {
                return@guard rawSearch(request)
            }
            searchCache.getOrFetch(
                CacheKey(request.serviceId, request.query + "|" + (request.filter ?: "")),
                storeIf = { it.isNotEmpty() }
            ) { rawSearchWithRetry(request) }
        }
    }

    /**
     * The first search of a fresh session sometimes comes back empty while the service still
     * negotiates its consent cookie; one quiet retry covers that without caching the emptiness.
     */
    private suspend fun rawSearchWithRetry(request: SearchRequest): List<ContentItem> {
        val first = rawSearch(request)
        if (first.isNotEmpty() || request.page != 0) return first
        delay(400)
        return rawSearch(request)
    }

    private suspend fun rawSearch(request: SearchRequest): List<ContentItem> = withContext(Dispatchers.IO) {
        guard {
            val service = serviceForId(request.serviceId)
            val filters = contentFilterItems(service, request.filter)
            val handler = service.searchQHFactory.fromQuery(request.query, filters, null)
            val items = loadSearchPage(service, handler, request.query, request.filter, request.page)
            items.mapNotNull { it.toContentItem() }
        }
    }

    /**
     * Converts the app's filter string (e.g. "videos") into the extractor's filter item list
     * expected by the search query handlers of the extraction engine. A `null` filter maps to
     * the engine's default "all" filter, because the search query handlers of the extraction
     * engine reject an empty filter list.
     */
    private fun contentFilterItems(
        service: org.schabi.newpipe.extractor.StreamingService,
        name: String?
    ): List<FilterItem> {
        val effectiveFilter = name ?: org.schabi.newpipe.extractor.services.youtube
            .linkHandler.YoutubeSearchQueryHandlerFactory.ALL
        val filter = service.searchQHFactory.availableContentFilter ?: return emptyList()
        val items = filter.filterGroups.asSequence()
            .flatMap { it.filterItems.asSequence() }
            .filterNotNull()
            .toList()
        return items.filter { it.getName() == effectiveFilter }
    }

    override fun kiosks(serviceId: Int): List<ServiceKiosk> = runCatching {
        val kioskList = serviceForId(serviceId).kioskList ?: return emptyList()
        val defaultKiosk = kioskList.defaultKioskId
        // "Trending" is the list users expect to see first on the home screen. Services that do
        // not offer it fall back to their own default kiosk.
        kioskList.availableKiosks
            .sortedBy {
                when {
                    it == TRENDING_KIOSK_ID -> 0
                    it == defaultKiosk -> 1
                    else -> 2
                }
            }
            .map { ServiceKiosk(id = it, name = kioskDisplayName(it)) }
    }.getOrDefault(emptyList())

    override suspend fun kiosk(serviceId: Int, kioskId: String): List<ContentItem> =
        kioskPage(serviceId, kioskId, 0)

    /**
     * Remembers the page tokens of every kiosk, so that scrolling down costs one request instead
     * of re-fetching all previous pages.
     */
    private val kioskCursors = ConcurrentHashMap<String, MutableList<Page?>>()

    override suspend fun kioskPage(
        serviceId: Int,
        kioskId: String,
        pageIndex: Int
    ): List<ContentItem> = withContext(Dispatchers.IO) {
        guard {
            if (pageIndex != 0) {
                return@guard rawKioskPage(serviceId, kioskId, pageIndex)
            }
            kioskCache.getOrFetch(CacheKey(serviceId, kioskId)) {
                rawKioskPage(serviceId, kioskId, 0)
            }
        }
    }

    private suspend fun rawKioskPage(
        serviceId: Int,
        kioskId: String,
        pageIndex: Int
    ): List<ContentItem> = withContext(Dispatchers.IO) {
        guard {
            val service = serviceForId(serviceId)
            if (kioskId == TRENDING_KIOSK_ID) {
                // YouTube retired the trending feed: /feed/trending serves an empty
                // "Try searching to get started" page that the extractor cannot parse. The best
                // anonymous approximation is the video search for trending topics. The search
                // handler factory of the extraction engine only supports content filters.
                val handler = service.searchQHFactory.fromQuery(
                    TRENDING_FALLBACK_QUERY,
                    contentFilterItems(service, TRENDING_FALLBACK_FILTER),
                    null
                )
                return@guard loadSearchPage(
                    service,
                    handler,
                    TRENDING_FALLBACK_QUERY,
                    null,
                    pageIndex,
                    "kiosk:$kioskId"
                ).mapNotNull { it.toContentItem() }
            }
            val kioskList = service.kioskList
                ?: throw ContentException.Unsupported("The service does not provide kiosks")
            // Use the extractor directly instead of KioskInfo: KioskInfo also parses the
            // localized name of the kiosk, which is not needed here and can fail when the
            // service changes the shape of its response header.
            val extractor = kioskList.getExtractorById(kioskId, null)
            extractor.fetchPage()

            val key = "$serviceId:$kioskId"
            // cursors[i] holds the page token that requests page i + 1; a null entry marks an
            // exhausted kiosk. The initial page is always fetched directly from the extractor.
            val cursors = kioskCursors.getOrPut(key) { mutableListOf() }
            synchronized(cursors) {
                if (pageIndex == 0) {
                    if (cursors.isEmpty()) {
                        cursors.add(extractor.initialPage.nextPage)
                    }
                    return@guard extractor.initialPage.items.mapNotNull { it.toContentItem() }
                }
                while (cursors.size < pageIndex) {
                    if (cursors.isEmpty()) {
                        cursors.add(extractor.initialPage.nextPage)
                        continue
                    }
                    val previous = cursors.last()
                    if (previous == null) return@guard emptyList()
                    cursors.add(extractor.getPage(previous).nextPage)
                }
                val token = cursors.getOrNull(pageIndex - 1) ?: return@guard emptyList()
                val page = extractor.getPage(token) ?: return@guard emptyList()
                page.items.mapNotNull { it.toContentItem() }
            }
        }
    }

    override suspend fun streamDetails(serviceId: Int, url: String): StreamDetails =
        withContext(Dispatchers.IO) {
            streamCache.getOrFetch(CacheKey(serviceId, url)) {
                guard { extractStreamDetails(serviceId, url) }
            }
        }

    private fun extractStreamDetails(serviceId: Int, url: String): StreamDetails {
        val service = serviceForId(serviceId)
        return try {
            detailsOf(serviceId, url, withBotWallRetry(service, url))
        } catch (exception: Exception) {
            // "Made for kids" videos answer the extraction engine and only open through its
            // recovery ladder, which YouTube refuses on some attempts; the playback fallback
            // retries the plain extraction and then probes the SABR streaming route.
            if (isKidsUnplayable(exception, url)) {
                retryKidsExtraction(serviceId, url, exception)
            } else {
                throw exception
            }
        }
    }

    /**
     * Re-offers the plain extraction [KIDS_ENGINE_RETRY_ATTEMPTS] times, because the engine's
     * refusal for made-for-kids videos is flaky and the recovery ladder usually opens the
     * media on a later attempt. When every retry is refused with UNPLAYABLE, the SABR streaming
     * route is probed once and the outcome is answered honestly.
     */
    private fun retryKidsExtraction(
        serviceId: Int,
        url: String,
        original: Exception
    ): StreamDetails {
        var lastFailure = original
        for (attempt in 1..KIDS_ENGINE_RETRY_ATTEMPTS) {
            if (attempt > 1) {
                Thread.sleep(KIDS_RETRY_DELAY_MS)
            }
            try {
                val details = detailsOf(serviceId, url, withBotWallRetry(serviceForId(serviceId), url))
                Logger.i(messageString = "The plain extraction retry $attempt opened the video")
                return details
            } catch (attemptFailure: Exception) {
                Logger.w(
                    messageString = "The plain extraction retry $attempt did not open the video",
                    throwable = attemptFailure
                )
                if (!isKidsUnplayable(attemptFailure, url)) {
                    throw attemptFailure
                }
                lastFailure = attemptFailure
            }
        }
        kidsPlaybackFallback(serviceId, url, lastFailure)
    }

    /** Assembles the app stream details from the extraction engine's [StreamInfo]. */
    private fun detailsOf(serviceId: Int, url: String, info: StreamInfo): StreamDetails =
        StreamDetails(
            serviceId = serviceId,
            url = info.url ?: url,
            title = info.name.orEmpty(),
            uploader = info.uploaderName.orEmpty(),
            uploaderUrl = info.uploaderUrl,
            uploaderAvatarUrl = info.uploaderAvatars.bestImageUrl(),
            thumbnailUrl = info.thumbnails.bestImageUrl(),
            description = info.description?.content,
            durationSeconds = info.duration.takeIf { it > 0 },
            viewCount = info.viewCount.takeIf { it >= 0 },
            uploadDate = info.textualUploadDate,
            liveNow = info.streamType == StreamType.LIVE_STREAM,
            videoStreams = (info.videoStreams + info.videoOnlyStreams)
                .mapNotNull { it.toMediaStream() },
            audioStreams = info.audioStreams.mapNotNull { it.toMediaStream() },
            subtitles = info.subtitles.mapNotNull {
                val subtitleUrl = @Suppress("DEPRECATION") it.url ?: return@mapNotNull null
                SubtitleTrack(
                    url = subtitleUrl,
                    name = it.displayLanguageName.orEmpty(),
                    languageTag = it.languageTag
                )
            },
            relatedItems =
                @Suppress("DEPRECATION") info.relatedStreams.mapNotNull { it.toContentItem() }
        )

    /**
     * True when the extraction failed with the `UNPLAYABLE` playability status that "made for
     * kids" videos (and other refused videos) give back through the engine's default visionOS
     * client. Only YouTube watch urls are eligible, other services keep their exact errors.
     */
    private fun isKidsUnplayable(exception: Exception, url: String): Boolean {
        val isYouTubeWatch = url.contains("youtube.com/watch?v=") || url.contains("youtu.be/")
        if (!isYouTubeWatch) {
            return false
        }
        var current: Throwable? = exception
        var hops = 0
        while (current != null && hops < 5) {
            val message = current.message.orEmpty()
            // UNPLAYABLE arrives with varying sub-message wordings ("This video is not
            // available", "This video is unavailable", ...). Any UNPLAYABLE on a YouTube watch
            // url is worth replaying through the SABR streaming route; when that replay does
            // not reach the media, a clear error is raised below.
            if (message.contains("UNPLAYABLE")) {
                return true
            }
            current = current.cause
            hops++
        }
        return false
    }

    /**
     * Handles a YouTube video every extraction attempt refused (usually "made for kids"
     * content). A pot-less SABR session is replayed through the MWEB player client and driven
     * to one playback request so the outcome is known precisely: while YouTube still serves
     * its media the engine's error is kept, and when it refuses the media, an honest
     * user-facing message is raised instead.
     */
    private fun kidsPlaybackFallback(
        serviceId: Int,
        url: String,
        original: Exception
    ): Nothing {
        val videoId = videoIdOfUrl(url) ?: throw original
        Logger.i(messageString = "Every extraction attempt was refused; probing the SABR streaming route")
        val (response, visitorData) = mwebPlayerResponse(videoId, original) ?: throw original
        val info = try {
            YoutubeStreamExtractor.buildSabrInfoFromPlayerResponse(
                videoId, playbackCpn(), response, visitorData, MWEB_CLIENT_VERSION
            )
        } catch (failure: Exception) {
            // The SABR route yields nothing either; keep the engine's precise error.
            throw original
        }
        if (info.formats.none { it.isAudio() } || info.formats.none { it.isVideo() }) {
            throw original
        }
        if (requestSabrMediaSegments(info) <= 0) {
            // YouTube answers every media request with an empty UMP body: this video needs a
            // BotGuard attestation ("PO token") that a desktop player cannot mint without a
            // browser engine. Say so instead of surfacing a cryptic protocol error.
            throw ContentException.Extraction(KIDS_ATT)
        }
        // Media actually flowed, but UMP segments cannot be handed to the desktop player.
        throw ContentException.Unsupported(KIDS_SABR_FORMAT)
    }

    /**
     * Fetches the InnerTube player response of [videoId] through the mobile web (MWEB) player
     * client, the one client YouTube still lets extract made-for-kids metadata and SABR
     * streaming data from without an attestation. The visitor data has to be taken from a real
     * YouTube page response first, and rapid repeat requests routinely flake to UNPLAYABLE, so
     * the request is retried before giving up. Returns the response together with the visitor
     * data it was requested with, or null when every attempt was refused.
     */
    private fun mwebPlayerResponse(
        videoId: String,
        original: Exception
    ): Pair<JsonObject, String>? {
        val localization = NewPipe.getPreferredLocalization()
        val contentCountry = NewPipe.getPreferredContentCountry()
        for (attempt in 1..MWEB_PLAYER_ATTEMPTS) {
            if (attempt > 1) {
                Thread.sleep(MWEB_RETRY_DELAY_MS)
            }
            val visitorData = try {
                homepageVisitorData(localization)
            } catch (failure: Exception) {
                Logger.w(messageString = "Fetching the YouTube visitor data failed", throwable = failure)
                null
            } ?: continue
            val body = JsonWriter.string(
                JsonObject.builder()
                    .value("videoId", videoId)
                    .value("contentCheckOk", true)
                    .value("racyCheckOk", true)
                    .value(
                        "context",
                        JsonObject.builder()
                            .value(
                                "client",
                                JsonObject.builder()
                                    .value("clientName", "MWEB")
                                    .value("clientVersion", MWEB_CLIENT_VERSION)
                                    .value("hl", localization.languageCode)
                                    .value("gl", contentCountry.countryCode)
                                    .value("visitorData", visitorData)
                                    .done()
                            )
                            .value(
                                "user",
                                JsonObject.builder().value("lockedSafetyMode", false).done()
                            )
                            .value(
                                "request",
                                JsonObject.builder().value("useSsl", true).done()
                            )
                            .done()
                    )
                    .done()
            ).toByteArray(Charsets.UTF_8)
            val json = try {
                JsonParser.`object`().from(
                    YoutubeParsingHelper.getValidJsonResponseBody(
                        NewPipe.getDownloader().post(
                            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                            mapOf(
                                "User-Agent" to listOf(MWEB_PLAYER_USER_AGENT),
                                "X-Youtube-Client-Name" to listOf("2"),
                                "X-Youtube-Client-Version" to listOf(MWEB_CLIENT_VERSION),
                                "X-Goog-Visitor-Id" to listOf(visitorData),
                                "X-Goog-Api-Format-Version" to listOf("2"),
                                "Content-Type" to listOf("application/json")
                            ),
                            body,
                            localization
                        )
                    )
                )
            } catch (failure: Exception) {
                Logger.w(messageString = "The MWEB player request failed", throwable = failure)
                if (failure is ReCaptchaException) throw failure
                continue
            }
            val status = json.getObject("playabilityStatus")?.getString("status")
            if (status == "OK") {
                return json to visitorData
            }
            Logger.i(messageString = "The MWEB player replay $attempt answered $status")
        }
        return null
    }

    /**
     * Fetches the YouTube homepage with a desktop browser user agent and extracts the web
     * client's visitor data, which the MWEB player client requires in its context.
     */
    private fun homepageVisitorData(localization: Localization): String {
        val response = NewPipe.getDownloader().get(
            "https://www.youtube.com/",
            mapOf("User-Agent" to listOf(CHROME_HOME_USER_AGENT)),
            localization
        )
        val html = response.responseBody()
        return Regex("\"visitorData\":\"([^\"]+)\"").find(html)
            ?.groupValues?.get(1)
            ?: throw IOException("The YouTube homepage exposed no visitor data")
    }

    /**
     * Drives a SABR session far enough to learn whether YouTube still serves the media of the
     * video without an attestation: one preparation request to declare the format availability,
     * then one playback request carrying exactly one video and one audio track, counting the
     * returned media segments (init segments excluded).
     */
    private fun requestSabrMediaSegments(info: YoutubeSabrInfo): Int {
        val audio = info.formats.firstOrNull { it.isAudio() } ?: return 0
        val video = info.formats.firstOrNull { it.isVideo() } ?: return 0
        val session = YoutubeSabrSession(info)
        var mediaSegments = 0
        try {
            session.requestOnce(
                YoutubeSabrRequest.preparation(0L, listOf(audio, video))
            ) { segment: SabrMediaSegment ->
                if (!segment.getHeader().isInitSegment()) {
                    mediaSegments++
                }
            }
            session.requestOnce(
                YoutubeSabrRequest.playback(
                    0L,
                    1.0f,
                    listOf(
                        YoutubeSabrRequest.Track.of(video, null, 0),
                        YoutubeSabrRequest.Track.of(audio, null, 0)
                    )
                )
            ) { segment: SabrMediaSegment ->
                if (!segment.getHeader().isInitSegment()) {
                    mediaSegments++
                }
            }
        } catch (failure: Exception) {
            Logger.w(messageString = "The SABR session probe failed", throwable = failure)
        }
        return mediaSegments
    }

    /** The sixteen character playback nonce every SABR request carries. */
    private fun playbackCpn(): String =
        UUID.randomUUID().toString().replace("-", "").take(16)

    /** The eleven character video id inside a YouTube watch / short / youtu.be URL. */
    private fun videoIdOfUrl(url: String): String? {
        for (pattern in listOf("v=", "/shorts/", "/embed/", "youtu.be/")) {
            val index = url.indexOf(pattern)
            if (index >= 0) {
                val start = index + pattern.length
                val end = url.indexOfAny(charArrayOf('&', '?', '/'), start)
                    .let { if (it < 0) url.length else it }
                val candidate = url.substring(start, end)
                if (candidate.length == 11) {
                    return candidate
                }
            }
        }
        return null
    }

    /**
     * Fetches the stream details and retries when YouTube answers the "Sign in to confirm that
     * you're not a bot" wall. The ladder is: pause, retry anonymously once, and when a YouTube
     * web session is stored, retry with its cookies before giving up with a friendly error.
     */
    private fun withBotWallRetry(
        service: StreamingService,
        url: String
    ): StreamInfo = try {
        StreamInfo.getInfo(service, url)
    } catch (exception: Exception) {
        if (!isBotWall(exception)) throw exception
        Logger.w(
            messageString = "YouTube blocked the anonymous access temporarily; retrying once",
            throwable = exception
        )
        Thread.sleep(BOT_WALL_RETRY_DELAY_MS)
        try {
            StreamInfo.getInfo(service, url)
        } catch (anonymousRetry: Exception) {
            if (!isBotWall(anonymousRetry)) throw anonymousRetry
            if (YtSession.exists()) {
                Logger.w(
                    messageString =
                        "YouTube still blocks the anonymous access; retrying with the web session",
                    throwable = anonymousRetry
                )
                Thread.sleep(BOT_WALL_RETRY_DELAY_MS)
                try {
                    StreamInfo.getInfo(service, url)
                } catch (signedRetry: Exception) {
                    if (!isBotWall(signedRetry)) throw signedRetry
                    throw ContentException.Extraction(
                        "YouTube is limiting access from this network even with the stored " +
                            "session. Please refresh the session in Settings -> Account.",
                        signedRetry
                    )
                }
            } else {
                throw ContentException.Extraction(
                    "YouTube is limiting anonymous access from this network for the moment. " +
                        "Please try again in a minute or sign in to YouTube in " +
                        "Settings -> Account.",
                    anonymousRetry
                )
            }
        }
    }

    /**
     * Detects the YouTube "Sign in to confirm you're not a bot" wall. The exact exception
     * type differs between the extraction engine implementations, so the message text and
     * supplier's own exception classes are matched.
     */
    private fun isBotWall(exception: Exception): Boolean {
        if (exception.javaClass.name.endsWith("SignInConfirmNotBotException")) return true
        return sequenceOf(exception, exception.cause).filterNotNull().any { cause ->
            cause.message.orEmpty().contains("Sign in to confirm", ignoreCase = true)
        }
    }

    override suspend fun channel(serviceId: Int, url: String): ChannelDetails =
        withContext(Dispatchers.IO) {
            channelCache.getOrFetch(CacheKey(serviceId, url)) {
                guard { extractChannel(serviceId, url) }
            }
        }

    private fun extractChannel(serviceId: Int, url: String): ChannelDetails {
        val service = serviceForId(serviceId)
        val info = ChannelInfo.getInfo(service, url)
        val tabItems = info.tabs.asSequence()
            .mapNotNull { tab ->
                try {
                    tab to ChannelTabInfo.getInfo(service, tab).relatedItems
                } catch (exception: Exception) {
                    Logger.w(
                        messageString = "A channel tab could not be loaded",
                        throwable = exception
                    )
                    null
                }
            }
            .toList()
        val items = tabItems
            .firstOrNull { (_, relatedItems) -> relatedItems.isNotEmpty() }
            ?.second
            ?.mapNotNull { it.toContentItem() }
            ?: emptyList()
        val playlists = tabItems
            .firstOrNull { (tab, _) -> tab.id.orEmpty().contains("playlist", ignoreCase = true) }
            ?.second
            ?.mapNotNull { it.toContentItem() }
            ?.filter { it.type == ContentType.PLAYLIST }
            ?: emptyList()
        return ChannelDetails(
            serviceId = serviceId,
            url = info.url ?: url,
            name = info.name.orEmpty(),
            avatarUrl = info.avatars.bestImageUrl(),
            bannerUrl = info.banners.bestImageUrl(),
            description = info.description,
            subscriberCount = info.subscriberCount.takeIf { it >= 0 },
            items = items,
            playlists = playlists
        )
    }

    override suspend fun playlist(serviceId: Int, url: String): PlaylistDetails =
        withContext(Dispatchers.IO) {
            playlistCache.getOrFetch(CacheKey(serviceId, url)) {
                guard { extractPlaylist(serviceId, url) }
            }
        }

    override suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isBlank()) return@withContext emptyList()
        guard {
            val service = serviceForId(DEFAULT_SUGGEST_SERVICE_ID)
            val suggestions = runCatching {
                service.suggestionExtractor?.suggestionList(term)?.mapNotNull { it?.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
            }.getOrElse { exception ->
                Logger.w(messageString = "Search suggestions failed", throwable = exception)
                emptyList()
            }
            buildList {
                add(term)
                suggestions.forEach { suggestion ->
                    if (!equalsIgnoreCase(suggestion, term) && none { equalsIgnoreCase(it, suggestion) }) {
                        add(suggestion)
                    }
                }
            }.take(MAX_SUGGESTIONS)
        }
    }

    private fun equalsIgnoreCase(left: String, right: String): Boolean =
        left.equals(right, ignoreCase = true)

    private fun extractPlaylist(serviceId: Int, url: String): PlaylistDetails {
        val service = serviceForId(serviceId)
        val info = PlaylistInfo.getInfo(service, url)
        return PlaylistDetails(
            serviceId = serviceId,
            url = info.url ?: url,
            name = info.name.orEmpty(),
            thumbnailUrl = info.thumbnailUrl,
            description = null,
            uploader = info.uploaderName,
            uploaderUrl = info.uploaderUrl,
            itemCount = info.streamCount.takeIf { it >= 0 },
            items = info.relatedItems.mapNotNull { it.toContentItem() }
        )
    }

    override suspend fun resolveUrl(url: String): ResolvedContent = withContext(Dispatchers.IO) {
        guard {
            val service = NewPipe.getServiceByUrl(url)
            when (service.getLinkTypeByUrl(url)) {
                StreamingService.LinkType.STREAM ->
                    ResolvedContent.Stream(streamDetails(service.serviceId, url))

                StreamingService.LinkType.CHANNEL ->
                    ResolvedContent.Channel(channel(service.serviceId, url))

                StreamingService.LinkType.PLAYLIST ->
                    ResolvedContent.Playlist(playlist(service.serviceId, url))

                else ->
                    throw ContentException.Unsupported("The URL does not point to any content")
            }
        }
    }

    override fun availableSearchFilters(serviceId: Int): List<String> = runCatching {
        val filter = serviceForId(serviceId).searchQHFactory.availableContentFilter
            ?: return emptyList()
        filter.filterGroups.asSequence()
            .flatMap { it.filterItems.asSequence() }
            .filterNotNull()
            .map { it.getName() }
            .filter { !it.isNullOrEmpty() }
            .distinct()
            .toList()
    }.getOrDefault(emptyList())

    /**
     * Loads one page of search results.
     *
     * The extraction engine addresses pages through an opaque [Page] token, therefore a page can
     * only be requested after the previous one has been fetched. The token of the previous page is
     * cached so that scrolling forward costs a single request.
     */
    private fun loadSearchPage(
        service: StreamingService,
        handler: org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler,
        query: String,
        filter: String?,
        pageIndex: Int,
        cursorKey: String = "${service.serviceId}|$filter|$query"
    ): List<InfoItem> {
        val key = cursorKey

        if (pageIndex == 0) {
            val info = SearchInfo.getInfo(service, handler)
            val next = info.nextPage
            if (next == null) {
                searchCursors.remove(key)
            } else {
                searchCursors[key] = SearchCursor(0, next)
            }
            return info.relatedItems
        }

        val cached = searchCursors[key]
        var page: Page? = if (cached != null && cached.index == pageIndex - 1) {
            cached.page
        } else {
            walkToPage(service, handler, pageIndex - 1, key)
        }
            ?: return emptyList()

        val result = SearchInfo.getMoreItems(service, handler, page)
        val next = result.nextPage
        if (next == null) {
            searchCursors.remove(key)
        } else {
            searchCursors[key] = SearchCursor(pageIndex, next)
        }
        return result.items
    }

    /**
     * Fetches pages from the start until the page before [targetPage] is reached and returns the
     * token of that page.
     */
    private fun walkToPage(
        service: StreamingService,
        handler: org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler,
        targetPage: Int,
        key: String
    ): Page? {
        var page: Page? = null
        for (index in 0..targetPage) {
            val next = if (index == 0) {
                val info = SearchInfo.getInfo(service, handler)
                page = info.nextPage
                info.nextPage
            } else {
                val current = page ?: return null
                val result = SearchInfo.getMoreItems(service, handler, current)
                page = result.nextPage
                result.nextPage
            }
            if (next == null) return null
            searchCursors[key] = SearchCursor(index, next)
        }
        return page
    }

    /**
     * Runs [block] and translates the extraction engine exceptions into [ContentException]s.
     */
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: ContentException) {
        throw e
    } catch (e: ReCaptchaException) {
        throw ContentException.CaptchaRequired(e)
    } catch (e: IOException) {
        throw ContentException.Network(e)
    } catch (e: ExtractionException) {
        throw ContentException.Extraction(e.message ?: "The content could not be extracted", e)
    } catch (e: UnsupportedOperationException) {
        throw ContentException.Unsupported(e.message ?: "The requested content is not supported", e)
    }

    /**
     * The services known to the extraction engine.
     */
    fun services(): List<ServiceInfo> = registry.services()
}

private data class SearchCursor(val index: Int, val page: Page)

/** The cache key of one repository result: the service and a content-specific identifier. */
private data class CacheKey(val serviceId: Int, val id: String)

/**
 * A small in-memory cache with a time to live and in-flight request deduplication.
 *
 * Repeated navigation is served from the cache, and two concurrent requests for the same item
 * share a single fetch instead of racing each other. The least recently added entries are
 * evicted once the cache grows past [maxSize].
 */
internal class ResultsCache<K : Any, V : Any>(
    private val ttlMillis: Long,
    private val maxSize: Int = 64
) {
    private class Entry<V>(val value: V, val storedAt: Long)

    private val entries = LinkedHashMap<K, Entry<V>>()
    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<V>>()

    /**
     * The cached value for [key], or `null` when it is missing or expired.
     */
    fun getOrNull(key: K): V? = synchronized(entries) {
        val entry = entries[key]
        if (entry == null || System.currentTimeMillis() - entry.storedAt > ttlMillis) {
            entries.remove(key)
            null
        } else {
            entry.value
        }
    }

    /**
     * Serves [key] from the cache, or fetches it once through [fetch] and stores the result.
     * Results that [storeIf] rejects are returned but not remembered, so a transient empty
     * response cannot shadow the real content for the cache lifetime.
     */
    suspend fun getOrFetch(
        key: K,
        storeIf: (V) -> Boolean = { true },
        fetch: suspend () -> V
    ): V {
        getOrNull(key)?.let { return it }

        val pending = inFlight[key] ?: CompletableDeferred<V>()
        if (inFlight.putIfAbsent(key, pending) != null) {
            return pending.await()
        }

        try {
            val value = fetch()
            if (storeIf(value)) {
                synchronized(entries) {
                    entries.remove(key)
                    entries[key] = Entry(value, System.currentTimeMillis())
                    while (entries.size > maxSize) {
                        entries.remove(entries.keys.first())
                    }
                }
            }
            pending.complete(value)
            return value
        } catch (throwable: Throwable) {
            pending.completeExceptionally(throwable)
            throw throwable
        } finally {
            inFlight.remove(key, pending)
        }
    }
}

/** The id of the "Trending" kiosk of the extraction engine. */
private const val TRENDING_KIOSK_ID = "Trending"

/**
 * The search that stands in for the retired YouTube trending feed: the video results for the
 * trending topic, which is the closest anonymous approximation YouTube still serves.
 */
private const val TRENDING_FALLBACK_QUERY = "trending"

/** The extractor content filter of the trending fallback: videos only. */
private const val TRENDING_FALLBACK_FILTER = "videos"

/** The default service used for suggestions when no service-specific API is wired. */
private const val DEFAULT_SUGGEST_SERVICE_ID = 0

/** Maximum number of suggestions returned, including the typed query. */
private const val MAX_SUGGESTIONS = 8

/**
 * The pause before the details of a stream are fetched again when YouTube answers the
 * "Sign in to confirm that you're not a bot" wall, which usually passes after a moment.
 */
private const val BOT_WALL_RETRY_DELAY_MS = 2_500L

/*
 * The MWEB player client identity the kids-content SABR fallback presents: YouTube validates
 * the request against the mobile web browser fingerprint, and the visitor data has to come
 * from a real YouTube page response. When these stop working, refresh all values together.
 */
private const val MWEB_PLAYER_ATTEMPTS = 3
private const val MWEB_RETRY_DELAY_MS = 1_500L
private const val MWEB_CLIENT_VERSION = "2.20260101.00.00"

/**
 * The engine refusals of made-for-kids videos are flaky: the recovery ladder usually opens
 * the media on a later attempt, so the playback fallback offers the plain extraction these
 * many further tries (each separated by KIDS_RETRY_DELAY_MS) before the SABR probe runs.
 */
private const val KIDS_ENGINE_RETRY_ATTEMPTS = 2
private const val KIDS_RETRY_DELAY_MS = 1_200L
private const val MWEB_PLAYER_USER_AGENT =
    "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
private const val CHROME_HOME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"

/** The honest error texts of the kids-content fallback. */
private const val KIDS_ATT =
    "This video is marked \"made for kids\", so YouTube only serves it through its attested " +
        "SABR streaming, which requires a browser attestation (\"PO token\") this player " +
        "cannot generate yet. Playback is unavailable; try the video in a browser."

private const val KIDS_SABR_FORMAT =
    "This video streams over YouTube's SABR delivery, whose media segments this player cannot " +
        "read yet. Playback is unavailable; try the video in a browser."

/**
 * Turns a kiosk id such as `trending_music` into the label `Trending Music`.
 */
private fun kioskDisplayName(id: String): String = id
    .split('_')
    .filter { it.isNotBlank() }
    .joinToString(" ") { word ->
        word.replaceFirstChar { character -> character.uppercaseChar() }
    }

/**
 * The URL of the image with the largest area, or `null` when there is no image.
 */
private fun List<Image>?.bestImageUrl(): String? = this
    ?.maxByOrNull { it.width.toLong() * it.height.toLong() }
    ?.url

/**
 * Converts an extraction engine item into a platform independent [ContentItem].
 */
internal fun InfoItem.toContentItem(): ContentItem? {
    val type = when (infoType) {
        InfoItem.InfoType.STREAM -> ContentType.STREAM
        InfoItem.InfoType.CHANNEL -> ContentType.CHANNEL
        InfoItem.InfoType.PLAYLIST -> ContentType.PLAYLIST
        else -> return null
    }

    var item = ContentItem(
        serviceId = serviceId,
        url = url,
        title = name.orEmpty(),
        type = type,
        thumbnailUrl = thumbnailUrl
    )

    when (this) {
        is StreamInfoItem -> item = item.copy(
            uploader = uploaderName,
            uploaderUrl = uploaderUrl,
            durationSeconds = duration.takeIf { it > 0 },
            viewCount = viewCount.takeIf { it >= 0 },
            description = shortDescription,
            uploadDate = textualUploadDate,
            liveNow = streamType == StreamType.LIVE_STREAM
        )

        is ChannelInfoItem -> item = item.copy(
            description = description,
            viewCount = subscriberCount.takeIf { it >= 0 }
        )

        is PlaylistInfoItem -> item = item.copy(
            uploader = uploaderName,
            uploaderUrl = uploaderUrl
        )
    }

    return item
}

/**
 * Converts an extraction engine video stream into a [MediaStream].
 */
private fun org.schabi.newpipe.extractor.stream.VideoStream.toMediaStream(): MediaStream? {
    val streamUrl = @Suppress("DEPRECATION") url ?: return null
    return MediaStream(
        url = streamUrl,
        formatId = itag.toString(),
        quality = @Suppress("DEPRECATION") resolution,
        codec = codec.orEmpty(),
        bitrate = bitrate,
        isVideoOnly = @Suppress("DEPRECATION") isVideoOnly,
        width = width.takeIf { it > 0 },
        height = height.takeIf { it > 0 },
        fps = fps.takeIf { it > 0 }
    )
}

/**
 * Converts an extraction engine audio stream into a [MediaStream].
 */
private fun org.schabi.newpipe.extractor.stream.AudioStream.toMediaStream(): MediaStream? {
    val streamUrl = @Suppress("DEPRECATION") url ?: return null
    return MediaStream(
        url = streamUrl,
        formatId = itag.toString(),
        quality = quality.orEmpty(),
        codec = codec.orEmpty(),
        bitrate = bitrate,
        isVideoOnly = false,
        audioTrackName = audioTrackName ?: audioTrackId
    )
}

actual fun createContentRepository(): ContentRepository = ExtractorContentRepository()
