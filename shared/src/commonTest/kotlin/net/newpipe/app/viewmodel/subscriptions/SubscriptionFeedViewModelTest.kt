/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.subscriptions

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.newpipe.app.player.MediaItem
import net.newpipe.app.provider.MediaPlayback
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentPage
import net.newpipe.app.provider.ProviderContentRepository
import net.newpipe.app.provider.ProviderFailureReason
import net.newpipe.app.provider.ProviderRegistry
import net.newpipe.app.provider.ProviderResult
import net.newpipe.app.subscription.Subscription
import net.newpipe.app.subscription.SubscriptionStore
import net.newpipe.app.viewmodel.subscriptions.SubscriptionFeedViewModel.FeedState

private class FeedStubProvider(
    override val id: String = "feed-stub",
    override val name: String = "Feed stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(channels = true),
    private val onChannel: suspend (String) -> ProviderResult<ProviderContentPage> = {
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED)
    }
) : MediaProvider {
    override suspend fun channel(url: String, pageToken: String?): ProviderResult<ProviderContentPage> =
        onChannel(url)
}

private class NoopMediaPlayback : MediaPlayback {
    override suspend fun resolve(item: ProviderContentItem): ProviderResult<MediaItem> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED)

    override suspend fun play(item: ProviderContentItem): ProviderResult<Unit> =
        ProviderResult.Failure(ProviderFailureReason.UNSUPPORTED)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionFeedViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun store(vararg subscriptions: Subscription): SubscriptionStore {
        val store = SubscriptionStore(MapSettings(), Json { ignoreUnknownKeys = true })
        subscriptions.forEach { store.add(it) }
        return store
    }

    private fun repository(provider: MediaProvider): ProviderContentRepository {
        val registry = ProviderRegistry()
        registry.register(provider)
        return ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
    }

    private fun page(vararg ids: String) = ProviderContentPage(
        items = ids.map { ProviderContentItem(id = it, title = it, url = "https://v/$it") }
    )

    private fun sub(name: String) = Subscription(
        id = "https://youtube.com/@$name",
        name = name,
        url = "https://youtube.com/@$name"
    )

    @Test
    fun emptySubscriptionsYieldEmptyContent() {
        val viewModel = SubscriptionFeedViewModel(
            store = store(),
            repository = repository(FeedStubProvider()),
            mediaPlayback = NoopMediaPlayback()
        )

        viewModel.load()

        val state = viewModel.state.value
        assertTrue(state is FeedState.Content)
        assertTrue(state.feed.isEmpty)
    }

    @Test
    fun successfulChannelsAreGrouped() {
        val provider = FeedStubProvider(onChannel = { url ->
            if (url.endsWith("@A")) ProviderResult.Success(page("a1", "a2")) else
                ProviderResult.Success(page("b1"))
        })
        val viewModel = SubscriptionFeedViewModel(
            store = store(sub("A"), sub("B")),
            repository = repository(provider),
            mediaPlayback = NoopMediaPlayback()
        )

        viewModel.load()

        val state = viewModel.state.value as FeedState.Content
        assertEquals(setOf("A", "B"), state.feed.groups.map { it.channelName }.toSet())
        assertEquals(3, state.feed.itemCount)
    }

    @Test
    fun allChannelsFailingYieldsUnavailable() {
        val provider = FeedStubProvider(
            onChannel = { ProviderResult.Failure(ProviderFailureReason.NETWORK) }
        )
        val viewModel = SubscriptionFeedViewModel(
            store = store(sub("A"), sub("B")),
            repository = repository(provider),
            mediaPlayback = NoopMediaPlayback()
        )

        viewModel.load()

        assertEquals(FeedState.Unavailable(ProviderFailureReason.NETWORK), viewModel.state.value)
    }

    @Test
    fun partialFailureStillRendersAndReportsFailedChannels() {
        val provider = FeedStubProvider(onChannel = { url ->
            if (url.endsWith("@A")) {
                ProviderResult.Success(page("a1"))
            } else {
                ProviderResult.Failure(ProviderFailureReason.NETWORK)
            }
        })
        val viewModel = SubscriptionFeedViewModel(
            store = store(sub("A"), sub("B")),
            repository = repository(provider),
            mediaPlayback = NoopMediaPlayback()
        )

        viewModel.load()

        val state = viewModel.state.value as FeedState.Content
        assertEquals(listOf("A"), state.feed.groups.map { it.channelName })
        assertEquals(listOf("B"), state.feed.failedChannels)
    }

    @Test
    fun resetReturnsToIdle() {
        val viewModel = SubscriptionFeedViewModel(
            store = store(),
            repository = repository(FeedStubProvider()),
            mediaPlayback = NoopMediaPlayback()
        )
        viewModel.load()

        viewModel.reset()

        assertEquals(FeedState.Idle, viewModel.state.value)
    }
}
