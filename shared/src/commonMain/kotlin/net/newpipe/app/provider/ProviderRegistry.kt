/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Singleton

@Singleton
class ProviderRegistry {
    private val _providers = MutableStateFlow<List<MediaProvider>>(emptyList())
    val providers: StateFlow<List<MediaProvider>> = _providers.asStateFlow()

    val available: StateFlow<List<MediaProvider>>
        get() = MutableStateFlow(availableOf(_providers.value)).asStateFlow()

    val hasAvailable: Boolean
        get() = availableOf(_providers.value).isNotEmpty()

    fun register(provider: MediaProvider) {
        _providers.value = pureRegister(_providers.value, provider)
    }

    fun unregister(id: String) {
        _providers.value = _providers.value.filterNot { it.id == id }
    }

    fun byId(id: String): MediaProvider? {
        return _providers.value.find { it.id == id }
    }

    companion object {
        fun pureRegister(existing: List<MediaProvider>, provider: MediaProvider): List<MediaProvider> {
            val result = existing.filterNot { it.id == provider.id }.toMutableList()
            result.add(provider)
            return result.toList()
        }

        fun availableOf(providers: List<MediaProvider>): List<MediaProvider> {
            return providers.filter { it.isAvailable }
        }
    }
}