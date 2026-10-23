/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ServiceInfo
import net.newpipe.app.service.ContentRepository
import net.newpipe.app.service.ServiceKiosk
import net.newpipe.app.service.ServiceRegistry
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the home screen, which shows the predefined content lists ("kiosks") of a service.
 *
 * Loading a kiosk cancels the request that is still running, so that a slow response of an
 * obsolete kiosk can never replace the content of the one the user selected last.
 */
@KoinViewModel
class HomeViewModel(
    private val repository: ContentRepository,
    private val serviceRegistry: ServiceRegistry
) : ViewModel() {

    private val _services = MutableStateFlow<List<ServiceInfo>>(emptyList())
    val services = _services.asStateFlow()

    private val _serviceId = MutableStateFlow(DEFAULT_SERVICE_ID)
    val serviceId = _serviceId.asStateFlow()

    private val _kiosks = MutableStateFlow<List<ServiceKiosk>>(emptyList())
    val kiosks = _kiosks.asStateFlow()

    private val _selectedKiosk = MutableStateFlow<String?>(null)
    val selectedKiosk = _selectedKiosk.asStateFlow()

    private val _items = MutableStateFlow<List<ContentItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var loadJob: Job? = null
    private var loadGeneration = 0

    init {
        val services = serviceRegistry.services()
        _services.value = services
        if (services.none { it.id == _serviceId.value }) {
            services.firstOrNull()?.let { _serviceId.value = it.id }
        }
        refreshKiosks()
    }

    /**
     * Changes the service and loads the default kiosk of the new service.
     */
    fun selectService(id: Int) {
        if (_serviceId.value == id) return
        _serviceId.value = id
        _items.value = emptyList()
        refreshKiosks()
    }

    /**
     * Loads the given kiosk.
     */
    fun selectKiosk(kioskId: String) {
        if (_selectedKiosk.value == kioskId) return
        _selectedKiosk.value = kioskId
        load(kioskId)
    }

    /**
     * Reloads the selected kiosk.
     */
    fun reload() {
        val kioskId = _selectedKiosk.value ?: return
        load(kioskId)
    }

    private fun refreshKiosks() {
        val kiosks = repository.kiosks(_serviceId.value)
        _kiosks.value = kiosks
        if (kiosks.isEmpty()) {
            _selectedKiosk.value = null
            _items.value = emptyList()
            _error.value = "This service does not provide any content lists"
            _loading.value = false
            return
        }
        loadWithFallback(kiosks)
    }

    private fun load(kioskId: String) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        _items.value = emptyList()
        _error.value = null
        _loading.value = true

        loadJob = viewModelScope.launch {
            try {
                val items = repository.kiosk(_serviceId.value, kioskId)
                if (generation != loadGeneration) return@launch
                Logger.i(messageString = "Loaded ${items.size} items from kiosk '$kioskId'")
                _items.value = items
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (generation != loadGeneration) return@launch
                Logger.e(messageString = "Loading the kiosk failed", throwable = exception)
                _error.value = exception.message ?: "The content could not be loaded"
            } finally {
                if (generation == loadGeneration) {
                    _loading.value = false
                }
            }
        }
    }

    /**
     * Loads the first kiosk that can actually be fetched.
     *
     * Services change the shape of their kiosk pages from time to time, and the extraction engine
     * cannot always follow every change at once. Rather than showing an error on the home screen
     * when the preferred list is temporarily broken, the next available list is shown. A service
     * whose lists all fail still reports the last error to the user.
     */
    private fun loadWithFallback(kiosks: List<ServiceKiosk>) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        _items.value = emptyList()
        _error.value = null
        _loading.value = true

        loadJob = viewModelScope.launch {
            var lastError: Throwable? = null
            var loaded: ServiceKiosk? = null

            for (kiosk in kiosks.take(MAX_KIOSK_ATTEMPTS)) {
                try {
                    val items = repository.kiosk(_serviceId.value, kiosk.id)
                    if (generation != loadGeneration) return@launch
                    Logger.i(messageString = "Loaded ${items.size} items from kiosk '${kiosk.id}'")
                    loaded = kiosk
                    _items.value = items
                    lastError = null
                    break
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    if (generation != loadGeneration) return@launch
                    Logger.w(
                        messageString = "Kiosk '${kiosk.id}' could not be loaded",
                        throwable = exception
                    )
                    lastError = exception
                }
            }

            if (generation != loadGeneration) return@launch

            val kiosk = loaded
            if (kiosk != null) {
                _selectedKiosk.value = kiosk.id
            } else {
                _error.value = lastError?.message ?: "The content could not be loaded"
            }
            _loading.value = false
        }
    }

    companion object {
        /** YouTube, the default service of NewPipe. */
        const val DEFAULT_SERVICE_ID = 0

        /** How many content lists are tried before the home screen reports an error. */
        private const val MAX_KIOSK_ATTEMPTS = 3
    }
}
