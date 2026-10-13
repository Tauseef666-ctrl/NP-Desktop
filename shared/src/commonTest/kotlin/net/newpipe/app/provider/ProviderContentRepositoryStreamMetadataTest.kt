/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class StreamMetadataStubProvider(
    override val id: String = "stream-metadata-stub",
    override val name: String = "Stream metadata stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        resolve = true
    ),
    private val onStreamMetadata: suspend (url: String) -> ProviderResult<StreamMetadata> =
        { _ -> ProviderResult.Success(StreamMetadata(title = "t")) }
) : MediaProvider {

    var lastUrl: String? = null
        private set

    var streamMetadataCalled: Boolean = false
        private set

    override suspend fun streamMetadata(url: String): ProviderResult<StreamMetadata> {
        lastUrl = url
        streamMetadataCalled = true
        return onStreamMetadata(url)
    }
}

private class StreamMetadataUnsupportedStubProvider(
    override val id: String = "stream-metadata-unsupported-stub",
    override val name: String = "Stream metadata unsupported stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        resolve = true
    )
) : MediaProvider

class ProviderContentRepositoryStreamMetadataTest {

    private fun repository(vararg providers: MediaProvider): ProviderContentRepository {
        val registry = ProviderRegistry()
        providers.forEach { registry.register(it) }
        return ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
    }

    @Test
    fun streamMetadataForwardsUrlAndReturnsMetadata() = runTest {
        val metadata = StreamMetadata(
            title = "Hello",
            uploaderName = "Someone",
            viewCount = 42L
        )
        val provider = StreamMetadataStubProvider(
            onStreamMetadata = { _ -> ProviderResult.Success(metadata) }
        )
        val repository = repository(provider)

        val result = repository.streamMetadata("https://video")

        assertTrue(provider.streamMetadataCalled)
        assertEquals("https://video", provider.lastUrl)
        assertEquals(metadata, (result as ProviderResult.Success<StreamMetadata>).value)
    }

    @Test
    fun streamMetadataWithoutProviderFailsNoProvider() = runTest {
        val repository = repository()

        val result = repository.streamMetadata("https://video")

        assertEquals(
            ProviderFailureReason.NO_PROVIDER,
            (result as ProviderResult.Failure).reason
        )
    }

    @Test
    fun streamMetadataUnsupportedByProviderFailsUnsupported() = runTest {
        val provider = StreamMetadataUnsupportedStubProvider()
        val repository = repository(provider)

        val result = repository.streamMetadata("https://video")

        assertEquals(
            ProviderFailureReason.UNSUPPORTED,
            (result as ProviderResult.Failure).reason
        )
    }
}
