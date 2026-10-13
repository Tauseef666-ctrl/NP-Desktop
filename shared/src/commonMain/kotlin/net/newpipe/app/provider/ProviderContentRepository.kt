/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Singleton

/**
 * Single entry point the UI uses to fetch provider content. It picks, per operation, the first
 * available provider whose [ProviderCapabilities] advertises that operation, so screens never reach
 * into [ProviderRegistry] directly.
 *
 * Every method returns a [ProviderResult]; when there is no provider at all it fails with
 * [ProviderFailureReason.NO_PROVIDER], when a provider exists but cannot do the operation it fails
 * with [ProviderFailureReason.UNSUPPORTED]. Provider work runs on [dispatcher] ([Dispatchers.Default]
 * in production) so slow blocking extraction never runs on the caller's (main) dispatcher.
 */
@Singleton
class ProviderContentRepository(
    private val registry: ProviderRegistry
) {

    /**
     * Dispatcher for the blocking provider work. Defaults to [Dispatchers.Default]; tests can swap it
     * for an unconfined dispatcher so every hop completes inside the test that triggered it.
     */
    internal var dispatcher: CoroutineDispatcher = Dispatchers.Default

    /** True when at least one provider can perform any content operation. */
    val hasAnyProvider: Boolean
        get() = registry.available.value.isNotEmpty()

    /** True when at least one provider can search. */
    val canSearch: Boolean
        get() = firstFor { it.capabilities.search } != null

    suspend fun search(query: String, pageToken: String? = null): ProviderResult<ProviderContentPage> {
        val provider = firstFor { it.capabilities.search }
            ?: return noProviderOrUnsupported { it.capabilities.search }
        return run(provider) { it.search(query, pageToken) }
    }

    suspend fun trending(pageToken: String? = null): ProviderResult<ProviderContentPage> {
        val provider = firstFor { it.capabilities.trending }
            ?: return noProviderOrUnsupported { it.capabilities.trending }
        return run(provider) { it.trending(pageToken) }
    }

    suspend fun channel(url: String, pageToken: String? = null): ProviderResult<ProviderContentPage> {
        val provider = firstFor { it.capabilities.channels }
            ?: return noProviderOrUnsupported { it.capabilities.channels }
        return run(provider) { it.channel(url, pageToken) }
    }

    /**
     * List the selectable content tabs of the channel at [url]. Picks the first available provider
     * whose [ProviderCapabilities.channels] is enabled; fails the same way [channel] does when none
     * can list channels.
     */
    suspend fun channelTabs(url: String): ProviderResult<List<ProviderChannelTab>> {
        val provider = firstFor { it.capabilities.channels }
            ?: return noProviderOrUnsupported { it.capabilities.channels }
        return run(provider) { it.channelTabs(url) }
    }

    /**
     * Load one content tab ([tabId] as returned by [channelTabs]) of the channel at [url]. Picks the
     * first available provider whose [ProviderCapabilities.channels] is enabled.
     */
    suspend fun channelTab(
        url: String,
        tabId: String,
        pageToken: String? = null
    ): ProviderResult<ProviderContentPage> {
        val provider = firstFor { it.capabilities.channels }
            ?: return noProviderOrUnsupported { it.capabilities.channels }
        return run(provider) { it.channelTab(url, tabId, pageToken) }
    }

    /**
     * Load the contents of the playlist at [url]. Picks the first available provider whose
     * [ProviderCapabilities.playlists] is enabled; fails with [ProviderFailureReason.NO_PROVIDER]
     * when none exist or [ProviderFailureReason.UNSUPPORTED] when the providers present cannot list
     * playlists.
     */
    suspend fun playlist(url: String, pageToken: String? = null): ProviderResult<ProviderContentPage> {
        val provider = firstFor { it.capabilities.playlists }
            ?: return noProviderOrUnsupported { it.capabilities.playlists }
        return run(provider) { it.playlist(url, pageToken) }
    }

    /**
     * Resolve the page at [url] to a playable stream. Fails with [ProviderFailureReason.NO_PROVIDER]
     * when no provider can resolve, or [ProviderFailureReason.UNSUPPORTED] when the providers present
     * cannot.
     */
    suspend fun resolve(url: String): ProviderResult<ResolvedMedia> {
        val provider = firstFor { it.capabilities.resolve }
            ?: return noProviderOrUnsupported { it.capabilities.resolve }
        return run(provider) { it.resolve(url) }
    }

    /**
     * Fetch descriptive [StreamMetadata] for the stream page at [url]. Picks the first available
     * provider whose [ProviderCapabilities.resolve] is enabled (metadata is read from the same stream
     * page as playback), and fails the same way [resolve] does when none can.
     */
    suspend fun streamMetadata(url: String): ProviderResult<StreamMetadata> {
        val provider = firstFor { it.capabilities.resolve }
            ?: return noProviderOrUnsupported { it.capabilities.resolve }
        return run(provider) { it.streamMetadata(url) }
    }

    private fun firstFor(predicate: (MediaProvider) -> Boolean): MediaProvider? =
        registry.available.value.firstOrNull { it.isAvailable && predicate(it) }

    private fun noProviderOrUnsupported(predicate: (MediaProvider) -> Boolean): ProviderResult.Failure {
        val any = registry.available.value
        return if (any.isEmpty()) {
            ProviderResult.Failure(ProviderFailureReason.NO_PROVIDER)
        } else if (any.none(predicate)) {
            ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED)
        } else {
            ProviderResult.Failure(ProviderFailureReason.NO_PROVIDER)
        }
    }

    private suspend fun <T> run(
        provider: MediaProvider,
        block: suspend (MediaProvider) -> ProviderResult<T>
    ): ProviderResult<T> = withContext(dispatcher) {
        try {
            block(provider)
        } catch (t: Throwable) {
            // Providers are expected to map their own failures; this is only a last-resort guard so a
            // misbehaving provider cannot crash the caller.
            ProviderResult.Failure(ProviderFailureReason.UNKNOWN, t.message)
        }
    }
}
