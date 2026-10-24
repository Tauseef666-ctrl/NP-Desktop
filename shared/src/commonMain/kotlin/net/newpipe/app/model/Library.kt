/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.model

/**
 * A playlist the user created locally.
 *
 * The extraction engine exposes remote playlists, but it offers no write access. Local playlists
 * therefore live entirely in the application and keep the same [ContentItem] entries that the rest
 * of the application uses, so a playlist entry can be opened like any other item.
 */
data class SavedPlaylist(
    val id: Long,
    val name: String,
    val items: List<ContentItem> = emptyList()
)

/**
 * A channel the user subscribed to.
 *
 * The subscription is local to the application: the extraction engine exposes channels but offers
 * no write access to the account of the service.
 */
data class SubscribedChannel(
    val serviceId: Int,
    val url: String,
    val name: String,
    val avatarUrl: String? = null
)

/**
 * A video that was downloaded to the local storage.
 *
 * @param id stable identifier, currently the absolute path of the downloaded file
 * @param item the metadata of the downloaded video
 * @param filePath the absolute path of the file on disk
 * @param quality the quality label of the downloaded stream, for example `720p`
 * @param audioFilePath the absolute path of the separate audio file when the video stream had no
 * audio
 */
data class DownloadedVideo(
    val id: String,
    val item: ContentItem,
    val filePath: String,
    val quality: String,
    val formatId: String,
    val audioFilePath: String? = null,
    val downloadedAt: Long
)
