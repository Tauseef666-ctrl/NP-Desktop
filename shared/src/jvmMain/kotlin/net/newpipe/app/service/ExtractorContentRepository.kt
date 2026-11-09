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
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
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
    private val streamCache = ResultsCache<CacheKey, StreamDetails>(ttlMillis = 5 * 60_000L)
    private val channelCache = ResultsCache<CacheKey, ChannelDetails>(ttlMillis = 10 * 60_000L)
    private val playlistCache = ResultsCache<CacheKey, PlaylistDetails>(ttlMillis = 10 * 60_000L)
    private val kioskCache = ResultsCache<CacheKey, List<ContentItem>>(ttlMillis = 2 * 60_000L)
    private val searchCache = ResultsCache<CacheKey, List<ContentItem>>(ttlMillis = 2 * 60_000L)

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
            val filters = listOfNotNull(request.filter)
            val handler = service.searchQHFactory.fromQuery(request.query, filters, null)
            val items = loadSearchPage(service, handler, request.query, request.filter, request.page)
            items.mapNotNull { it.toContentItem() }
        }
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
                    listOf(TRENDING_FALLBACK_FILTER),
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
            val info = withBotWallRetry(service, url)
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
                        name = it.name ?: it.displayLanguageName.orEmpty(),
                        languageTag = it.languageTag
                    )
                },
                relatedItems =
                    @Suppress("DEPRECATION") info.relatedStreams.mapNotNull { it.toContentItem() }
            )
        } catch (exception: Exception) {
            // "Made for kids" videos answer the extraction engine's default visionOS client with
            // UNPLAYABLE; YouTube still serves them through the iOS player client, which becomes
            // the playback fallback of this repository.
            if (isKidsUnplayable(exception, url)) {
                kidsPlaybackFallback(serviceId, url, exception)
            } else {
                throw exception
            }
        }
    }

    /**
     * True when the extraction failed with the playability error that "made for kids" videos give
     * back through the engine's default visionOS client: `Got error UNPLAYABLE: "This video is not
     * available"`. Only YouTube watch URLs are eligible, other services keep their exact errors.
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
            if (message.contains("UNPLAYABLE") &&
                message.contains("This video is not available")
            ) {
                return true
            }
            current = current.cause
            hops++
        }
        return false
    }

    /**
     * Replays a blocked (usually "made for kids") YouTube video through the iOS player client,
     * which is still served playable streaming URLs, and assembles the [StreamDetails] directly
     * from the returned InnerTube player response. When the retry does not yield a playable
     * response, the original extraction failure is rethrown so the user interface keeps its
     * precise error message.
     */
    private fun kidsPlaybackFallback(
        serviceId: Int,
        url: String,
        original: Exception
    ): StreamDetails {
        Logger.i(
            messageString =
                "YouTube denied the stream to the default client; retrying with the iOS player client"
        )
        val videoId = videoIdOfUrl(url) ?: throw original
        val response = try {
            iosPlayerResponse(videoId)
        } catch (retry: Exception) {
            throw original
        }
        val status = response.getObject("playabilityStatus")?.getString("status").orEmpty()
        val videoDetails = response.getObject("videoDetails")
        val streamingData = response.getObject("streamingData")
        if (!status.equals("ok", ignoreCase = true) ||
            videoDetails == null ||
            streamingData == null
        ) {
            throw original
        }

        val videoStreams = ArrayList<MediaStream>()
        val audioStreams = ArrayList<MediaStream>()
        streamingData.getArray("formats")
            ?.let { collectPlayerFormats(it, videoStreams, audioStreams) }
        streamingData.getArray("adaptiveFormats")
            ?.let { collectPlayerFormats(it, videoStreams, audioStreams) }
        if (videoStreams.isEmpty() && audioStreams.isEmpty()) {
            throw original
        }

        val micro = response.getObject("microformat")?.getObject("playerMicroformatRenderer")
        val channelId = videoDetails.getString("channelId")
        return StreamDetails(
            serviceId = serviceId,
            url = url,
            title = videoDetails.getString("title").orEmpty(),
            uploader = videoDetails.getString("author").orEmpty(),
            uploaderUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
            uploaderAvatarUrl = null,
            thumbnailUrl = videoDetails.getObject("thumbnail")?.getArray("thumbnails")
                ?.filterIsInstance<JsonObject>()
                ?.maxByOrNull { (it.getNumber("width") as? Number)?.toInt() ?: 0 }
                ?.getString("url"),
            description = videoDetails.getString("shortDescription"),
            durationSeconds = videoDetails.getString("lengthSeconds")?.toLongOrNull(),
            viewCount = videoDetails.getString("viewCount")?.toLongOrNull()
                ?: (micro?.getNumber("viewCount") as? Number)?.toLong(),
            uploadDate = micro?.getString("uploadDate"),
            liveNow = videoDetails.getBoolean("isLiveContent") ?: false,
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            subtitles = collectPlayerSubtitles(response)
        )
    }

    /**
     * Requests the player response of [videoId] through the InnerTube endpoint while posing as
     * the YouTube iOS app: the client version, the User-Agent and the `X-Youtube-*` headers have
     * to match exactly, otherwise YouTube answers with "Precondition check failed".
     */
    private fun iosPlayerResponse(videoId: String): JsonObject {
        val localization = NewPipe.getPreferredLocalization()
        val contentCountry = NewPipe.getPreferredContentCountry()
            ?: ContentCountry(java.util.Locale.getDefault().country.ifBlank { "US" })
        val client = InnertubeClientRequestInfo.ofVisionOsClient().also {
            it.clientInfo.clientName = "IOS"
            it.clientInfo.clientVersion = IOS_FALLBACK_CLIENT_VERSION
            it.clientInfo.clientId = "5"
            it.clientInfo.clientScreen = "WATCH"
            it.deviceInfo.platform = "MOBILE"
            it.deviceInfo.deviceMake = "Apple"
            it.deviceInfo.deviceModel = IOS_FALLBACK_DEVICE_MODEL
            it.deviceInfo.osName = "iOS"
            it.deviceInfo.osVersion = IOS_FALLBACK_OS_VERSION
        }
        if (client.clientInfo.visitorData == null) {
            client.clientInfo.visitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                InnertubeClientRequestInfo.ofWebClient().also {
                    it.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
                },
                localization,
                contentCountry,
                YoutubeParsingHelper.getYouTubeHeaders(),
                "https://www.youtube.com/youtubei/v1/",
                null,
                false
            )
        }
        val headers = mapOf<String, List<String>>(
            "User-Agent" to listOf(
                "com.google.ios.youtube/$IOS_FALLBACK_CLIENT_VERSION " +
                    "($IOS_FALLBACK_DEVICE_MODEL; U; CPU iOS " +
                    "${IOS_FALLBACK_OS_VERSION.substringBefore(".")} like Mac OS X)"
            ),
            "X-Youtube-Client-Name" to listOf("5"),
            "X-Youtube-Client-Version" to listOf(IOS_FALLBACK_CLIENT_VERSION),
            "X-Apple-Product-Model" to listOf(IOS_FALLBACK_DEVICE_MODEL),
            "X-ios-bundle-identifier" to listOf("com.google.ios.youtube"),
            "X-Goog-Api-Format-Version" to listOf("2")
        )
        val builder = YoutubeParsingHelper.prepareJsonBuilder(
            localization, contentCountry, client, null
        )
        builder.value("videoId", videoId)
            .value("contentCheckOk", true)
            .value("racyCheckOk", true)
        val body = JsonWriter.string(builder.done()).toByteArray(Charsets.UTF_8)
        val response = NewPipe.getDownloader().postWithContentTypeJson(
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            headers,
            body,
            localization
        )
        return JsonParser.`object`().from(
            YoutubeParsingHelper.getValidJsonResponseBody(response)
        )
    }

    /**
     * Adds the playable formats of an InnerTube player response `formats`/`adaptiveFormats` array
     * to the stream lists. The formats carry plain URLs, so no deciphering is needed; muxed
     * formats that also contain audio come first because they are handed to the player directly.
     */
    private fun collectPlayerFormats(
        formats: JsonArray,
        videoStreams: MutableList<MediaStream>,
        audioStreams: MutableList<MediaStream>
    ) {
        for (raw in formats) {
            val format = raw as? JsonObject ?: continue
            val streamUrl = format.getString("url") ?: continue
            val mimeType = format.getString("mimeType").orEmpty()
            val bitrate = (format.getNumber("bitrate") as? Number)?.toInt() ?: 0
            val formatId = (format.getNumber("itag") as? Number)?.toInt()?.toString().orEmpty()
            if (mimeType.startsWith("audio/")) {
                val sampleRate = (format.getNumber("audioSampleRate") as? Number)?.toInt() ?: 0
                val quality = format.getString("audioQuality")
                    ?.removePrefix("AUDIO_QUALITY_")
                    ?.lowercase()
                    ?.replaceFirstChar { it.uppercase() }
                    ?: sampleRate.takeIf { it > 0 }?.let { "${it / 1000} kHz" }
                    ?: ""
                audioStreams += MediaStream(
                    url = streamUrl,
                    formatId = formatId,
                    quality = quality,
                    codec = codecOf(mimeType),
                    bitrate = bitrate,
                    isVideoOnly = false
                )
                continue
            }
            val height = (format.getNumber("height") as? Number)?.toInt() ?: 0
            videoStreams += MediaStream(
                url = streamUrl,
                formatId = formatId,
                quality = format.getString("qualityLabel")
                    ?: height.takeIf { it > 0 }?.let { "${it}p" }
                    ?: "",
                codec = codecOf(mimeType),
                bitrate = bitrate,
                // A video format that also contains an audio codec part is not video-only.
                isVideoOnly = !mimeType.contains("audio"),
                width = ((format.getNumber("width") as? Number)?.toInt() ?: 0)
                    .takeIf { it > 0 },
                height = height.takeIf { it > 0 },
                fps = ((format.getNumber("fps") as? Number)?.toInt() ?: 0)
                    .takeIf { it > 0 }
            )
        }
    }

    /**
     * The caption tracks of an InnerTube player response, matching what the regular extraction
     * delivers for subtitles.
     */
    private fun collectPlayerSubtitles(response: JsonObject): List<SubtitleTrack> {
        val tracks = response.getObject("playerCaptionsTracklistRenderer")
            ?.getArray("captionTracks") ?: return emptyList()
        return tracks.filterIsInstance<JsonObject>().mapNotNull { track ->
            val trackUrl = track.getString("baseUrl") ?: return@mapNotNull null
            val name = track.getObject("name")
            SubtitleTrack(
                url = "$trackUrl&fmt=json3",
                name = name?.getString("simpleText")
                    ?: name?.getString("vName")
                    ?: track.getString("languageCode").orEmpty(),
                languageTag = track.getString("languageCode")
            )
        }
    }

    /** The codecs value of an InnerTube MIME type such as `video/mp4; codecs="avc1.640028"`. */
    private fun codecOf(mimeType: String): String {
        val marker = "codecs=\""
        val start = mimeType.indexOf(marker)
        if (start < 0) {
            return ""
        }
        val end = mimeType.indexOf('"', start + marker.length)
        if (end < 0) {
            return ""
        }
        return mimeType.substring(start + marker.length, end)
    }

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
    } catch (exception: SignInConfirmNotBotException) {
        Logger.w(
            messageString = "YouTube blocked the anonymous access temporarily; retrying once",
            throwable = exception
        )
        Thread.sleep(BOT_WALL_RETRY_DELAY_MS)
        try {
            StreamInfo.getInfo(service, url)
        } catch (anonymousRetry: SignInConfirmNotBotException) {
            if (YtSession.exists()) {
                Logger.w(
                    messageString =
                        "YouTube still blocks the anonymous access; retrying with the web session",
                    throwable = anonymousRetry
                )
                Thread.sleep(BOT_WALL_RETRY_DELAY_MS)
                try {
                    StreamInfo.getInfo(service, url)
                } catch (signedRetry: SignInConfirmNotBotException) {
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
            thumbnailUrl = info.thumbnails.bestImageUrl(),
            description = info.description?.content,
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
        serviceForId(serviceId).searchQHFactory.availableContentFilter.toList()
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
    private val maxSize: Int = 24
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

/**
 * The iOS YouTube app version the kids-content playback fallback presents: YouTube only
 * serves playable streaming data to current client versions, and the version, User-Agent
 * and headers have to match. When these stop working, refresh all three together.
 */
private const val IOS_FALLBACK_CLIENT_VERSION = "21.05.2"

/** The device model the kids-content playback fallback presents alongside the client version. */
private const val IOS_FALLBACK_DEVICE_MODEL = "iPhone16,2"

/** The iOS version the kids-content playback fallback presents alongside the client version. */
private const val IOS_FALLBACK_OS_VERSION = "18.6.2.23G80"

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
        thumbnailUrl = thumbnails.bestImageUrl()
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
            uploaderUrl = uploaderUrl,
            description = description?.content
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
