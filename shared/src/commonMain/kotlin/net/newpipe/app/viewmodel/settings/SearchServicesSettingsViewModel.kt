/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderRegistry
import net.newpipe.app.search.SearchHistoryStore
import org.koin.core.annotation.KoinViewModel

/** Honest, read-only snapshot of one streaming provider shown on the Search & services screen. */
data class ProviderOverview(
    val id: String,
    val name: String,
    val available: Boolean
)

/**
 * Drives the Search & services settings screen. It exposes the live size of the stored search
 * history (clearing really forgets it through [SearchHistoryStore]) and the currently connected
 * streaming providers read from the real [ProviderRegistry].
 *
 * Provider derivation is a manual collect into a [MutableStateFlow] (rather than a plain `stateIn`)
 * so [refresh] can re-read the registry on demand, while registry mutations still propagate live.
 */
@KoinViewModel
class SearchServicesSettingsViewModel(
    private val searchHistory: SearchHistoryStore,
    private val registry: ProviderRegistry
) : ViewModel() {

    // Dedicated scope (not viewModelScope) so mapping the live stores works on any platform
    // dispatcher and does not require a Main dispatcher just to construct the ViewModel.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** Live size of the stored search history. */
    val searchHistoryCount: StateFlow<Int> = searchHistory.entries
        .map { it.size }
        .stateIn(scope, SharingStarted.Eagerly, searchHistory.entries.value.size)

    private val _providers = MutableStateFlow(pureOverviews(registry.providers.value))

    /** Live, honest list of the connected providers with their availability. */
    val providers: StateFlow<List<ProviderOverview>> = _providers.asStateFlow()

    /** Whether the registry currently holds any provider. */
    val hasProviders: StateFlow<Boolean> = providers
        .map { it.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, _providers.value.isNotEmpty())

    init {
        scope.launch {
            registry.providers.collect { _providers.value = pureOverviews(it) }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    /** Forget the stored search history. A no-op when already empty. */
    fun clearSearchHistory() {
        if (searchHistory.entries.value.isEmpty()) return
        searchHistory.clear()
    }

    /** Re-read the provider list from the registry (which may change while the screen is open). */
    fun refresh() {
        _providers.value = pureOverviews(registry.providers.value)
    }

    companion object {
        /** Pure mapping from registry providers to the display model; extracted for unit testing. */
        fun pureOverviews(providers: List<MediaProvider>): List<ProviderOverview> =
            providers.map { provider ->
                ProviderOverview(
                    id = provider.id,
                    name = provider.name,
                    available = provider.isAvailable
                )
            }
    }
}
