/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import net.newpipe.app.model.ChannelDetails
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.PlaylistDetails
import net.newpipe.app.model.ResolvedContent
import net.newpipe.app.model.ServiceInfo
import net.newpipe.app.model.StreamDetails

/**
 * A request for a single page of search results.
 *
 * @param query the raw search term entered by the user
 * @param serviceId id of the service to search on
 * @param filter the content filter supported by the service (for example `videos` on YouTube);
 * `null` means "use the service default"
 * @param page zero based page index
 */
data class SearchRequest(
    val query: String,
    val serviceId: Int,
    val filter: String? = null,
    val page: Int = 0
)

/**
 * One of the predefined content lists a service offers, for example "Trending" on YouTube.
 *
 * @param id the identifier the extraction engine uses for the kiosk
 * @param name the label shown to the user
 */
data class ServiceKiosk(
    val id: String,
    val name: String
)

/**
 * Provides access to the content of the supported streaming services.
 *
 * The interface is intentionally free of any extraction-engine type so that it can be implemented
 * on every platform and stubbed in tests. Implementations live in the platform source sets.
 */
interface ContentRepository {

    /**
     * Searches the given service and returns one page of results.
     */
    suspend fun search(request: SearchRequest): List<ContentItem>

    /**
     * The predefined content lists ("kiosks") the service offers, with the service default first.
     * An empty list means the service does not provide such lists.
     */
    fun kiosks(serviceId: Int): List<ServiceKiosk>

    /**
     * Loads the items of one kiosk, for example the trending page of the service.
     */
    suspend fun kiosk(serviceId: Int, kioskId: String): List<ContentItem>

    /**
     * Loads one page of a kiosk, so that the feed can grow while the user scrolls.
     *
     * @param pageIndex zero based page index
     * @return the items of the page, or an empty list once the kiosk has no further pages
     */
    suspend fun kioskPage(
        serviceId: Int,
        kioskId: String,
        pageIndex: Int
    ): List<ContentItem>

    /**
     * Loads the metadata and the stream list of a single stream.
     */
    suspend fun streamDetails(serviceId: Int, url: String): StreamDetails

    /**
     * Loads the metadata and the most recent uploads of a channel.
     */
    suspend fun channel(serviceId: Int, url: String): ChannelDetails

    /**
     * Loads the metadata and the entries of a playlist.
     */
    suspend fun playlist(serviceId: Int, url: String): PlaylistDetails

    /**
     * Resolves an arbitrary URL to the content it points at.
     */
    suspend fun resolveUrl(url: String): ResolvedContent

    /**
     * The content filters the given service supports for searching, in the order the service
     * declares them. An empty list means the service does not support filtering.
     */
    fun availableSearchFilters(serviceId: Int): List<String>
}

/**
 * Provides the list of services the extraction engine knows about.
 */
interface ServiceRegistry {

    /** All supported services, ordered by their service id. */
    fun services(): List<ServiceInfo>

    /** The service with the given id, or `null` when the id is unknown. */
    fun serviceById(id: Int): ServiceInfo?

    /** The service with the given name, or `null` when the name is unknown. */
    fun serviceByName(name: String): ServiceInfo?
}

/**
 * Errors raised while retrieving content.
 *
 * Every failure is mapped onto one of these so that the user interface can show a meaningful
 * message instead of a raw stack trace.
 */
sealed class ContentException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** The request could not be completed because of a network problem. */
    class Network(cause: Throwable? = null) :
        ContentException("Network error while contacting the service", cause)

    /** The service asked the client to slow down. */
    class RateLimited(cause: Throwable? = null) :
        ContentException("The service is rate limiting the requests", cause)

    /** The service requires a captcha to be solved before it serves more content. */
    class CaptchaRequired(cause: Throwable? = null) :
        ContentException("The service requires a captcha to be solved", cause)

    /** The requested content or feature is not supported. */
    class Unsupported(message: String, cause: Throwable? = null) :
        ContentException(message, cause)

    /** The extraction engine failed to interpret the service response. */
    class Extraction(message: String, cause: Throwable? = null) :
        ContentException(message, cause)
}

/**
 * Creates the content repository for the current platform.
 */
expect fun createContentRepository(): ContentRepository

/**
 * Creates the service registry for the current platform.
 */
expect fun createServiceRegistry(): ServiceRegistry
