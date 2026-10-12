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
    val playlists: Boolean = false
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
}
