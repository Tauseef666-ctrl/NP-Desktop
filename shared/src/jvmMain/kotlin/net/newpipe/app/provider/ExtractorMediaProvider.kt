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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A real [MediaProvider] backed by NewPipeExtractor on the JVM.
 *
 * The provider wraps the YouTube service exposed by [ServiceList]. It is honest about its limits:
 *
 *  * Every listing (search, trending, channels and playlists) paginates: the extractor's next-page
 *    URL is surfaced as the [ProviderContentPage.nextPageToken] and accepted back on the following
 *    call. A null/blank incoming `pageToken` fetches the first page; a null next-page token means the
 *    provider has no more content for that listing. Channels expose their content tabs (Videos,
 *    Shorts, Live, Playlists, …) and each tab paginates the same way.
 *  * Extraction is blocking and runs on the caller's dispatcher; [ProviderContentRepository] already
 *    moves provider calls off the main dispatcher.
 *  * If NewPipeExtractor fails to initialise, [isAvailable] is `false` and the provider advertises
 *    itself as unusable instead of crashing the Koin graph.
 *  * Stream metadata is read from the same page as playback, so [streamMetadata] performs no extra
 *    request beyond the one [resolve] already needs.
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

    override val isAvailable: Boolean = ensureInitialized()

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
            val tabs = ServiceList.YouTube.getChannelExtractor(url).tabs
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
            ServiceList.YouTube.getChannelExtractor(url).tabs.map { tab ->
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
            val tabs = ServiceList.YouTube.getChannelExtractor(url).tabs
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
            val isLive = info.streamType == StreamType.LIVE_STREAM ||
                info.streamType == StreamType.AUDIO_LIVE_STREAM
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
            val isLive = info.streamType == StreamType.LIVE_STREAM ||
                info.streamType == StreamType.AUDIO_LIVE_STREAM
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

    private fun <T> guarded(block: () -> T): ProviderResult<T> {
        return try {
            ProviderResult.Success(block())
        } catch (e: ReCaptchaException) {
            ProviderResult.Failure(ProviderFailureReason.NETWORK, "reCAPTCHA required: ${e.message}")
        } catch (e: IOException) {
            ProviderResult.Failure(ProviderFailureReason.NETWORK, e.message)
        } catch (e: ParsingException) {
            ProviderResult.Failure(ProviderFailureReason.PARSE, e.message)
        } catch (e: ExtractionException) {
            ProviderResult.Failure(ProviderFailureReason.PARSE, e.message)
        } catch (e: Exception) {
            ProviderResult.Failure(ProviderFailureReason.UNKNOWN, e.message)
        }
    }

    companion object {
        const val ID = "newpipe-extractor"
        private const val SERVICE_NAME = "YouTube"

        private val initStarted = AtomicBoolean(false)
        private val initSucceeded = AtomicBoolean(false)

        /**
         * Initialise NewPipeExtractor exactly once for the process. The first caller wins the
         * compare-and-set; later callers only read the outcome. Any failure is swallowed into a
         * `false` result so a broken extractor can never crash dependency injection.
         */
        private fun ensureInitialized(): Boolean {
            if (initStarted.compareAndSet(false, true)) {
                try {
                    NewPipe.init(JVMHttpDownloader(), Localization.DEFAULT)
                    initSucceeded.set(true)
                } catch (e: Exception) {
                    initSucceeded.set(false)
                }
            }
            return initSucceeded.get()
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
                    durationSeconds = stream.duration.takeIf { it > 0L },
                    kind = if (stream.streamType == StreamType.LIVE_STREAM) {
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
         * The opaque token that continues a paginated listing: the extractor's next-page URL, or
         * `null` when the listing is exhausted (no next page, or a blank URL).
         */
        fun pureNextPageToken(page: ListExtractor.InfoItemsPage<*>): String? =
            page.nextPage?.url?.takeIf { it.isNotBlank() }

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
         */
        private fun resolvePage(
            pageToken: String?,
            extractor: ListExtractor<*>
        ): ListExtractor.InfoItemsPage<InfoItem> {
            val page = if (pageToken.isNullOrBlank()) {
                extractor.initialPage
            } else {
                extractor.getPage(Page(pageToken))
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
    }
}
