/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.model

/**
 * A streaming service supported by the extraction engine.
 *
 * The [id] is the service id defined by NewPipeExtractor and is stable across platforms, which
 * keeps user data (subscriptions, history, playlists) portable.
 */
data class ServiceInfo(
    val id: Int,
    val name: String
)

/**
 * The kind of content an item refers to.
 */
enum class ContentType {
    STREAM,
    CHANNEL,
    PLAYLIST
}

/**
 * A single entry of a content list: a search result, a channel upload or a playlist entry.
 */
data class ContentItem(
    val serviceId: Int,
    val url: String,
    val title: String,
    val type: ContentType,
    val uploader: String? = null,
    val uploaderUrl: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val viewCount: Long? = null,
    val description: String? = null,
    val uploadDate: String? = null,
    val liveNow: Boolean = false
)

/**
 * A playable media stream extracted from a service.
 *
 * NewPipe frequently yields a video-only stream plus a separate audio-only stream, therefore
 * [isVideoOnly] is part of the model and the player must never assume that a video stream carries
 * audio.
 */
data class MediaStream(
    val url: String,
    val formatId: String,
    val quality: String,
    val codec: String,
    val bitrate: Int,
    val isVideoOnly: Boolean,
    val audioTrackName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null
)

/**
 * A subtitle track offered by a service.
 */
data class SubtitleTrack(
    val url: String,
    val name: String,
    val languageTag: String?
)

/**
 * Full metadata of a single stream, together with the extracted stream list.
 */
data class StreamDetails(
    val serviceId: Int,
    val url: String,
    val title: String,
    val uploader: String,
    val uploaderUrl: String? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val durationSeconds: Long? = null,
    val viewCount: Long? = null,
    val uploadDate: String? = null,
    val liveNow: Boolean = false,
    val videoStreams: List<MediaStream> = emptyList(),
    val audioStreams: List<MediaStream> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val relatedItems: List<ContentItem> = emptyList()
)

/**
 * Metadata of a channel together with its most recent uploads.
 */
data class ChannelDetails(
    val serviceId: Int,
    val url: String,
    val name: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val description: String? = null,
    val subscriberCount: Long? = null,
    val items: List<ContentItem> = emptyList()
)

/**
 * Metadata of a playlist together with its entries.
 */
data class PlaylistDetails(
    val serviceId: Int,
    val url: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val uploader: String? = null,
    val uploaderUrl: String? = null,
    val itemCount: Long? = null,
    val items: List<ContentItem> = emptyList()
)

/**
 * The result of resolving an arbitrary URL to something NewPipe can display.
 */
sealed interface ResolvedContent {
    data class Stream(val details: StreamDetails) : ResolvedContent

    data class Channel(val details: ChannelDetails) : ResolvedContent

    data class Playlist(val details: PlaylistDetails) : ResolvedContent
}
