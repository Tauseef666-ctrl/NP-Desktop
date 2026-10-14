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

private class FilterStubProvider(
    override val id: String = "filter-stub",
    override val name: String = "Filter stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(search = true),
    private val onSearchFiltered: suspend (String, SearchFilter, String?) -> ProviderResult<ProviderContentPage> =
        { _, _, _ -> ProviderResult.Success(ProviderContentPage(emptyList())) }
) : MediaProvider {

    var lastQuery: String? = null
        private set

    var lastFilter: SearchFilter? = null
        private set

    var lastPageToken: String? = null
        private set

    var searchFilteredCalls: Int = 0
        private set

    override suspend fun searchFiltered(
        query: String,
        filter: SearchFilter,
        pageToken: String?
    ): ProviderResult<ProviderContentPage> {
        lastQuery = query
        lastFilter = filter
        lastPageToken = pageToken
        searchFilteredCalls++
        return onSearchFiltered(query, filter, pageToken)
    }
}

private class SearchOnlyStubProvider(
    override val id: String = "search-only-stub",
    override val name: String = "Search-only stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(search = true)
) : MediaProvider {

    var lastQuery: String? = null
        private set

    var searchCalls: Int = 0
        private set

    override suspend fun search(query: String, pageToken: String?): ProviderResult<ProviderContentPage> {
        lastQuery = query
        searchCalls++
        return ProviderResult.Success(ProviderContentPage(emptyList()))
    }
}

class ProviderContentRepositorySearchFilterTest {

    private fun repository(vararg providers: MediaProvider): ProviderContentRepository {
        val registry = ProviderRegistry()
        providers.forEach { registry.register(it) }
        return ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
    }

    @Test
    fun searchFilteredForwardsQueryFilterAndToken() = runTest {
        val page = ProviderContentPage(
            items = listOf(ProviderContentItem(id = "v1", title = "First", url = "https://page/1")),
            nextPageToken = "TOKEN-2"
        )
        val provider = FilterStubProvider(onSearchFiltered = { _, _, _ -> ProviderResult.Success(page) })
        val repository = repository(provider)

        val result = repository.searchFiltered("cats", SearchFilter.VIDEOS, "token-1")

        assertEquals("cats", provider.lastQuery)
        assertEquals(SearchFilter.VIDEOS, provider.lastFilter)
        assertEquals("token-1", provider.lastPageToken)
        assertEquals(page, (result as ProviderResult.Success<ProviderContentPage>).value)
    }

    @Test
    fun searchFilteredWithoutProviderFailsNoProvider() = runTest {
        val repository = repository()

        val result = repository.searchFiltered("cats", SearchFilter.CHANNELS)

        assertEquals(
            ProviderFailureReason.NO_PROVIDER,
            (result as ProviderResult.Failure).reason
        )
    }

    @Test
    fun searchFilteredUnsupportedFailsUnsupported() = runTest {
        val provider = object : MediaProvider {
            override val id: String = "no-search"
            override val name: String = "No search"
            override val isAvailable: Boolean = true
            override val capabilities: ProviderCapabilities = ProviderCapabilities(search = false)
        }
        val repository = repository(provider)

        val result = repository.searchFiltered("cats", SearchFilter.PLAYLISTS)

        assertEquals(
            ProviderFailureReason.UNSUPPORTED,
            (result as ProviderResult.Failure).reason
        )
    }

    @Test
    fun searchFilteredFallsBackToSearchWhenNotOverridden() = runTest {
        val provider = SearchOnlyStubProvider()
        val repository = repository(provider)

        repository.searchFiltered("dogs", SearchFilter.CHANNELS)

        assertTrue(provider.searchCalls == 1)
        assertEquals("dogs", provider.lastQuery)
    }
}
