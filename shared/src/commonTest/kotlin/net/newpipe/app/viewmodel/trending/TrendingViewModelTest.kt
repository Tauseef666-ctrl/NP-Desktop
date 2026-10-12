/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.trending

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.newpipe.app.provider.ChannelNavigation
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentKind
import net.newpipe.app.provider.ProviderContentPage
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderRegistry
import net.newpipe.app.provider.ProviderResult

private class FakeTrendingProvider(
    override val id: String = "fake",
    override val name: String = "Fake provider",
    override val isAvailable: Boolean = true,
    private val onTrending: suspend () -> ProviderResult<ProviderContentPage>
) : MediaProvider {
    override val capabilities: ProviderCapabilities = ProviderCapabilities(trending = true)

    override suspend fun trending(pageToken: String?): ProviderResult<ProviderContentPage> = onTrending()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrendingViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        registry: ProviderRegistry = ProviderRegistry(),
        channelNav: ChannelNavigation = ChannelNavigation()
    ) = TrendingViewModel(
        repository = ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined },
        channelNav = channelNav
    )

    private fun item(id: String) = ProviderContentItem(
        id = id,
        title = "Item $id",
        url = "https://example.org/$id"
    )

    private suspend fun TrendingViewModel.awaitSettled(): TrendingState =
        state.first { it is TrendingState.Success || it is TrendingState.Failure }

    @Test
    fun emptyRegistryPublishesNoProviderFailure() = runTest {
        val vm = viewModel(registry = ProviderRegistry())

        assertEquals(
            TrendingState.Failure(ProviderFailureReason.NO_PROVIDER),
            vm.awaitSettled()
        )
    }

    @Test
    fun successfulTrendingPublishesItems() = runTest {
        val page = ProviderContentPage(listOf(item("v1"), item("v2")))
        val registry = ProviderRegistry().apply {
            register(FakeTrendingProvider { ProviderResult.Success(page) })
        }
        val vm = viewModel(registry = registry)

        assertEquals(TrendingState.Success(page.items), vm.awaitSettled())
    }

    @Test
    fun providerFailurePublishesReason() = runTest {
        val registry = ProviderRegistry().apply {
            register(FakeTrendingProvider { ProviderResult.Failure(ProviderFailureReason.NETWORK) })
        }
        val vm = viewModel(registry = registry)

        assertEquals(
            TrendingState.Failure(ProviderFailureReason.NETWORK),
            vm.awaitSettled()
        )
    }

    @Test
    fun retryReQueriesAfterFailure() = runTest {
        val page = ProviderContentPage(listOf(item("v1")))
        var calls = 0
        val registry = ProviderRegistry().apply {
            register(
                FakeTrendingProvider {
                    calls++
                    if (calls == 1) {
                        ProviderResult.Failure(ProviderFailureReason.NETWORK)
                    } else {
                        ProviderResult.Success(page)
                    }
                }
            )
        }
        val vm = viewModel(registry = registry)

        assertEquals(
            TrendingState.Failure(ProviderFailureReason.NETWORK),
            vm.awaitSettled()
        )

        vm.retry()

        assertEquals(TrendingState.Success(page.items), vm.awaitSettled())
        assertEquals(2, calls)
    }

    @Test
    fun selectChannelPrefersUploaderUrlForStreams() {
        val vm = viewModel()
        val item = item("v1").copy(
            uploader = "Some Channel",
            uploaderUrl = "https://example.org/@somechannel"
        )

        assertTrue(vm.selectChannel(item))
    }

    @Test
    fun selectChannelUsesContentUrlForChannels() {
        val channelNav = ChannelNavigation()
        val vm = viewModel(channelNav = channelNav)
        val channel = ProviderContentItem(
            id = "c1",
            title = "Some Channel",
            url = "https://example.org/@somechannel",
            kind = ProviderContentKind.CHANNEL
        )

        assertTrue(vm.selectChannel(channel))
        assertEquals(
            ChannelNavigation.Selection("https://example.org/@somechannel", "Some Channel"),
            channelNav.selected.value
        )
    }

    @Test
    fun selectChannelReturnsFalseWhenNoChannelIsKnown() {
        val vm = viewModel()
        // A stream without an uploader URL has no channel to open.
        assertFalse(vm.selectChannel(item("v1")))
    }
}
