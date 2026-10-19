/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import org.koin.core.annotation.Singleton
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A real [MediaProvider] backed by NewPipeExtractor on the JVM.
 *
 * The provider wraps the YouTube service exposed by [ServiceList]. It is honest about its limits:
 *
 *  * Every listing (search, trending, channels and playlists) paginates: the extractor's next-page
 *    state (endpoint, continuation id and POST body) is packed into an opaque
 *    [ProviderContentPage.nextPageToken] and accepted back on the following call. A null/blank
 *    incoming `pageToken` fetches the first page; a null next-page token means the
 *    provider has no more content for that listing. Channels expose their content tabs (Videos,
 *    Shorts, Live, Playlists, …) and each tab paginates the same way.
 *  * Extraction is blocking and runs on the caller's dispatcher; [ProviderContentRepository] already
 *    moves provider calls off the main dispatcher.
 *  * If NewPipeExtractor fails to initialise, [isAvailable] is `false` and the provider advertises
 *    itself as unusable instead of crashing the Koin graph.
 *  * Stream metadata is read from the same page as playback, so [streamMetadata] performs no extra
 *    request beyond the one [resolve] already needs.
 *  * Subtitle/caption tracks are likewise read from that same page, so [subtitles] adds no request
 *    beyond the one the stream page already needed.
 *  * Live streams (including audio-only live) are reported with [ProviderContentKind.LIVE], and
 *    search can be restricted to a content filter (Videos/Channels/Playlists) via [searchFiltered].
 */
@Singleton(binds = [MediaProvider::class])
class ExtractorMediaProvider : MediaProvider {

    override val id: String = ID

    override val name: String = SERVICE_NAME

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        search = true,
        trending = true,
        subscriptions = false,
        channels = true,
        playlists = true,
        resolve = true
    )

    init {
        // Warm the extractor on a background daemon thread the moment this provider is created —
        // Koin's eager registrar creates it during graph start — so neither Koin start nor any UI
        // thread ever runs NewPipe.init, and the first content request normally finds it done.
        startInitOnce()
    }

    override val isAvailable: Boolean get() = ensureInitialized()

    /**
     * Search YouTube. A null/blank [pageToken] fetches the first page; otherwise the token is handed
     * back to the extractor to continue where the previous page stopped (see class KDoc).
     */
    override suspend fun search(query: String, pageToken: String?): ProviderResult<ProviderContentPage> =
        guarded {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            val page = resolvePage(pageToken, extractor)
            pureMapPage(page, pureNextPageToken(page))
        }

    /**
     * Search YouTube restricted to [filter] (Videos/Channels/Playlists). [SearchFilter.ALL] keeps the
     * search unfiltered. A null/blank [pageToken] fetches the first page; otherwise the token
     * continues the same filtered search (see class KDoc).
     */
    override suspend fun searchFiltered(
        query: String,
        filter: SearchFilter,
        pageToken: String?
    ): ProviderResult<ProviderContentPage> =
        guarded {
            val extractor = ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(contentFilterId(filter)).filter { it.isNotBlank() },
                ""
            )
            val page = resolvePage(pageToken, extractor)
            pureMapPage(page, pureNextPageToken(page))
        }

    /**
     * Load the default YouTube kiosk. A null/blank [pageToken] fetches the first page; otherwise the
     * token continues the previous page (see class KDoc).
     */
    override suspend fun trending(pageToken: String?): ProviderResult<ProviderContentPage> =
        guarded {
            val extractor = ServiceList.YouTube.kioskList.defaultKioskExtractor
            val page = resolvePage(pageToken, extractor)
            pureMapPage(page, pureNextPageToken(page))
        }

    /**
     * Load the first content tab (preferring the videos tab) of the channel at [url]. A null/blank
     * [pageToken] fetches the first page; otherwise the token continues the same tab (see class KDoc).
     */
    override suspend fun channel(url: String, pageToken: String?): ProviderResult<ProviderContentPage> =
        guarded {
            val tabs = fetchedChannel(url).tabs
            if (tabs.isEmpty()) {
                throw ParsingException("Channel has no content tabs: $url")
            }
            val videosTab = tabs.firstOrNull {
                it.url.contains("/videos", ignoreCase = true) ||
                    it.id.contains("videos", ignoreCase = true)
            } ?: tabs.first()
            val page = resolvePage(pageToken, ServiceList.YouTube.getChannelTabExtractor(videosTab))
            pureMapPage(page, pureNextPageToken(page))
        }

    /**
     * List the selectable content tabs of the channel at [url], mapping each extractor tab id to a
     * display label (see [channelTabLabel]).
     */
    override suspend fun channelTabs(url: String): ProviderResult<List<ProviderChannelTab>> =
        guarded {
            fetchedChannel(url).tabs.map { tab ->
                ProviderChannelTab(id = tab.id, label = channelTabLabel(tab.id))
            }
        }

    /**
     * Load one page of the channel tab [tabId] of the channel at [url]. The tab is matched by exact
     * id first, then case-insensitively as a substring. A null/blank [pageToken] fetches the first
     * page; otherwise the token continues the same tab (see class KDoc).
     */
    override suspend fun channelTab(
        url: String,
        tabId: String,
        pageToken: String?
    ): ProviderResult<ProviderContentPage> =
        guarded {
            val tabs = fetchedChannel(url).tabs
            if (tabs.isEmpty()) throw ParsingException("Channel has no content tabs: $url")
            val tab = tabs.firstOrNull { it.id == tabId }
                ?: tabs.firstOrNull { it.id.contains(tabId, ignoreCase = true) }
                ?: throw ParsingException("Channel has no tab '$tabId': $url")
            val page = resolvePage(pageToken, ServiceList.YouTube.getChannelTabExtractor(tab))
            pureMapPage(page, pureNextPageToken(page))
        }

    /**
     * Load one page of the playlist at [url]. A blank [pageToken] fetches the initial page; otherwise
     * the token is handed back to the extractor to continue where the previous page stopped.
     */
    override suspend fun playlist(url: String, pageToken: String?): ProviderResult<ProviderContentPage> =
        guarded {
            val page = resolvePage(pageToken, ServiceList.YouTube.getPlaylistExtractor(url))
            pureMapPage(page, pureNextPageToken(page))
        }

    override suspend fun resolve(url: String): ProviderResult<ResolvedMedia> {
        val result = guarded {
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            val isLive = isLiveStreamType(info.streamType)
            pickResolvedMedia(
                isLive = isLive,
                hlsUrl = info.hlsUrl,
                durationSeconds = info.duration,
                progressiveVideo = info.videoStreams.mapNotNull { stream ->
                    stream.url?.let { VideoCandidate(it, stream.height) }
                },
                audioStreams = info.audioStreams.mapNotNull { it.url }
            )
        }
        return when (result) {
            is ProviderResult.Success ->
                result.value?.let { ProviderResult.Success(it) }
                    ?: ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED, "No playable stream found for $url")
            is ProviderResult.Failure -> result
        }
    }

    /**
     * Read the descriptive [StreamMetadata] of the stream page at [url] from the same extractor page
     * that playback uses.
     */
    override suspend fun streamMetadata(url: String): ProviderResult<StreamMetadata> =
        guarded {
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            val isLive = isLiveStreamType(info.streamType)
            pureStreamMetadata(
                title = info.name,
                uploaderName = info.uploaderName,
                uploaderUrl = info.uploaderUrl,
                uploaderVerified = info.isUploaderVerified,
                subscriberCount = info.uploaderSubscriberCount,
                viewCount = info.viewCount,
                likeCount = info.likeCount,
                textualUploadDate = info.textualUploadDate,
                durationSeconds = info.duration,
                thumbnailUrl = bestThumbnail(info.thumbnails),
                description = info.description?.content(),
                category = info.category,
                tags = info.tags.orEmpty(),
                isLive = isLive
            )
        }

    /**
     * List the subtitle/caption tracks of the stream page at [url] from the same extractor page that
     * playback and metadata use.
     */
    override suspend fun subtitles(url: String): ProviderResult<List<SubtitleTrack>> =
        guarded {
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            pureSubtitleTracks(
                info.subtitles.orEmpty().map { track ->
                    SubtitleFields(
                        languageTag = track.languageTag,
                        displayName = track.displayLanguageName,
                        isAutoGenerated = track.isAutoGenerated,
                        format = track.extension,
                        url = track.url
                    )
                }
            )
        }

    private fun <T> guarded(block: () -> T): ProviderResult<T> {
        return try {
            ProviderResult.Success(block())
        } catch (e: ReCaptchaException) {
            logFailure("reCAPTCHA", e, withStack = false)
            ProviderResult.Failure(ProviderFailureReason.NETWORK, "reCAPTCHA required: ${e.message}")
        } catch (e: IOException) {
            logFailure("network", e, withStack = false)
            ProviderResult.Failure(ProviderFailureReason.NETWORK, e.message)
        } catch (e: ParsingException) {
            logFailure("parse", e, withStack = false)
            ProviderResult.Failure(ProviderFailureReason.PARSE, e.message)
        } catch (e: ExtractionException) {
            logFailure("extraction", e, withStack = false)
            ProviderResult.Failure(ProviderFailureReason.PARSE, e.message)
        } catch (e: Exception) {
            // The user-visible string for UNKNOWN discards detail, so keep the class + message in
            // the failure detail AND dump the full stack to stderr — without this the real cause of
            // "Something went wrong while loading content." was invisible.
            logFailure("unmapped", e, withStack = true)
            ProviderResult.Failure(ProviderFailureReason.UNKNOWN, "${e.javaClass.name}: ${e.message}")
        }
    }

    private fun logFailure(kind: String, e: Exception, withStack: Boolean) {
        System.err.println("[NewPipe+] extractor $kind failure: ${e.javaClass.name}: ${e.message}")
        if (withStack) e.printStackTrace()
    }

    companion object {
        const val ID = "newpipe-extractor"
        private const val SERVICE_NAME = "YouTube"

        /** The provider content-filter id for [filter]; an empty string means "no filter" (all). */
        fun contentFilterId(filter: SearchFilter): String =
            if (filter == SearchFilter.ALL) "" else filter.id

        /** True when [type] is a stream that is currently live (video or audio). */
        fun isLiveStreamType(type: StreamType): Boolean =
            type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM

        private val initStarted = AtomicBoolean(false)
        private val initResult = CompletableFuture<Boolean>()

        /**
         * Start the one-time extractor init on a background daemon thread. Never blocks the
         * caller, so constructing this provider during Koin graph creation (on the UI thread,
         * before the first frame) neither runs nor waits for [NewPipe.init].
         */
        private fun startInitOnce() {
            if (initStarted.compareAndSet(false, true)) {
                Thread(
                    {
                        var ok = false
                        try {
                            NewPipe.init(JVMHttpDownloader(), Localization.DEFAULT)
                            ok = true
                        } catch (e: Exception) {
                            ok = false
                        } finally {
                            // Completes even if init dies with an Error, so no reader can hang.
                            initResult.complete(ok)
                        }
                    },
                    "newpipe-extractor-init"
                ).apply {
                    isDaemon = true
                    start()
                }
            }
        }

        /**
         * True once the process-wide [NewPipe.init] has succeeded. The first caller starts the
         * background init; every caller waits for its result, so a concurrent reader can never
         * observe "unavailable" while the init is still in flight. Any failure is reported as
         * `false` so a broken extractor can never crash dependency injection.
         */
        private fun ensureInitialized(): Boolean {
            startInitOnce()
            return try {
                initResult.get()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }

        /**
         * Pure mapping of one extractor [InfoItem] into the provider-agnostic
         * [ProviderContentItem], or `null` for items this provider does not surface (comments).
         */
        fun pureMapItem(item: InfoItem): ProviderContentItem? = when (item.infoType) {
            InfoItem.InfoType.STREAM -> {
                val stream = item as StreamInfoItem
                ProviderContentItem(
                    id = item.url,
                    title = item.name,
                    url = item.url,
                    thumbnailUrl = bestThumbnail(item.thumbnails),
                    uploader = stream.uploaderName?.takeIf { it.isNotBlank() },
                    uploaderUrl = stream.uploaderUrl?.takeIf { it.isNotBlank() },
                    uploaderAvatarUrl = bestThumbnail(stream.uploaderAvatars),
                    durationSeconds = stream.duration.takeIf { it > 0L },
                    viewCount = stream.viewCount.takeIf { it >= 0L },
                    textualUploadDate = stream.textualUploadDate?.takeIf { it.isNotBlank() },
                    kind = if (isLiveStreamType(stream.streamType)) {
                        ProviderContentKind.LIVE
                    } else {
                        ProviderContentKind.VIDEO
                    }
                )
            }

            InfoItem.InfoType.CHANNEL -> {
                // ChannelInfoItem carries a description, but ProviderContentItem has no subtitle
                // field, so only the shared fields are filled in.
                ProviderContentItem(
                    id = item.url,
                    title = item.name,
                    url = item.url,
                    thumbnailUrl = bestThumbnail(item.thumbnails),
                    kind = ProviderContentKind.CHANNEL
                )
            }

            InfoItem.InfoType.PLAYLIST -> {
                val playlist = item as PlaylistInfoItem
                ProviderContentItem(
                    id = item.url,
                    title = item.name,
                    url = item.url,
                    thumbnailUrl = bestThumbnail(item.thumbnails),
                    uploader = playlist.uploaderName?.takeIf { it.isNotBlank() },
                    uploaderUrl = playlist.uploaderUrl?.takeIf { it.isNotBlank() },
                    kind = ProviderContentKind.PLAYLIST
                )
            }

            InfoItem.InfoType.COMMENT -> null

            else -> null
        }

        /**
         * Pure mapping of a page into a [ProviderContentPage], dropping items that have no
         * representation. The optional [nextPageToken] is carried over verbatim; callers that only
         * pass a page keep the previous behaviour of a `null` next token.
         */
        fun pureMapPage(
            page: ListExtractor.InfoItemsPage<InfoItem>,
            nextPageToken: String? = null
        ): ProviderContentPage =
            ProviderContentPage(
                items = page.items.orEmpty().mapNotNull(::pureMapItem),
                nextPageToken = nextPageToken
            )

        /**
         * The opaque continuation token for [page]'s next page, or `null` when the listing is
         * exhausted (no next page, or an empty one). Continuation requests need everything the
         * extractor packed into its [Page] — endpoint, continuation id and the POST body (YouTube's
         * list continuations post JSON, they do not fetch a URL) — so all three are base64url-
         * encoded into one URL-safe string that the UI only ever round-trips.
         */
        fun pureNextPageToken(page: ListExtractor.InfoItemsPage<*>): String? {
            val next = page.nextPage ?: return null
            val url = next.url.orEmpty()
            val id = next.id.orEmpty()
            val body = next.body
            if (url.isBlank() && id.isBlank() && body == null) return null
            val encodedBody = body?.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
                .orEmpty()
            return TOKEN_PREFIX +
                encodeTokenPart(url) + "|" +
                encodeTokenPart(id) + "|" +
                encodedBody
        }

        /**
         * Rebuild the extractor [Page] a continuation token was made of. Anything that is not in
         * this provider's own `v1|` format is treated as a bare page URL (the format used before
         * tokens carried state), so no already-issued token can brick pagination.
         *
         * @throws ParsingException when the token claims to be ours but is structurally broken
         */
        internal fun decodePageToken(token: String): Page {
            if (!token.startsWith(TOKEN_PREFIX)) return Page(token)
            val parts = token.removePrefix(TOKEN_PREFIX).split('|')
            if (parts.size != 3) throw ParsingException("Malformed pagination token")
            val body = parts[2].takeIf { it.isNotEmpty() }
                ?.let { Base64.getUrlDecoder().decode(it) }
            return Page(decodeTokenPart(parts[0]), decodeTokenPart(parts[1]), body)
        }

        private fun encodeTokenPart(value: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

        private fun decodeTokenPart(value: String): String =
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

        private const val TOKEN_PREFIX = "v1|"

        /**
         * The channel extractor at [url] after its page has been fetched. `tabs` is populated by
         * `onFetchPage`, so touching the tabs of an unfetched channel extractor yields an empty tab
         * list (surfacing as a "no content tabs" parse failure).
         */
        private fun fetchedChannel(url: String) =
            ServiceList.YouTube.getChannelExtractor(url).also { it.fetchPage() }

        /**
         * Pure mapping of an extractor channel tab id to a display label. Unknown ids fall back to
         * the provider's own id so the UI never shows a blank tab.
         */
        fun channelTabLabel(id: String): String = when (id.lowercase()) {
            ChannelTabs.VIDEOS -> "Videos"
            ChannelTabs.TRACKS -> "Tracks"
            ChannelTabs.SHORTS -> "Shorts"
            ChannelTabs.LIVESTREAMS, "streams", "live" -> "Live"
            ChannelTabs.CHANNELS -> "Channels"
            ChannelTabs.PLAYLISTS -> "Playlists"
            ChannelTabs.ALBUMS, "releases" -> "Albums"
            ChannelTabs.LIKES -> "Likes"
            ChannelTabs.PODCASTS -> "Podcasts"
            ChannelTabs.COURSES -> "Courses"
            else -> id
        }

        /**
         * Fetch one page of [extractor]'s listing: the initial page for a null/blank [pageToken],
         * otherwise the page the token points at. Widened to a concrete [InfoItem] page because the
         * extractor's item type varies per listing but the mapper always works on [InfoItem].
         *
         * The initial branch must call [Extractor.fetchPage] first: `onFetchPage` is what performs
         * the request and populates the extractor's parsed state (`initialData`/`responseData`);
         * reading `initialPage` straight after construction dereferences that state while it is
         * still null — that NPE surfaced to users as "Something went wrong while loading content."
         * Continuation pages carry their own request state in the token, so they need no fetch.
         */
        private fun resolvePage(
            pageToken: String?,
            extractor: ListExtractor<*>
        ): ListExtractor.InfoItemsPage<InfoItem> {
            val page = if (pageToken.isNullOrBlank()) {
                extractor.fetchPage()
                extractor.initialPage
            } else {
                extractor.getPage(decodePageToken(pageToken))
            }
            return widen(page)
        }

        private fun bestThumbnail(images: List<Image>?): String? {
            if (images.isNullOrEmpty()) return null
            val known = images.filter { it.height != Image.HEIGHT_UNKNOWN && it.height > 0 }
            val best = known.maxByOrNull { it.height } ?: images.first()
            return best.url
        }

        @Suppress("UNCHECKED_CAST")
        private fun widen(page: ListExtractor.InfoItemsPage<*>): ListExtractor.InfoItemsPage<InfoItem> =
            page as ListExtractor.InfoItemsPage<InfoItem>

        /** A progressive video rendition candidate: its direct URL and nominal height. */
        data class VideoCandidate(val url: String, val height: Int)

        /**
         * Pure selection of a playable rendition. Live streams prefer their HLS manifest; otherwise the
         * highest-height progressive (muxed audio+video) stream is used, falling back to the first audio-only
         * stream when no progressive stream exists. Returns `null` when nothing playable is available.
         */
        fun pickResolvedMedia(
            isLive: Boolean,
            hlsUrl: String?,
            durationSeconds: Long,
            progressiveVideo: List<VideoCandidate>,
            audioStreams: List<String>
        ): ResolvedMedia? {
            if (isLive) {
                hlsUrl?.takeIf { it.isNotBlank() }?.let {
                    return ResolvedMedia(streamUrl = it, isLive = true, audioOnly = false, durationSeconds = null)
                }
            }
            val knownDuration = durationSeconds.takeIf { it > 0L }
            val bestVideo = progressiveVideo.filter { it.url.isNotBlank() }.maxByOrNull { it.height }
            if (bestVideo != null) {
                return ResolvedMedia(
                    streamUrl = bestVideo.url,
                    isLive = isLive,
                    audioOnly = false,
                    durationSeconds = if (isLive) null else knownDuration
                )
            }
            val audio = audioStreams.firstOrNull { it.isNotBlank() }
            if (audio != null) {
                return ResolvedMedia(
                    streamUrl = audio,
                    isLive = isLive,
                    audioOnly = true,
                    durationSeconds = if (isLive) null else knownDuration
                )
            }
            return null
        }

        /**
         * Pure mapping of a stream page's raw values into a [StreamMetadata], normalising absent or
         * empty values to `null` so the UI never shows placeholders. No network access.
         */
        fun pureStreamMetadata(
            title: String,
            uploaderName: String?,
            uploaderUrl: String?,
            uploaderVerified: Boolean,
            subscriberCount: Long,
            viewCount: Long,
            likeCount: Long,
            textualUploadDate: String?,
            durationSeconds: Long,
            thumbnailUrl: String?,
            description: String?,
            category: String?,
            tags: List<String>,
            isLive: Boolean
        ): StreamMetadata = StreamMetadata(
            title = title,
            uploaderName = uploaderName?.takeIf { it.isNotBlank() },
            uploaderUrl = uploaderUrl?.takeIf { it.isNotBlank() },
            uploaderVerified = uploaderVerified,
            subscriberCount = subscriberCount.takeIf { it > 0L },
            viewCount = viewCount.takeIf { it > 0L },
            likeCount = likeCount.takeIf { it > 0L },
            uploadDate = textualUploadDate?.takeIf { it.isNotBlank() },
            durationSeconds = durationSeconds.takeIf { it > 0L },
            thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
            description = description?.takeIf { it.isNotBlank() },
            category = category?.takeIf { it.isNotBlank() },
            tags = tags.filter { it.isNotBlank() },
            isLive = isLive
        )

        /** The raw subtitle fields of one extractor track, as read off a stream page. */
        data class SubtitleFields(
            val languageTag: String?,
            val displayName: String?,
            val isAutoGenerated: Boolean,
            val format: String?,
            val url: String?
        )

        /**
         * Pure mapping of raw subtitle [fields] into [SubtitleTrack]s. Entries with neither a display
         * name nor a language tag are dropped; blank optional values are normalised to `null`. No
         * network access.
         */
        fun pureSubtitleTracks(fields: List<SubtitleFields>): List<SubtitleTrack> =
            fields.mapNotNull { field ->
                val tag = field.languageTag?.trim().orEmpty()
                val name = field.displayName?.trim().takeUnless { it.isNullOrBlank() }
                    ?: tag.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                SubtitleTrack(
                    id = buildString {
                        append(tag.ifBlank { name })
                        append('|')
                        append(if (field.isAutoGenerated) "auto" else "manual")
                        append('|')
                        append(field.format.orEmpty())
                    },
                    languageTag = tag.ifBlank { name },
                    displayName = name,
                    isAutoGenerated = field.isAutoGenerated,
                    format = field.format?.trim()?.takeIf { it.isNotBlank() },
                    url = field.url?.trim()?.takeIf { it.isNotBlank() }
                )
            }
    }
}
