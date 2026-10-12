/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.search

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.newpipe.app.provider.ChannelNavigation
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentPage
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderRegistry
import net.newpipe.app.provider.ProviderResult
import net.newpipe.app.search.SearchHistoryStore

private class FakeSearchProvider(
    override val id: String = "fake",
    override val name: String = "Fake provider",
    override val isAvailable: Boolean = true,
    private val onSearch: suspend (String) -> ProviderResult<ProviderContentPage>
) : MediaProvider {
    override val capabilities: ProviderCapabilities = ProviderCapabilities(search = true)

    override suspend fun search(
        query: String,
        pageToken: String?
    ): ProviderResult<ProviderContentPage> = onSearch(query)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        settings: MapSettings = MapSettings(),
        registry: ProviderRegistry = ProviderRegistry()
    ) = SearchViewModel(
        history = SearchHistoryStore(settings, Json { ignoreUnknownKeys = true }),
        repository = ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined },
        channelNavigation = ChannelNavigation()
    )

    private fun item(id: String) = ProviderContentItem(
        id = id,
        title = "Item $id",
        url = "https://example.org/$id"
    )

    private suspend fun SearchViewModel.awaitSettled(): SearchResultsState =
        results.first { it is SearchResultsState.Success || it is SearchResultsState.Failure }

    @Test
    fun recordUpdatesHistoryEntries() {
        val vm = viewModel()
        vm.record("alpha")
        vm.record("beta")
        assertEquals(listOf("beta", "alpha"), vm.historyEntries.value)
    }

    @Test
    fun suggestionsReflectRecordedHistory() {
        val vm = viewModel()
        vm.record("Kotlin Multiplatform")
        vm.record("Jetpack")
        assertEquals(listOf("Kotlin Multiplatform"), vm.suggestions("kotlin"))
    }

    @Test
    fun clearHistoryEmptiesEntries() {
        val vm = viewModel()
        vm.record("alpha")
        vm.clearHistory()
        assertTrue(vm.historyEntries.value.isEmpty())
    }

    @Test
    fun suggestionsExcludeExactMatch() {
        val vm = viewModel()
        vm.record("alpha")
        assertTrue(vm.suggestions("alpha").isEmpty())
    }

    @Test
    fun blankQueryLeavesResultsIdle() = runTest {
        val vm = viewModel()
        vm.search("   ")
        assertEquals(SearchResultsState.Idle, vm.results.value)
    }

    @Test
    fun clearResultsReturnsToIdle() = runTest {
        val page = ProviderContentPage(listOf(item("v1")))
        val registry = ProviderRegistry().apply {
            register(FakeSearchProvider { ProviderResult.Success(page) })
        }
        val vm = viewModel(registry = registry)

        vm.search("kotlin")
        vm.awaitSettled()
        vm.clearResults()

        assertEquals(SearchResultsState.Idle, vm.results.value)
    }

    @Test
    fun successfulSearchLoadsThenPublishesItems() = runTest {
        val page = ProviderContentPage(listOf(item("v1"), item("v2")))
        val gate = CompletableDeferred<ProviderResult<ProviderContentPage>>()
        val registry = ProviderRegistry().apply {
            register(FakeSearchProvider { gate.await() })
        }
        val vm = viewModel(registry = registry)

        vm.search("kotlin")
        assertEquals(SearchResultsState.Loading, vm.results.value)

        gate.complete(ProviderResult.Success(page))

        assertEquals(SearchResultsState.Success(page.items), vm.awaitSettled())
    }

    @Test
    fun providerFailurePublishesReason() = runTest {
        val registry = ProviderRegistry().apply {
            register(FakeSearchProvider { ProviderResult.Failure(ProviderFailureReason.NETWORK) })
        }
        val vm = viewModel(registry = registry)

        vm.search("offline")

        assertEquals(
            SearchResultsState.Failure(ProviderFailureReason.NETWORK),
            vm.awaitSettled()
        )
    }

    @Test
    fun emptyRegistryPublishesNoProviderFailure() = runTest {
        val vm = viewModel(registry = ProviderRegistry())

        vm.search("anything")

        assertEquals(
            SearchResultsState.Failure(ProviderFailureReason.NO_PROVIDER),
            vm.awaitSettled()
        )
    }

    @Test
    fun searchRecordsTrimmedNonBlankQuery() = runTest {
        val registry = ProviderRegistry().apply {
            register(FakeSearchProvider { ProviderResult.Success(ProviderContentPage(emptyList())) })
        }
        val vm = viewModel(registry = registry)

        vm.search("  kotlin  ")

        assertEquals(listOf("kotlin"), vm.historyEntries.value)
    }

    @Test
    fun newerSearchWinsOverSlowEarlierSearch() = runTest {
        val slowGate = CompletableDeferred<ProviderResult<ProviderContentPage>>()
        val newerPage = ProviderContentPage(listOf(item("newer")))
        val registry = ProviderRegistry().apply {
            register(
                FakeSearchProvider { query ->
                    if (query == "slow") slowGate.await() else ProviderResult.Success(newerPage)
                }
            )
        }
        val vm = viewModel(registry = registry)

        vm.search("slow")
        vm.search("newer")
        assertEquals(SearchResultsState.Success(newerPage.items), vm.awaitSettled())

        slowGate.complete(ProviderResult.Success(ProviderContentPage(listOf(item("stale")))))
        assertEquals(SearchResultsState.Success(newerPage.items), vm.results.value)
    }
}
