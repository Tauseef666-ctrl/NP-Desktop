/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
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
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

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

    override suspend fun search(request: SearchRequest): List<ContentItem> = withContext(Dispatchers.IO) {
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
            val service = serviceForId(serviceId)
            val kioskList = service.kioskList
                ?: throw ContentException.Unsupported("The service does not provide kiosks")
            // Use the extractor directly instead of KioskInfo: KioskInfo also parses the
            // localized name of the kiosk, which is not needed here and can fail when the
            // service changes the shape of its response header.
            val extractor = kioskList.getExtractorById(kioskId, null)
            extractor.fetchPage()

            val key = "$serviceId:$kioskId"
            val cursors = kioskCursors.getOrPut(key) { mutableListOf(null) }
            synchronized(cursors) {
                while (cursors.size <= pageIndex) {
                    if (cursors.last() == null && cursors.size > 1) return@guard emptyList()
                    val page = if (cursors.size == 1) {
                        extractor.initialPage
                    } else {
                        extractor.getPage(cursors.last()!!)
                    }
                    cursors.add(page.nextPage)
                }

                val page = if (pageIndex == 0) {
                    extractor.initialPage
                } else {
                    extractor.getPage(cursors[pageIndex]!!)
                }
                page.items.mapNotNull { it.toContentItem() }
            }
        }
    }

    override suspend fun streamDetails(serviceId: Int, url: String): StreamDetails =
        withContext(Dispatchers.IO) {
            guard {
                val service = serviceForId(serviceId)
                val info = StreamInfo.getInfo(service, url)
                StreamDetails(
                    serviceId = serviceId,
                    url = info.url ?: url,
                    title = info.name.orEmpty(),
                    uploader = info.uploaderName.orEmpty(),
                    uploaderUrl = info.uploaderUrl,
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
                        val url = it.url ?: return@mapNotNull null
                        SubtitleTrack(
                            url = url,
                            name = it.name ?: it.displayLanguageName.orEmpty(),
                            languageTag = it.languageTag
                        )
                    },
                    relatedItems = info.relatedStreams.mapNotNull { it.toContentItem() }
                )
            }
        }

    override suspend fun channel(serviceId: Int, url: String): ChannelDetails =
        withContext(Dispatchers.IO) {
            guard {
                val service = serviceForId(serviceId)
                val info = ChannelInfo.getInfo(service, url)
                val items = info.tabs.asSequence()
                    .mapNotNull { tab ->
                        try {
                            ChannelTabInfo.getInfo(service, tab).relatedItems
                        } catch (exception: Exception) {
                            Logger.w(
                                messageString = "A channel tab could not be loaded",
                                throwable = exception
                            )
                            null
                        }
                    }
                    .firstOrNull { it.isNotEmpty() }
                    ?.mapNotNull { it.toContentItem() }
                    ?: emptyList()
                ChannelDetails(
                    serviceId = serviceId,
                    url = info.url ?: url,
                    name = info.name.orEmpty(),
                    avatarUrl = info.avatars.bestImageUrl(),
                    bannerUrl = info.banners.bestImageUrl(),
                    description = info.description,
                    subscriberCount = info.subscriberCount.takeIf { it >= 0 },
                    items = items
                )
            }
        }

    override suspend fun playlist(serviceId: Int, url: String): PlaylistDetails =
        withContext(Dispatchers.IO) {
            guard {
                val service = serviceForId(serviceId)
                val info = PlaylistInfo.getInfo(service, url)
                PlaylistDetails(
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
        pageIndex: Int
    ): List<InfoItem> {
        val key = "${service.serviceId}|$filter|$query"

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

/** The id of the "Trending" kiosk of the extraction engine. */
private const val TRENDING_KIOSK_ID = "Trending"

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
    val streamUrl = url ?: return null
    return MediaStream(
        url = streamUrl,
        formatId = itag.toString(),
        quality = resolution,
        codec = codec.orEmpty(),
        bitrate = bitrate,
        isVideoOnly = isVideoOnly,
        width = width.takeIf { it > 0 },
        height = height.takeIf { it > 0 },
        fps = fps.takeIf { it > 0 }
    )
}

/**
 * Converts an extraction engine audio stream into a [MediaStream].
 */
private fun org.schabi.newpipe.extractor.stream.AudioStream.toMediaStream(): MediaStream? {
    val streamUrl = url ?: return null
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
