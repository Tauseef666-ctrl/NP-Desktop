/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.stream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.model.MediaStream
import net.newpipe.app.model.StreamDetails
import net.newpipe.app.service.ContentException
import net.newpipe.app.service.ContentRepository
import org.koin.core.annotation.KoinViewModel

/**
 * Loads the details of a single stream and keeps the currently selected quality.
 */
@KoinViewModel
class StreamViewModel(
    private val repository: ContentRepository
) : ViewModel() {

    private val _details = MutableStateFlow<StreamDetails?>(null)
    val details = _details.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _selectedVideo = MutableStateFlow<MediaStream?>(null)
    val selectedVideo = _selectedVideo.asStateFlow()

    private val _selectedAudio = MutableStateFlow<MediaStream?>(null)
    val selectedAudio = _selectedAudio.asStateFlow()

    private var loadedUrl: String? = null

    /**
     * Loads the stream details. Repeated calls with the same url are ignored.
     */
    fun load(serviceId: Int, url: String) {
        if (loadedUrl == url) return
        loadedUrl = url

        _loading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val details = repository.streamDetails(serviceId, url)
                _details.value = details
                _selectedVideo.value = pickBestVideo(details)
                _selectedAudio.value = pickBestAudio(details)
            } catch (exception: ContentException) {
                Logger.e(messageString = "Loading the stream failed", throwable = exception)
                _error.value = exception.message
            } catch (exception: Exception) {
                Logger.e(messageString = "Loading the stream failed", throwable = exception)
                _error.value = exception.message
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Selects the video quality to play.
     */
    fun selectVideo(stream: MediaStream) {
        _selectedVideo.value = stream
    }

    /**
     * Selects the audio track to play.
     */
    fun selectAudio(stream: MediaStream) {
        _selectedAudio.value = stream
    }

    companion object {
        /**
         * The video stream with the highest resolution, falling back to the highest bitrate.
         */
        fun pickBestVideo(details: StreamDetails): MediaStream? =
            details.videoStreams
                .maxWithOrNull(
                    compareBy<MediaStream> { it.height ?: 0 }
                        .thenBy { it.bitrate }
                )

        /**
         * The audio stream with the highest bitrate.
         */
        fun pickBestAudio(details: StreamDetails): MediaStream? =
            details.audioStreams.maxWithOrNull(compareBy { it.bitrate })
    }
}
