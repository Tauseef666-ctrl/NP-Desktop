/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderRegistry
import org.koin.core.annotation.KoinViewModel

data class ProviderView(
    val id: String,
    val name: String,
    val isAvailable: Boolean,
    val capabilities: ProviderCapabilities
)

@KoinViewModel
class ContentSettingsViewModel(
    private val registry: ProviderRegistry
) : ViewModel() {

    val providers: StateFlow<List<ProviderView>> = registry.providers
        .map { list -> list.map { it.toView() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = registry.providers.value.map { it.toView() }
        )

    val hasProviders: StateFlow<Boolean> = registry.providers
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = registry.providers.value.isNotEmpty()
        )

    private fun MediaProvider.toView(): ProviderView = ProviderView(
        id = id,
        name = name,
        isAvailable = isAvailable,
        capabilities = capabilities
    )
}