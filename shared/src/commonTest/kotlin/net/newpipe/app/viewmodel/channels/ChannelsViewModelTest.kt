/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.channels

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
}
