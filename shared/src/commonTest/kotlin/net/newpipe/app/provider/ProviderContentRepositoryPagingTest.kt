/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class PagingStubProvider(
    override val id: String = "paging-stub",
    override val name: String = "Paging stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        search = true,
        trending = true,
        channels = true
    ),
    private val onSearch: suspend (query: String, pageToken: String?) -> ProviderResult<ProviderContentPage> =
        { _, _ -> ProviderResult.Success(ProviderContentPage(emptyList())) },
    private val onTrending: suspend (pageToken: String?) -> ProviderResult<ProviderContentPage> =
        { _ -> ProviderResult.Success(ProviderContentPage(emptyList())) },
    private val onChannel: suspend (url: String, pageToken: String?) -> ProviderResult<ProviderContentPage> =
        { _, _ -> ProviderResult.Success(ProviderContentPage(emptyList())) }
) : MediaProvider {

    var lastQuery: String? = null
        private set

    var lastUrl: String? = null
        private set

    var receivedPageToken: String? = null
        private set

    var searchCalled: Boolean = false
        private set

    var trendingCalled: Boolean = false
        private set

    var channelCalled: Boolean = false
        private set

    override suspend fun search(query: String, pageToken: String?): ProviderResult<ProviderContentPage> {
        lastQuery = query
        receivedPageToken = pageToken
        searchCalled = true
        return onSearch(query, pageToken)
    }

    override suspend fun trending(pageToken: String?): ProviderResult<ProviderContentPage> {
        receivedPageToken = pageToken
        trendingCalled = true
        return onTrending(pageToken)
    }

    override suspend fun channel(url: String, pageToken: String?): ProviderResult<ProviderContentPage> {
        lastUrl = url
        receivedPageToken = pageToken
        channelCalled = true
        return onChannel(url, pageToken)
    }
}

class ProviderContentRepositoryPagingTest {

    private fun repository(vararg providers: MediaProvider): ProviderContentRepository {
        val registry = ProviderRegistry()
        providers.forEach { registry.register(it) }
        return ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
    }

    @Test
    fun searchForwardsPageTokenAndReturnsNextToken() = runTest {
        val provider = PagingStubProvider(
            onSearch = { _, _ ->
                ProviderResult.Success(
                    ProviderContentPage(
                        items = listOf(
                            ProviderContentItem(id = "v1", title = "First", url = "https://page/1")
                        ),
                        nextPageToken = "TOKEN-2"
                    )
                )
            }
        )
        val repository = repository(provider)

        val result = repository.search("query", pageToken = "page-1")

        assertTrue(provider.searchCalled)
        assertEquals("query", provider.lastQuery)
        assertEquals("page-1", provider.receivedPageToken)
        assertEquals("TOKEN-2", (result as ProviderResult.Success<ProviderContentPage>).value.nextPageToken)
    }

    @Test
    fun searchWithNullPageTokenCallsProviderWithNull() = runTest {
        val provider = PagingStubProvider(
            onSearch = { _, _ -> ProviderResult.Success(ProviderContentPage(emptyList())) }
        )
        val repository = repository(provider)

        repository.search("query")

        assertTrue(provider.searchCalled)
        assertNull(provider.receivedPageToken)
    }

    @Test
    fun trendingForwardsPageTokenAndReturnsNextToken() = runTest {
        val provider = PagingStubProvider(
            onTrending = { _ ->
                ProviderResult.Success(
                    ProviderContentPage(
                        items = listOf(
                            ProviderContentItem(id = "v1", title = "First", url = "https://page/1")
                        ),
                        nextPageToken = "TOKEN-2"
                    )
                )
            }
        )
        val repository = repository(provider)

        val result = repository.trending(pageToken = "page-1")

        assertTrue(provider.trendingCalled)
        assertEquals("page-1", provider.receivedPageToken)
        assertEquals("TOKEN-2", (result as ProviderResult.Success<ProviderContentPage>).value.nextPageToken)
    }

    @Test
    fun trendingWithNullPageTokenCallsProviderWithNull() = runTest {
        val provider = PagingStubProvider(
            onTrending = { _ -> ProviderResult.Success(ProviderContentPage(emptyList())) }
        )
        val repository = repository(provider)

        repository.trending()

        assertTrue(provider.trendingCalled)
        assertNull(provider.receivedPageToken)
    }

    @Test
    fun channelForwardsPageTokenAndReturnsNextToken() = runTest {
        val provider = PagingStubProvider(
            onChannel = { _, _ ->
                ProviderResult.Success(
                    ProviderContentPage(
                        items = listOf(
                            ProviderContentItem(id = "v1", title = "First", url = "https://page/1")
                        ),
                        nextPageToken = "TOKEN-2"
                    )
                )
            }
        )
        val repository = repository(provider)

        val result = repository.channel("https://channel", pageToken = "page-1")

        assertTrue(provider.channelCalled)
        assertEquals("https://channel", provider.lastUrl)
        assertEquals("page-1", provider.receivedPageToken)
        assertEquals("TOKEN-2", (result as ProviderResult.Success<ProviderContentPage>).value.nextPageToken)
    }

    @Test
    fun channelWithNullPageTokenCallsProviderWithNull() = runTest {
        val provider = PagingStubProvider(
            onChannel = { _, _ -> ProviderResult.Success(ProviderContentPage(emptyList())) }
        )
        val repository = repository(provider)

        repository.channel("https://channel")

        assertTrue(provider.channelCalled)
        assertNull(provider.receivedPageToken)
    }
}
