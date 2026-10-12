/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private class ResolveStubProvider(
    override val id: String = "resolve-stub",
    override val name: String = "Resolve stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(resolve = true),
    private val onResolve: suspend (String) -> ProviderResult<ResolvedMedia> = {
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED)
    }
) : MediaProvider {
    override suspend fun resolve(url: String): ProviderResult<ResolvedMedia> = onResolve(url)
}

class ProviderContentRepositoryResolveTest {

    private fun repository(vararg providers: MediaProvider): ProviderContentRepository {
        val registry = ProviderRegistry()
        providers.forEach { registry.register(it) }
        return ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
    }

    @Test
    fun resolveWithNoProviderFailsWithNoProvider() = runBlocking {
        val repository = repository()

        assertEquals(
            ProviderResult.Failure(ProviderFailureReason.NO_PROVIDER),
            repository.resolve("https://page")
        )
    }

    @Test
    fun resolveWithoutResolveCapabilityFailsUnsupported() = runBlocking {
        val repository = repository(
            ResolveStubProvider(
                capabilities = ProviderCapabilities(search = true),
                onResolve = { ProviderResult.Success(ResolvedMedia(streamUrl = "https://s")) }
            )
        )

        assertEquals(
            ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED),
            repository.resolve("https://page")
        )
    }

    @Test
    fun resolveReturnsSuccessFromProvider() = runBlocking {
        val repository = repository(
            ResolveStubProvider(
                onResolve = { ProviderResult.Success(ResolvedMedia(streamUrl = "https://s")) }
            )
        )

        assertEquals(
            ProviderResult.Success(ResolvedMedia(streamUrl = "https://s")),
            repository.resolve("https://page")
        )
    }

    @Test
    fun resolveMapsThrownExceptionToUnknown() = runBlocking {
        val repository = repository(
            ResolveStubProvider(onResolve = { error("boom") })
        )

        val result = repository.resolve("https://page")

        assertEquals(ProviderFailureReason.UNKNOWN, (result as ProviderResult.Failure).reason)
    }
}
