/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.stream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.russhwolf.settings.Settings
import net.newpipe.app.model.ChannelDetails
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.MediaStream
import net.newpipe.app.model.StreamDetails
import net.newpipe.app.preferences.VideoAudioPreferences
import net.newpipe.app.service.ContentException
import net.newpipe.app.service.ContentRepository
import org.koin.core.annotation.KoinViewModel

/**
 * Loads the details of a single stream and keeps the currently selected quality.
 */
@KoinViewModel
class StreamViewModel(
    private val repository: ContentRepository,
    private val settings: Settings
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

    private val _relatedItems = MutableStateFlow<List<ContentItem>>(emptyList())
    val relatedItems = _relatedItems.asStateFlow()

    private val _channel = MutableStateFlow<ChannelDetails?>(null)
    val channel = _channel.asStateFlow()

    private val _loadingMoreRelated = MutableStateFlow(false)
    val loadingMoreRelated = _loadingMoreRelated.asStateFlow()

    private val _hasMoreRelated = MutableStateFlow(true)
    val hasMoreRelated = _hasMoreRelated.asStateFlow()

    private var loadedUrl: String? = null

    /**
     * Loads the stream details. Repeated calls with the same url are ignored.
     */
    fun load(serviceId: Int, url: String) {
        if (loadedUrl == url) return
        loadedUrl = url

        _loading.value = true
        _error.value = null
        _channel.value = null

        viewModelScope.launch {
            try {
                val details = repository.streamDetails(serviceId, url)
                _details.value = details
                val preferredResolution = settings.getString(
                    VideoAudioPreferences.KEY_DEFAULT_RESOLUTION,
                    VideoAudioPreferences.DEFAULT_RESOLUTION
                )
                val preferredAudioFormat = settings.getString(
                    VideoAudioPreferences.KEY_DEFAULT_AUDIO_FORMAT,
                    VideoAudioPreferences.DEFAULT_AUDIO_FORMAT
                )
                _selectedVideo.value = pickPreferredVideo(details, preferredResolution)
                _selectedAudio.value = pickPreferredAudio(details, preferredAudioFormat)
                _channel.value = details.uploaderUrl?.let { channelUrl ->
                    try {
                        repository.channel(details.serviceId, channelUrl)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Logger.w(messageString = "The channel of the stream could not be loaded", throwable = exception)
                        null
                    }
                }
                _relatedItems.value = details.relatedItems
                _hasMoreRelated.value = details.uploaderUrl != null
                _loadingMoreRelated.value = false
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

    /**
     * Loads more entries for the related list.
     *
     * The extraction engine only returns a fixed first page of related streams, so the most recent
     * uploads of the same channel are appended. This keeps the list growing while the user scrolls
     * and is the only real continuation the extraction engine exposes for related streams.
     */
    fun loadMoreRelated() {
        if (_loadingMoreRelated.value || !_hasMoreRelated.value) return
        val details = _details.value ?: return
        val uploaderUrl = details.uploaderUrl

        if (uploaderUrl == null) {
            _hasMoreRelated.value = false
            return
        }

        _loadingMoreRelated.value = true

        viewModelScope.launch {
            try {
                val channel = repository.channel(details.serviceId, uploaderUrl)
                val existing = _relatedItems.value.map { it.url }.toSet()
                val more = channel.items.filter {
                    it.type == ContentType.STREAM && it.url !in existing
                }

                if (more.isEmpty()) {
                    _hasMoreRelated.value = false
                } else {
                    _relatedItems.value = _relatedItems.value + more
                    Logger.i(
                        messageString = "Loaded ${more.size} more related streams"
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Logger.w(
                    messageString = "Loading more related streams failed",
                    throwable = exception
                )
                _hasMoreRelated.value = false
            } finally {
                _loadingMoreRelated.value = false
            }
        }
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
         * The video stream that matches the preferred resolution label, e.g. `720p60`.
         *
         * The closest resolution that does not exceed the preferred height wins; a `p60` label
         * additionally prefers streams with a high frame rate. `best_resolution` or an empty
         * label falls back to the highest available resolution.
         */
        fun pickPreferredVideo(details: StreamDetails, preferredResolution: String): MediaStream? {
            val streams = details.videoStreams
            if (streams.isEmpty()) return null

            // Live streams must be played from their HLS manifest: the DASH manifests of a live
            // stream expire immediately and cannot be opened by libVLC.
            if (details.liveNow) {
                val hls = streams.filter {
                    it.url.contains("m3u8") || it.formatId.lowercase().contains("hls")
                }
                val chosen = if (hls.isNotEmpty()) {
                    hls.maxWithOrNull(
                        compareBy<MediaStream> { it.height ?: 0 }.thenBy { it.bitrate }
                    )
                } else {
                    null
                }
                if (chosen != null) return chosen
            }

            if (preferredResolution == VideoAudioPreferences.BEST_RESOLUTION ||
                preferredResolution.isBlank()
            ) {
                return pickBestVideo(details)
            }

            val label = preferredResolution.lowercase()
            val pIndex = label.indexOf('p')
            val targetHeight = if (pIndex > 0) {
                label.substring(0, pIndex).trim().toIntOrNull() ?: return pickBestVideo(details)
            } else {
                return pickBestVideo(details)
            }
            val preferHighFps = label.substringAfter('p', "").startsWith("60")
                || preferredResolution.endsWith("p60")

            val fitting = streams.filter { (it.height ?: 0) <= targetHeight }
            val within = fitting.filter { it.height != null && it.height == targetHeight }

            val chosen = when {
                within.isNotEmpty() -> within.maxWithOrNull(
                    compareBy<MediaStream> { if (preferHighFps) (it.fps ?: 0) >= 50 else (it.fps ?: 0) < 50 }
                        .thenBy { it.bitrate }
                )
                else -> fitting.maxWithOrNull(
                    compareBy<MediaStream> { it.height ?: 0 }.thenBy { it.bitrate }
                )
            }

            return chosen
                ?: streams.filter { it.height != null }.minWithOrNull(
                    compareBy<MediaStream> { it.height ?: 0 }
                )
                ?: pickBestVideo(details)
        }

        /**
         * The audio stream with the highest bitrate.
         */
        fun pickBestAudio(details: StreamDetails): MediaStream? =
            details.audioStreams.maxWithOrNull(compareBy { it.bitrate })

        /**
         * The audio stream of the preferred format, falling back to the highest bitrate.
         *
         * YouTube exposes the formats in the stream id, so the format preference is matched
         * against it: `audio_m4a` prefers AAC/M4A and `audio_webm` prefers Opus/WebM.
         */
        fun pickPreferredAudio(details: StreamDetails, preferredFormat: String): MediaStream? {
            val streams = details.audioStreams
            if (streams.isEmpty()) return null

            val decorators = when (preferredFormat) {
                VideoAudioPreferences.AUDIO_WEBM -> listOf("webm", "opus")
                VideoAudioPreferences.AUDIO_M4A -> listOf("m4a", "mp4a", "aac")
                else -> emptyList()
            }
            if (decorators.isNotEmpty()) {
                val preferred = streams.filter { stream ->
                    val id = stream.formatId.lowercase()
                    decorators.any { id.contains(it) }
                }
                if (preferred.isNotEmpty()) {
                    return preferred.maxWithOrNull(compareBy { it.bitrate })
                }
            }

            return pickBestAudio(details)
        }
    }
}
