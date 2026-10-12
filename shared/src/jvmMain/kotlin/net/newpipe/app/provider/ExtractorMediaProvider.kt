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
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A real [MediaProvider] backed by NewPipeExtractor on the JVM.
 *
 * The provider wraps the YouTube service exposed by [ServiceList]. It is honest about its limits:
 *
 *  * Only the **first page** of every listing is returned; an incoming `pageToken` is ignored and
 *    [ProviderContentPage.nextPageToken] is always `null`. Pagination is a later milestone.
 *  * Extraction is blocking and runs on the caller's dispatcher; [ProviderContentRepository] already
 *    moves provider calls off the main dispatcher.
 *  * If NewPipeExtractor fails to initialise, [isAvailable] is `false` and the provider advertises
 *    itself as unusable instead of crashing the Koin graph.
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
        playlists = true
    )

    override val isAvailable: Boolean = ensureInitialized()

    /** Search YouTube once; [pageToken] is ignored (see class KDoc). */
    override suspend fun search(query: String, pageToken: String?): ProviderResult<ProviderContentPage> =
        guarded {
            pureMapPage(ServiceList.YouTube.getSearchExtractor(query).initialPage)
        }

    /** Load the default YouTube kiosk once; [pageToken] is ignored (see class KDoc). */
    override suspend fun trending(pageToken: String?): ProviderResult<ProviderContentPage> =
        guarded {
            pureMapPage(widen(ServiceList.YouTube.kioskList.defaultKioskExtractor.initialPage))
        }

    /**
     * Load the first content tab (preferring the videos tab) of the channel at [url] once; [pageToken]
     * is ignored (see class KDoc).
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
            pureMapPage(ServiceList.YouTube.getChannelTabExtractor(videosTab).initialPage)
        }

    private fun guarded(block: () -> ProviderContentPage): ProviderResult<ProviderContentPage> {
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
         * Pure mapping of a first page into a [ProviderContentPage], dropping items that have no
         * representation. Pagination is intentionally not carried over yet.
         */
        fun pureMapPage(page: ListExtractor.InfoItemsPage<InfoItem>): ProviderContentPage =
            ProviderContentPage(
                items = page.items.orEmpty().mapNotNull(::pureMapItem),
                nextPageToken = null
            )

        private fun bestThumbnail(images: List<Image>?): String? {
            if (images.isNullOrEmpty()) return null
            val known = images.filter { it.height != Image.HEIGHT_UNKNOWN && it.height > 0 }
            val best = known.maxByOrNull { it.height } ?: images.first()
            return best.url
        }

        @Suppress("UNCHECKED_CAST")
        private fun widen(page: ListExtractor.InfoItemsPage<*>): ListExtractor.InfoItemsPage<InfoItem> =
            page as ListExtractor.InfoItemsPage<InfoItem>
    }
}
