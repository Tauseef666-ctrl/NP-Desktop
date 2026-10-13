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

private class ChannelTabsStubProvider(
    override val id: String = "channel-tabs-stub",
    override val name: String = "Channel tabs stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        channels = true
    ),
    private val onChannelTabs: suspend (url: String) -> ProviderResult<List<ProviderChannelTab>> =
        { _ -> ProviderResult.Success(emptyList()) },
    private val onChannelTab: suspend (url: String, tabId: String, pageToken: String?) -> ProviderResult<ProviderContentPage> =
        { _, _, _ -> ProviderResult.Success(ProviderContentPage(emptyList())) }
) : MediaProvider {

    var lastTabsUrl: String? = null
        private set

    var lastTabUrl: String? = null
        private set

    var lastTabId: String? = null
        private set

    var receivedPageToken: String? = null
        private set

    var channelTabsCalled: Boolean = false
        private set

    var channelTabCalled: Boolean = false
        private set

    override suspend fun channelTabs(url: String): ProviderResult<List<ProviderChannelTab>> {
        lastTabsUrl = url
        channelTabsCalled = true
        return onChannelTabs(url)
    }

    override suspend fun channelTab(
        url: String,
        tabId: String,
        pageToken: String?
    ): ProviderResult<ProviderContentPage> {
        lastTabUrl = url
        lastTabId = tabId
        receivedPageToken = pageToken
        channelTabCalled = true
        return onChannelTab(url, tabId, pageToken)
    }
}

private class ChannelTabsUnsupportedStubProvider(
    override val id: String = "channel-tabs-unsupported-stub",
    override val name: String = "Channel tabs unsupported stub provider",
    override val isAvailable: Boolean = true,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        channels = true
    )
) : MediaProvider

class ProviderContentRepositoryChannelTabsTest {

    private fun repository(vararg providers: MediaProvider): ProviderContentRepository {
        val registry = ProviderRegistry()
        providers.forEach { registry.register(it) }
        return ProviderContentRepository(registry).apply { dispatcher = Dispatchers.Unconfined }
    }

    @Test
    fun channelTabsForwardsUrlAndReturnsTabs() = runTest {
        val tabs = listOf(
            ProviderChannelTab("videos", "Videos"),
            ProviderChannelTab("shorts", "Shorts")
        )
        val provider = ChannelTabsStubProvider(
            onChannelTabs = { _ -> ProviderResult.Success(tabs) }
        )
        val repository = repository(provider)

        val result = repository.channelTabs("https://channel")

        assertTrue(provider.channelTabsCalled)
        assertEquals("https://channel", provider.lastTabsUrl)
        assertEquals(tabs, (result as ProviderResult.Success<List<ProviderChannelTab>>).value)
    }

    @Test
    fun channelTabForwardsUrlTabIdAndPageToken() = runTest {
        val provider = ChannelTabsStubProvider(
            onChannelTab = { _, _, _ ->
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

        val result = repository.channelTab("https://channel", "shorts", pageToken = "page-2")

        assertTrue(provider.channelTabCalled)
        assertEquals("https://channel", provider.lastTabUrl)
        assertEquals("shorts", provider.lastTabId)
        assertEquals("page-2", provider.receivedPageToken)
        assertEquals("TOKEN-2", (result as ProviderResult.Success<ProviderContentPage>).value.nextPageToken)
    }

    @Test
    fun channelTabWithNullPageToken() = runTest {
        val provider = ChannelTabsStubProvider(
            onChannelTab = { _, _, _ -> ProviderResult.Success(ProviderContentPage(emptyList())) }
        )
        val repository = repository(provider)

        repository.channelTab("https://channel", "videos")

        assertTrue(provider.channelTabCalled)
        assertEquals("https://channel", provider.lastTabUrl)
        assertEquals("videos", provider.lastTabId)
        assertNull(provider.receivedPageToken)
    }

    @Test
    fun channelTabsWithoutProviderFailsNoProvider() = runTest {
        val repository = repository()

        val result = repository.channelTabs("https://channel")

        assertEquals(
            ProviderFailureReason.NO_PROVIDER,
            (result as ProviderResult.Failure).reason
        )
    }

    @Test
    fun channelTabWithoutProviderFailsNoProvider() = runTest {
        val repository = repository()

        val result = repository.channelTab("https://channel", "videos")

        assertEquals(
            ProviderFailureReason.NO_PROVIDER,
            (result as ProviderResult.Failure).reason
        )
    }

    @Test
    fun channelTabsUnsupportedByProviderFailsUnsupported() = runTest {
        val provider = ChannelTabsUnsupportedStubProvider()
        val repository = repository(provider)

        val result = repository.channelTabs("https://channel")

        assertEquals(
            ProviderFailureReason.UNSUPPORTED,
            (result as ProviderResult.Failure).reason
        )
    }
}
