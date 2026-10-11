/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeProvider(
    override val id: String,
    override val name: String,
    override val isAvailable: Boolean = false,
    override val capabilities: ProviderCapabilities = ProviderCapabilities()
) : MediaProvider

class ProviderRegistryTest {
    @Test
    fun registerAddsProvider() {
        val registry = ProviderRegistry()
        val provider = FakeProvider("yt", "YouTube", isAvailable = false)
        registry.register(provider)
        assertEquals(1, registry.providers.value.size)
        assertEquals(provider, registry.byId("yt"))
    }

    @Test
    fun unregisterRemovesProvider() {
        val registry = ProviderRegistry()
        registry.register(FakeProvider("yt", "YouTube"))
        registry.unregister("yt")
        assertTrue(registry.providers.value.isEmpty())
        assertNull(registry.byId("yt"))
    }

    @Test
    fun dedupeByIdReplacesExisting() {
        val registry = ProviderRegistry()
        registry.register(FakeProvider("yt", "Old", isAvailable = false))
        registry.register(FakeProvider("yt", "New", isAvailable = true))
        assertEquals(1, registry.providers.value.size)
        assertEquals("New", registry.providers.value[0].name)
        assertTrue(registry.providers.value[0].isAvailable)
    }

    @Test
    fun byIdReturnsNullWhenNotFound() {
        val registry = ProviderRegistry()
        assertNull(registry.byId("missing"))
    }

    @Test
    fun availableOfFiltersOnlyAvailable() {
        val providers = listOf(
            FakeProvider("a", "A", isAvailable = true),
            FakeProvider("b", "B", isAvailable = false),
            FakeProvider("c", "C", isAvailable = true)
        )
        val available = ProviderRegistry.availableOf(providers)
        assertEquals(2, available.size)
        assertEquals(listOf("a", "c"), available.map { it.id })
    }

    @Test
    fun pureRegisterDedupeByIdReplacesExisting() {
        val existing = listOf(FakeProvider("yt", "Old", isAvailable = false))
        val result = ProviderRegistry.pureRegister(existing, FakeProvider("yt", "New", isAvailable = true))
        assertEquals(1, result.size)
        assertEquals("New", result[0].name)
        assertTrue(result[0].isAvailable)
    }
}