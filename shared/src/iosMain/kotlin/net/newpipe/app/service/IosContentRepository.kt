/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ServiceInfo
import net.newpipe.app.model.StreamDetails
import net.newpipe.app.model.ChannelDetails
import net.newpipe.app.model.PlaylistDetails
import net.newpipe.app.model.ResolvedContent

/**
 * An iOS [ServiceRegistry] stub.
 *
 * The extraction engine is written in Java and therefore cannot be used from the iOS targets of
 * this module. The iOS application of the upstream project ships its own extraction implementation;
 * the desktop port does not need one either.
 *
 * See `docs/ARCHITECTURE.md` section 4.1.
 */
class IosServiceRegistry : ServiceRegistry {

    override fun services(): List<ServiceInfo> = emptyList()

    override fun serviceById(id: Int): ServiceInfo? = null

    override fun serviceByName(name: String): ServiceInfo? = null
}

/**
 * An iOS [ContentRepository] stub that always reports the feature as unsupported.
 */
class IosContentRepository : ContentRepository {

    override suspend fun search(request: SearchRequest): List<ContentItem> = throw unsupported()

    override fun kiosks(serviceId: Int): List<ServiceKiosk> = emptyList()

    override suspend fun kiosk(serviceId: Int, kioskId: String): List<ContentItem> = throw unsupported()

    override suspend fun streamDetails(serviceId: Int, url: String): StreamDetails = throw unsupported()

    override suspend fun channel(serviceId: Int, url: String): ChannelDetails = throw unsupported()

    override suspend fun playlist(serviceId: Int, url: String): PlaylistDetails = throw unsupported()

    override suspend fun resolveUrl(url: String): ResolvedContent = throw unsupported()

    override fun availableSearchFilters(serviceId: Int): List<String> = emptyList()

    private fun unsupported(): ContentException.Unsupported = ContentException.Unsupported(
        "Content extraction is not available on iOS"
    )
}

actual fun createServiceRegistry(): ServiceRegistry = IosServiceRegistry()

actual fun createContentRepository(): ContentRepository = IosContentRepository()
