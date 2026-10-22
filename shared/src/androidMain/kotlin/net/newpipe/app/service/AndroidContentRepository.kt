/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import net.newpipe.app.model.ServiceInfo

/**
 * An Android [ServiceRegistry] that does not talk to the extraction engine.
 *
 * The extraction engine is a plain JVM library. Sharing the implementation with the Android target
 * would require pulling the whole HTTP stack and the Rhino scripting engine into the Android
 * build, which is not done yet. The Android application keeps using the legacy extraction code in
 * the `app` module until the shared implementation is moved to a common JVM source set.
 *
 * See `docs/ARCHITECTURE.md` section 4.1.
 */
class AndroidServiceRegistry : ServiceRegistry {

    override fun services(): List<ServiceInfo> = emptyList()

    override fun serviceById(id: Int): ServiceInfo? = null

    override fun serviceByName(name: String): ServiceInfo? = null
}

/**
 * A [ContentRepository] that always reports the feature as unsupported.
 *
 * @see AndroidServiceRegistry
 */
class AndroidContentRepository : ContentRepository {

    override suspend fun search(request: SearchRequest): List<net.newpipe.app.model.ContentItem> =
        throw unsupported()

    override suspend fun streamDetails(serviceId: Int, url: String): net.newpipe.app.model.StreamDetails =
        throw unsupported()

    override suspend fun channel(serviceId: Int, url: String): net.newpipe.app.model.ChannelDetails =
        throw unsupported()

    override suspend fun playlist(serviceId: Int, url: String): net.newpipe.app.model.PlaylistDetails =
        throw unsupported()

    override suspend fun resolveUrl(url: String): net.newpipe.app.model.ResolvedContent =
        throw unsupported()

    override fun availableSearchFilters(serviceId: Int): List<String> = emptyList()

    private fun unsupported(): ContentException.Unsupported = ContentException.Unsupported(
        "Content extraction is not available on Android yet, use the legacy implementation"
    )
}

actual fun createServiceRegistry(): ServiceRegistry = AndroidServiceRegistry()

actual fun createContentRepository(): ContentRepository = AndroidContentRepository()
