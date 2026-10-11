/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeProvider(
    override val id: String,
    override val name: String,
    override val isAvailable: Boolean = false,
    override val capabilities: ProviderCapabilities = ProviderCapabilities()
) : MediaProvider

class ContentSettingsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyRegistry_hasProvidersFalse() {
        val registry = ProviderRegistry()
        val vm = ContentSettingsViewModel(registry)
        assertFalse(vm.hasProviders.value)
        assertTrue(vm.providers.value.isEmpty())
    }

    @Test
    fun register_emitsProviders() {
        val registry = ProviderRegistry()
        val vm = ContentSettingsViewModel(registry)
        registry.register(FakeProvider("yt", "YouTube", isAvailable = false))
        assertTrue(vm.hasProviders.value)
        assertEquals(1, vm.providers.value.size)
        assertEquals("YouTube", vm.providers.value[0].name)
        assertFalse(vm.providers.value[0].isAvailable)
    }

    @Test
    fun unavailableProviderReportedHonestly() {
        val registry = ProviderRegistry()
        val vm = ContentSettingsViewModel(registry)
        registry.register(FakeProvider("peertube", "PeerTube", isAvailable = false))
        assertEquals("peertube", vm.providers.value[0].id)
        assertEquals("PeerTube", vm.providers.value[0].name)
        assertFalse(vm.providers.value[0].isAvailable)
    }
}