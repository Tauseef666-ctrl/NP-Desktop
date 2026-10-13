/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.channels

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.newpipe.app.player.MediaItem
import net.newpipe.app.provider.ChannelNavigation
import net.newpipe.app.provider.MediaPlayback
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderChannelTab
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentKind
import net.newpipe.app.provider.ProviderContentPage
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.provider.ProviderRegistry
import net.newpipe.app.provider.ProviderResult

private val CHANNEL_URL = "https://example.com/channel/NewPipe"

private val VIDEO = ProviderContentItem(
    id = "v1",
    title = "First video",
    url = "https://example.com/watch?v=1",
    uploader = "NewPipe",
    durationSeconds = 125L,
    kind = ProviderContentKind.VIDEO
)

private val LIVE = ProviderContentItem(
    id = "l1",
    title = "Live now",
    url = "https://example.com/watch?v=2",
    uploader = "NewPipe",
    kind = ProviderContentKind.LIVE
)

private val SUCCESS_PAGE = ProviderResult.Success(ProviderContentPage(listOf(VIDEO, LIVE)))

/**
 * Media provider stand-in that answers channel requests with a fixed [result] and counts the calls
 * so tests can assert that retry really re-queries.
 */
private class FakeChannelProvider(
    private val result: ProviderResult<ProviderContentPage>,
    override val isAvailable: Boolean = true
) : MediaProvider {
    override val id: String = "fake"
    override val name: String = "Fake"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(channels = true)

    var channelCalls: Int = 0
        private set

    override suspend fun channel(url: String, pageToken: String?): ProviderResult<ProviderContentPage> {
        channelCalls++
        return result
    }
}

private class PagingChannelProvider(
    private val onChannel: suspend (String, String?) -> ProviderResult<ProviderContentPage>
) : MediaProvider {
    override val id: String = "fake"
    override val name: String = "Fake"
    override val isAvailable: Boolean = true
    override val capabilities: ProviderCapabilities = ProviderCapabilities(channels = true)

    val seenTokens: MutableList<String?> = mutableListOf()

    override suspend fun channel(url: String, pageToken: String?): ProviderResult<ProviderContentPage> {
        seenTokens.add(pageToken)
        return onChannel(url, pageToken)
    }
}

/**
 * Media provider stand-in that exposes channel tabs and serves per-tab content. Records every
 * [channelTab] call (tab id to page token) and counts [channel] calls so tests can assert which
 * path the view model took.
 */
private class TabsChannelProvider(
    private val tabs: List<ProviderChannelTab>,
    private val onTab: suspend (url: String, tabId: String, pageToken: String?) -> ProviderResult<ProviderContentPage>,
    private val onChannel: suspend (url: String, pageToken: String?) -> ProviderResult<ProviderContentPage> =
        { _, _ -> ProviderResult.Success(ProviderContentPage(emptyList())) }
) : MediaProvider {
    override val id: String = "fake-tabs"
    override val name: String = "Fake tabs"
    override val isAvailable: Boolean = true
    override val capabilities: ProviderCapabilities = ProviderCapabilities(channels = true)

    val tabCalls: MutableList<Pair<String, String?>> = mutableListOf()

    var channelCalls: Int = 0
        private set

    override suspend fun channelTabs(url: String): ProviderResult<List<ProviderChannelTab>> =
        ProviderResult.Success(tabs)

    override suspend fun channelTab(
        url: String,
        tabId: String,
        pageToken: String?
    ): ProviderResult<ProviderContentPage> {
        tabCalls.add(tabId to pageToken)
        return onTab(url, tabId, pageToken)
    }

    override suspend fun channel(url: String, pageToken: String?): ProviderResult<ProviderContentPage> {
        channelCalls++
        return onChannel(url, pageToken)
    }
}

/**
 * Playback stand-in that answers with a fixed [result] and records the last item it was asked to
 * play, so tests can assert the view model forwarded the tapped item.
 */
private class FakeChannelsPlayback(
    private val result: ProviderResult<Unit> = ProviderResult.Success(Unit)
) : MediaPlayback {
    var lastItem: ProviderContentItem? = null
        private set

    override suspend fun resolve(item: ProviderContentItem): ProviderResult<MediaItem> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED)

    override suspend fun play(item: ProviderContentItem): ProviderResult<Unit> {
        lastItem = item
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(
        provider: MediaProvider? = null
    ): ProviderContentRepository {
        val registry = ProviderRegistry()
        if (provider != null) registry.register(provider)
        return ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
    }

    private fun viewModel(
        repository: ProviderContentRepository,
        channelNav: ChannelNavigation,
        mediaPlayback: MediaPlayback = FakeChannelsPlayback()
    ) = ChannelsViewModel(repository, channelNav, mediaPlayback)

    @Test
    fun noSelectionStaysIdle() {
        val channelNav = ChannelNavigation()
        val viewModel = viewModel(repository(FakeChannelProvider(SUCCESS_PAGE)), channelNav)

        assertTrue(viewModel.state.value is ChannelsState.Idle)
        assertNull(viewModel.selection.value)
    }

    @Test
    fun selectionBeforeInitLoadsSuccess() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val provider = FakeChannelProvider(SUCCESS_PAGE)
        val viewModel = viewModel(repository(provider), channelNav)

        val state = viewModel.state.first { it !is ChannelsState.Loading }

        assertEquals(
            ChannelsState.Success(listOf(VIDEO, LIVE)),
            state
        )
        assertEquals(1, provider.channelCalls)
    }

    @Test
    fun selectionReflectsNavigation() {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val viewModel = viewModel(repository(FakeChannelProvider(SUCCESS_PAGE)), channelNav)

        assertEquals(ChannelNavigation.Selection(CHANNEL_URL, "NewPipe"), viewModel.selection.value)
    }

    @Test
    fun selectionAfterInitPickedUpByLoad() = runTest {
        val channelNav = ChannelNavigation()
        val provider = FakeChannelProvider(SUCCESS_PAGE)
        val viewModel = viewModel(repository(provider), channelNav)
        assertTrue(viewModel.state.value is ChannelsState.Idle)

        channelNav.select(CHANNEL_URL, "NewPipe")
        viewModel.load()

        val state = viewModel.state.first { it !is ChannelsState.Loading }
        assertTrue(state is ChannelsState.Success)
        assertEquals(1, provider.channelCalls)
    }

    @Test
    fun providerFailurePropagatesReason() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val failure = ProviderResult.Failure(ProviderFailureReason.PARSE, "bad payload")
        val viewModel = viewModel(repository(FakeChannelProvider(failure)), channelNav)

        val state = viewModel.state.first { it !is ChannelsState.Loading }

        assertEquals(ChannelsState.Failure(ProviderFailureReason.PARSE), state)
    }

    @Test
    fun emptyRegistryFailsNoProvider() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val viewModel = viewModel(repository(provider = null), channelNav)

        val state = viewModel.state.first { it !is ChannelsState.Loading }

        assertEquals(ChannelsState.Failure(ProviderFailureReason.NO_PROVIDER), state)
    }

    @Test
    fun retryReQueries() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val provider = FakeChannelProvider(SUCCESS_PAGE)
        val viewModel = viewModel(repository(provider), channelNav)
        viewModel.state.first { it !is ChannelsState.Loading }
        assertEquals(1, provider.channelCalls)

        viewModel.retry()

        val state = viewModel.state.first { it !is ChannelsState.Loading }
        assertTrue(state is ChannelsState.Success)
        assertEquals(2, provider.channelCalls)
    }

    @Test
    fun loadMoreAppendsNextPage() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val page1 = ProviderContentPage(listOf(VIDEO), nextPageToken = "T1")
        val page2 = ProviderContentPage(listOf(LIVE), nextPageToken = "T2")
        val provider = PagingChannelProvider { _, token ->
            if (token == null) ProviderResult.Success(page1) else ProviderResult.Success(page2)
        }
        val vm = viewModel(repository(provider), channelNav)
        vm.state.first { it !is ChannelsState.Loading }

        vm.loadMore()

        val success = vm.state.first { it is ChannelsState.Success && it.items.size == 2 }
            as ChannelsState.Success
        assertEquals(listOf(VIDEO, LIVE), success.items)
        assertEquals("T2", success.nextPageToken)
        assertEquals(listOf<String?>(null, "T1"), provider.seenTokens)
    }

    @Test
    fun loadMoreWithoutTokenIsNoOp() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val page1 = ProviderContentPage(listOf(VIDEO))
        val provider = PagingChannelProvider { _, _ -> ProviderResult.Success(page1) }
        val vm = viewModel(repository(provider), channelNav)
        vm.state.first { it !is ChannelsState.Loading }

        vm.loadMore()

        assertEquals(listOf<String?>(null), provider.seenTokens)
        assertEquals(listOf(VIDEO), (vm.state.value as ChannelsState.Success).items)
    }

    @Test
    fun loadMoreFailureKeepsItemsAndClearsLoading() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val page1 = ProviderContentPage(listOf(VIDEO), nextPageToken = "T1")
        val provider = PagingChannelProvider { _, token ->
            if (token == null) {
                ProviderResult.Success(page1)
            } else {
                ProviderResult.Failure(ProviderFailureReason.NETWORK)
            }
        }
        val vm = viewModel(repository(provider), channelNav)
        vm.state.first { it !is ChannelsState.Loading }

        vm.loadMore()

        val success = vm.state.value as ChannelsState.Success
        assertEquals(listOf(VIDEO), success.items)
        assertEquals("T1", success.nextPageToken)
        assertEquals(false, success.loadingMore)
    }

    @Test
    fun loadMoreIgnoredWhileAlreadyLoading() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val page1 = ProviderContentPage(listOf(VIDEO), nextPageToken = "T1")
        val page2 = ProviderContentPage(listOf(LIVE), nextPageToken = "T2")
        val gate = CompletableDeferred<ProviderResult<ProviderContentPage>>()
        val provider = PagingChannelProvider { _, token ->
            if (token == null) ProviderResult.Success(page1) else gate.await()
        }
        val vm = viewModel(repository(provider), channelNav)
        vm.state.first { it !is ChannelsState.Loading }

        vm.loadMore()
        vm.loadMore()

        assertEquals(listOf<String?>(null, "T1"), provider.seenTokens)

        gate.complete(ProviderResult.Success(page2))
        val success = vm.state.first { it is ChannelsState.Success && it.items.size == 2 }
            as ChannelsState.Success
        assertEquals(listOf(VIDEO, LIVE), success.items)
    }

    @Test
    fun playOnSuccessPublishesLaunched() {
        val vm = viewModel(repository(FakeChannelProvider(SUCCESS_PAGE)), ChannelNavigation())

        vm.play(VIDEO)

        assertEquals(ProviderPlaybackState.Launched, vm.playbackState.value)
    }

    @Test
    fun playOnFailurePublishesFailureWithReason() {
        val vm = viewModel(
            repository(FakeChannelProvider(SUCCESS_PAGE)),
            ChannelNavigation(),
            FakeChannelsPlayback(ProviderResult.Failure(ProviderFailureReason.NETWORK))
        )

        vm.play(VIDEO)

        assertEquals(
            ProviderPlaybackState.Failure(ProviderFailureReason.NETWORK),
            vm.playbackState.value
        )
    }

    @Test
    fun acknowledgePlaybackResetsToIdle() {
        val vm = viewModel(repository(FakeChannelProvider(SUCCESS_PAGE)), ChannelNavigation())
        vm.play(VIDEO)
        assertEquals(ProviderPlaybackState.Launched, vm.playbackState.value)

        vm.acknowledgePlayback()

        assertEquals(ProviderPlaybackState.Idle, vm.playbackState.value)
    }

    @Test
    fun playPassesItemToMediaPlayback() {
        val playback = FakeChannelsPlayback()
        val vm = viewModel(repository(FakeChannelProvider(SUCCESS_PAGE)), ChannelNavigation(), playback)

        vm.play(VIDEO)

        assertEquals(VIDEO, playback.lastItem)
    }

    private fun tabbedProvider(
        onTab: suspend (url: String, tabId: String, pageToken: String?) -> ProviderResult<ProviderContentPage>
    ): TabsChannelProvider = TabsChannelProvider(
        tabs = listOf(
            ProviderChannelTab("videos", "Videos"),
            ProviderChannelTab("shorts", "Shorts")
        ),
        onTab = onTab
    )

    @Test
    fun tabsAndDefaultSelectionLoaded() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val provider = tabbedProvider { _, tabId, _ ->
            if (tabId == "videos") {
                ProviderResult.Success(ProviderContentPage(listOf(VIDEO)))
            } else {
                ProviderResult.Success(ProviderContentPage(listOf(LIVE)))
            }
        }
        val vm = viewModel(repository(provider), channelNav)

        val state = vm.state.first { it !is ChannelsState.Loading }

        assertEquals(
            listOf(ProviderChannelTab("videos", "Videos"), ProviderChannelTab("shorts", "Shorts")),
            vm.tabs.value
        )
        assertEquals("videos", vm.selectedTabId.value)
        assertEquals(ChannelsState.Success(listOf(VIDEO)), state)
        assertEquals(listOf<Pair<String, String?>>("videos" to null), provider.tabCalls)
        assertEquals(0, provider.channelCalls)
    }

    @Test
    fun selectTabLoadsThatTab() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val provider = tabbedProvider { _, tabId, _ ->
            if (tabId == "videos") {
                ProviderResult.Success(ProviderContentPage(listOf(VIDEO)))
            } else {
                ProviderResult.Success(ProviderContentPage(listOf(LIVE)))
            }
        }
        val vm = viewModel(repository(provider), channelNav)
        vm.state.first { it !is ChannelsState.Loading }

        vm.selectTab("shorts")

        val state = vm.state.first { it !is ChannelsState.Loading }
        assertEquals("shorts", vm.selectedTabId.value)
        assertEquals(ChannelsState.Success(listOf(LIVE)), state)
        assertTrue(provider.tabCalls.contains("shorts" to null))
    }

    @Test
    fun selectSameTabIsNoOp() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val provider = tabbedProvider { _, _, _ ->
            ProviderResult.Success(ProviderContentPage(listOf(VIDEO)))
        }
        val vm = viewModel(repository(provider), channelNav)
        vm.state.first { it !is ChannelsState.Loading }
        assertEquals(listOf<Pair<String, String?>>("videos" to null), provider.tabCalls)

        vm.selectTab("videos")

        assertEquals(listOf<Pair<String, String?>>("videos" to null), provider.tabCalls)
    }

    @Test
    fun loadMoreUsesSelectedTab() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val shortsPage1 = ProviderContentPage(listOf(LIVE), nextPageToken = "T1")
        val shortsPage2 = ProviderContentPage(listOf(VIDEO), nextPageToken = "T2")
        val provider = tabbedProvider { _, tabId, token ->
            when {
                tabId != "shorts" -> ProviderResult.Success(ProviderContentPage(listOf(VIDEO)))
                token == null -> ProviderResult.Success(shortsPage1)
                else -> ProviderResult.Success(shortsPage2)
            }
        }
        val vm = viewModel(repository(provider), channelNav)
        vm.state.first { it !is ChannelsState.Loading }

        vm.selectTab("shorts")
        vm.state.first { it is ChannelsState.Success && it.nextPageToken == "T1" }

        vm.loadMore()

        val success = vm.state.first { it is ChannelsState.Success && it.items.size == 2 }
            as ChannelsState.Success
        assertEquals(listOf(LIVE, VIDEO), success.items)
        assertEquals("shorts" to "T1", provider.tabCalls.last())
    }

    @Test
    fun tabsUnsupportedFallsBackToChannel() = runTest {
        val channelNav = ChannelNavigation()
        channelNav.select(CHANNEL_URL, "NewPipe")
        val provider = FakeChannelProvider(SUCCESS_PAGE)
        val vm = viewModel(repository(provider), channelNav)

        val state = vm.state.first { it !is ChannelsState.Loading }

        assertTrue(vm.tabs.value.isEmpty())
        assertNull(vm.selectedTabId.value)
        assertEquals(1, provider.channelCalls)
        assertEquals(ChannelsState.Success(listOf(VIDEO, LIVE)), state)
    }
}
