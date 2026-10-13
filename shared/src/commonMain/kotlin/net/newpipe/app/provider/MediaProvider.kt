/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

/**
 * Provider capabilities for [MediaProvider]. Each flag names an operation the provider can really
 * perform; [ProviderContentRepository] uses these to pick a provider per operation.
 */
data class ProviderCapabilities(
    val search: Boolean = false,
    val trending: Boolean = false,
    val subscriptions: Boolean = false,
    val channels: Boolean = false,
    val playlists: Boolean = false,
    val resolve: Boolean = false
)

/**
 * Foundation for F10. The JVM build bundles a NewPipeExtractor-backed implementation
 * (`ExtractorMediaProvider`, registered into [ProviderRegistry]); other targets have no provider yet.
 * Content operations are suspend functions with honest default implementations that report
 * [ProviderFailureReason.UNSUPPORTED]. A concrete provider overrides exactly the operations named by
 * its [capabilities]; anything it does not support keeps the default failure instead of pretending.
 */
interface MediaProvider {
    val id: String
    val name: String
    val isAvailable: Boolean
    val capabilities: ProviderCapabilities

    /** Search [query] for content. [pageToken] continues a previous page when the provider supplies one. */
    suspend fun search(query: String, pageToken: String? = null): ProviderResult<ProviderContentPage> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED, "search")

    /** Load the provider's trending/ranked content. */
    suspend fun trending(pageToken: String? = null): ProviderResult<ProviderContentPage> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED, "trending")

    /** Load the contents of the channel at [url]. */
    suspend fun channel(url: String, pageToken: String? = null): ProviderResult<ProviderContentPage> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED, "channel")

    /**
     * List the selectable content tabs of the channel at [url] (for example Videos/Shorts/Live/
     * Playlists). Providers without tab support keep this default failure instead of pretending.
     */
    suspend fun channelTabs(url: String): ProviderResult<List<ProviderChannelTab>> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED, "channelTabs")

    /**
     * Load the contents of one channel tab ([tabId] as returned by [channelTabs]) of the channel at
     * [url]. [pageToken] continues a previous page when the provider supplied one.
     */
    suspend fun channelTab(
        url: String,
        tabId: String,
        pageToken: String? = null
    ): ProviderResult<ProviderContentPage> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED, "channelTab")

    /**
     * Load the contents of the playlist at [url]. [pageToken] continues a previous page when the
     * provider supplied a [ProviderContentPage.nextPageToken].
     */
    suspend fun playlist(url: String, pageToken: String? = null): ProviderResult<ProviderContentPage> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED, "playlist")

    /**
     * Resolve the page at [url] to a directly playable [ResolvedMedia] stream. Providers that can
     * search/trend/list but not resolve playback keep this default failure instead of pretending.
     */
    suspend fun resolve(url: String): ProviderResult<ResolvedMedia> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED, "resolve")
}
