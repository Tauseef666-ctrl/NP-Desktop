/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderRegistry
import net.newpipe.app.search.SearchHistoryStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSearchProvider(
    override val id: String,
    override val name: String,
    override val isAvailable: Boolean = false,
    override val capabilities: ProviderCapabilities = ProviderCapabilities()
) : MediaProvider

class SearchServicesSettingsViewModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun store(settings: MapSettings = MapSettings()) = SearchHistoryStore(settings, json)

    @Test
    fun historyCountStartsAtZero() {
        val vm = SearchServicesSettingsViewModel(store(), ProviderRegistry())
        assertEquals(0, vm.searchHistoryCount.value)
    }

    @Test
    fun historyCountReflectsRecordedSearches() {
        val history = store()
        val vm = SearchServicesSettingsViewModel(history, ProviderRegistry())

        history.record("alpha")
        assertEquals(1, vm.searchHistoryCount.value)

        history.record("beta")
        assertEquals(2, vm.searchHistoryCount.value)
    }

    @Test
    fun clearSearchHistoryEmptiesStoreAndRemovesKey() {
        val settings = MapSettings()
        val history = SearchHistoryStore(settings, json)
        history.record("alpha")
        val vm = SearchServicesSettingsViewModel(history, ProviderRegistry())
        assertEquals(1, vm.searchHistoryCount.value)

        vm.clearSearchHistory()

        assertTrue(history.entries.value.isEmpty())
        assertEquals(0, vm.searchHistoryCount.value)
        assertNull(settings.getStringOrNull(SearchHistoryStore.KEY))
    }

    @Test
    fun clearSearchHistoryWhenEmptyIsNoOp() {
        val settings = MapSettings()
        val vm = SearchServicesSettingsViewModel(SearchHistoryStore(settings, json), ProviderRegistry())

        vm.clearSearchHistory()

        assertEquals(0, vm.searchHistoryCount.value)
        assertNull(settings.getStringOrNull(SearchHistoryStore.KEY))
    }

    @Test
    fun emptyRegistryYieldsNoProviders() {
        val vm = SearchServicesSettingsViewModel(store(), ProviderRegistry())

        assertTrue(vm.providers.value.isEmpty())
        assertFalse(vm.hasProviders.value)
    }

    @Test
    fun registeredProvidersAreReportedWithAvailability() {
        val registry = ProviderRegistry()
        registry.register(FakeSearchProvider("yt", "YouTube", isAvailable = false))
        registry.register(FakeSearchProvider("pt", "PeerTube", isAvailable = true))

        val vm = SearchServicesSettingsViewModel(store(), registry)

        assertTrue(vm.hasProviders.value)
        assertEquals(
            listOf(
                ProviderOverview("yt", "YouTube", available = false),
                ProviderOverview("pt", "PeerTube", available = true)
            ),
            vm.providers.value
        )
    }

    @Test
    fun registryChangesPropagateLive() {
        val registry = ProviderRegistry()
        val vm = SearchServicesSettingsViewModel(store(), registry)
        assertFalse(vm.hasProviders.value)

        registry.register(FakeSearchProvider("yt", "YouTube", isAvailable = true))

        assertEquals(1, vm.providers.value.size)
        assertTrue(vm.hasProviders.value)
        assertEquals(ProviderOverview("yt", "YouTube", available = true), vm.providers.value[0])
    }

    @Test
    fun refreshReReadsRegistry() = runTest {
        val registry = ProviderRegistry()
        val vm = SearchServicesSettingsViewModel(store(), registry)
        assertTrue(vm.providers.value.isEmpty())

        registry.register(FakeSearchProvider("pt", "PeerTube", isAvailable = true))
        vm.refresh()

        assertEquals(listOf(ProviderOverview("pt", "PeerTube", available = true)), vm.providers.value)
    }

    @Test
    fun pureOverviewsMapsFields() {
        val overviews = SearchServicesSettingsViewModel.pureOverviews(
            listOf(FakeSearchProvider("a", "Alpha", isAvailable = true))
        )
        assertEquals(listOf(ProviderOverview("a", "Alpha", available = true)), overviews)
    }

    @Test
    fun pureOverviewsOfEmptyIsEmpty() {
        assertTrue(SearchServicesSettingsViewModel.pureOverviews(emptyList()).isEmpty())
    }
}
