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
import net.newpipe.app.provider.MediaPlayback
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentPage
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.ProviderRegistry
import net.newpipe.app.provider.ProviderResult
import net.newpipe.app.player.MediaItem
import net.newpipe.app.search.SearchHistoryStore

private class FakeSearchPlayback(
    private val playResult: ProviderResult<Unit> = ProviderResult.Success(Unit),
    private val resolveResult: ProviderResult<MediaItem> =
        ProviderResult.Failure(ProviderFailureReason.NO_PROVIDER)
) : MediaPlayback {
    var playedItems: MutableList<ProviderContentItem> = mutableListOf()

    override suspend fun resolve(item: ProviderContentItem): ProviderResult<MediaItem> = resolveResult

    override suspend fun play(item: ProviderContentItem): ProviderResult<Unit> {
        playedItems.add(item)
        return playResult
    }
}

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

private class PagingSearchProvider(
    private val onSearch: suspend (String?) -> ProviderResult<ProviderContentPage>
) : MediaProvider {
    override val id: String = "fake"
    override val name: String = "Fake provider"
    override val isAvailable: Boolean = true
    override val capabilities: ProviderCapabilities = ProviderCapabilities(search = true)

    val seenTokens: MutableList<String?> = mutableListOf()

    override suspend fun search(
        query: String,
        pageToken: String?
    ): ProviderResult<ProviderContentPage> {
        seenTokens.add(pageToken)
        return onSearch(pageToken)
    }
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
        registry: ProviderRegistry = ProviderRegistry(),
        playback: MediaPlayback = FakeSearchPlayback()
    ) = SearchViewModel(
        history = SearchHistoryStore(settings, Json { ignoreUnknownKeys = true }),
        repository = ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined },
        mediaPlayback = playback,
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

    @Test
    fun loadMoreAppendsNextPage() = runTest {
        val page1 = ProviderContentPage(listOf(item("v1")), nextPageToken = "T1")
        val page2 = ProviderContentPage(listOf(item("v2")), nextPageToken = "T2")
        val provider = PagingSearchProvider { token ->
            if (token == null) ProviderResult.Success(page1) else ProviderResult.Success(page2)
        }
        val vm = viewModel(registry = ProviderRegistry().apply { register(provider) })

        vm.search("kotlin")
        vm.awaitSettled()
        vm.loadMore()

        val success = vm.results.first { it is SearchResultsState.Success && it.items.size == 2 }
            as SearchResultsState.Success
        assertEquals(listOf(item("v1"), item("v2")), success.items)
        assertEquals("T2", success.nextPageToken)
        assertEquals(listOf<String?>(null, "T1"), provider.seenTokens)
    }

    @Test
    fun loadMoreWithoutTokenIsNoOp() = runTest {
        val page1 = ProviderContentPage(listOf(item("v1")))
        val provider = PagingSearchProvider { ProviderResult.Success(page1) }
        val vm = viewModel(registry = ProviderRegistry().apply { register(provider) })

        vm.search("kotlin")
        vm.awaitSettled()
        vm.loadMore()

        assertEquals(listOf<String?>(null), provider.seenTokens)
        assertEquals(
            listOf(item("v1")),
            (vm.results.value as SearchResultsState.Success).items
        )
    }

    @Test
    fun loadMoreFailureKeepsItemsAndClearsLoading() = runTest {
        val page1 = ProviderContentPage(listOf(item("v1")), nextPageToken = "T1")
        val provider = PagingSearchProvider { token ->
            if (token == null) {
                ProviderResult.Success(page1)
            } else {
                ProviderResult.Failure(ProviderFailureReason.NETWORK)
            }
        }
        val vm = viewModel(registry = ProviderRegistry().apply { register(provider) })

        vm.search("kotlin")
        vm.awaitSettled()
        vm.loadMore()

        val success = vm.results.value as SearchResultsState.Success
        assertEquals(listOf(item("v1")), success.items)
        assertEquals("T1", success.nextPageToken)
        assertEquals(false, success.loadingMore)
    }

    @Test
    fun loadMoreIgnoredWhileAlreadyLoading() = runTest {
        val page1 = ProviderContentPage(listOf(item("v1")), nextPageToken = "T1")
        val page2 = ProviderContentPage(listOf(item("v2")), nextPageToken = "T2")
        val gate = CompletableDeferred<ProviderResult<ProviderContentPage>>()
        val provider = PagingSearchProvider { token ->
            if (token == null) ProviderResult.Success(page1) else gate.await()
        }
        val vm = viewModel(registry = ProviderRegistry().apply { register(provider) })

        vm.search("kotlin")
        vm.awaitSettled()
        vm.loadMore()
        vm.loadMore()

        assertEquals(listOf<String?>(null, "T1"), provider.seenTokens)

        gate.complete(ProviderResult.Success(page2))
        val success = vm.results.first { it is SearchResultsState.Success && it.items.size == 2 }
            as SearchResultsState.Success
        assertEquals(listOf(item("v1"), item("v2")), success.items)
    }

    @Test
    fun playOnSuccessPublishesLaunched() = runTest {
        val vm = viewModel(playback = FakeSearchPlayback(playResult = ProviderResult.Success(Unit)))

        vm.play(item("v1"))

        assertEquals(ProviderPlaybackState.Launched, vm.playbackState.value)
    }

    @Test
    fun playOnFailurePublishesFailureWithReason() = runTest {
        val vm = viewModel(
            playback = FakeSearchPlayback(
                playResult = ProviderResult.Failure(ProviderFailureReason.NETWORK)
            )
        )

        vm.play(item("v1"))

        assertEquals(
            ProviderPlaybackState.Failure(ProviderFailureReason.NETWORK),
            vm.playbackState.value
        )
    }

    @Test
    fun acknowledgePlaybackResetsToIdle() = runTest {
        val vm = viewModel(playback = FakeSearchPlayback(playResult = ProviderResult.Success(Unit)))
        vm.play(item("v1"))
        assertEquals(ProviderPlaybackState.Launched, vm.playbackState.value)

        vm.acknowledgePlayback()

        assertEquals(ProviderPlaybackState.Idle, vm.playbackState.value)
    }

    @Test
    fun playPassesItemToMediaPlayback() = runTest {
        val playback = FakeSearchPlayback(playResult = ProviderResult.Success(Unit))
        val vm = viewModel(playback = playback)
        val target = item("v1")

        vm.play(target)

        assertEquals(listOf(target), playback.playedItems)
    }
}
