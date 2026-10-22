/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.model.ChannelDetails
import net.newpipe.app.service.ContentException
import net.newpipe.app.service.ContentRepository
import org.koin.core.annotation.KoinViewModel

/**
 * Loads a channel with its most recent uploads.
 */
@KoinViewModel
class ChannelViewModel(
    private val repository: ContentRepository
) : ViewModel() {

    private val _details = MutableStateFlow<ChannelDetails?>(null)
    val details = _details.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var loadedUrl: String? = null

    /**
     * Loads the channel. Repeated calls with the same url are ignored.
     */
    fun load(serviceId: Int, url: String) {
        if (loadedUrl == url) return
        loadedUrl = url

        _loading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                _details.value = repository.channel(serviceId, url)
            } catch (exception: ContentException) {
                Logger.e(messageString = "Loading the channel failed", throwable = exception)
                _error.value = exception.message
            } catch (exception: Exception) {
                Logger.e(messageString = "Loading the channel failed", throwable = exception)
                _error.value = exception.message
            } finally {
                _loading.value = false
            }
        }
    }
}
