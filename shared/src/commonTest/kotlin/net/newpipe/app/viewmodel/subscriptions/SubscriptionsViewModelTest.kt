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
import net.newpipe.app.subscription.SubscriptionStore
import net.newpipe.app.subscription.subscriptionIdFor
import net.newpipe.app.viewmodel.subscriptions.SubscriptionsViewModel.AddResult

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(settings: MapSettings = MapSettings()) =
        SubscriptionsViewModel(SubscriptionStore(settings, Json { ignoreUnknownKeys = true }))

    @Test
    fun validAddReturnsAddedAndAppears() {
        val viewModel = viewModel()

        val result = viewModel.add("NewPipe", "https://youtube.com/@Foo")

        assertEquals(AddResult.Added, result)
        assertEquals(1, viewModel.subscriptions.value.size)
        assertEquals("NewPipe", viewModel.subscriptions.value.first().name)
        assertEquals("https://youtube.com/@Foo", viewModel.subscriptions.value.first().url)
    }

    @Test
    fun blankNameFallsBackToNormalizedUrl() {
        val viewModel = viewModel()

        viewModel.add("   ", "https://youtube.com/@Foo")

        assertEquals("https://youtube.com/@Foo", viewModel.subscriptions.value.first().name)
    }

    @Test
    fun invalidUrlReturnsInvalidUrlAndAddsNothing() {
        val viewModel = viewModel()

        assertEquals(AddResult.InvalidUrl, viewModel.add("x", "not a url"))
        assertEquals(AddResult.InvalidUrl, viewModel.add("x", "ftp://youtube.com/@Foo"))
        assertTrue(viewModel.subscriptions.value.isEmpty())
    }

    @Test
    fun sameNormalizedUrlTwiceReturnsDuplicate() {
        val viewModel = viewModel()
        assertEquals(AddResult.Added, viewModel.add("A", "https://youtube.com/@Foo"))

        val second = viewModel.add("B", "  HTTPS://YouTube.com/@Foo/  ")

        assertEquals(AddResult.Duplicate, second)
        assertEquals(1, viewModel.subscriptions.value.size)
    }

    @Test
    fun addUsesNormalizedUrlAsId() {
        val viewModel = viewModel()

        viewModel.add("A", "HTTPS://YouTube.com/@Foo/")

        assertEquals(
            subscriptionIdFor("https://youtube.com/@Foo"),
            viewModel.subscriptions.value.first().id
        )
    }

    @Test
    fun removeDropsSubscription() {
        val viewModel = viewModel()
        viewModel.add("A", "https://youtube.com/@Foo")
        val id = viewModel.subscriptions.value.first().id

        viewModel.remove(id)

        assertTrue(viewModel.subscriptions.value.isEmpty())
    }
}
