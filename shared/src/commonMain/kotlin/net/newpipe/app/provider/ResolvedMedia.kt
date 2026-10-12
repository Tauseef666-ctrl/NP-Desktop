/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import net.newpipe.app.player.MediaItem

/**
 * A playable media stream resolved from a [ProviderContentItem]'s page URL.
 *
 * @param streamUrl The direct URL the playback engine can open.
 * @param isLive True for a live stream (not seekable to a stored position, no duration).
 * @param audioOnly True when only an audio rendition could be resolved.
 * @param durationSeconds Known duration, or null when unknown/live.
 */
data class ResolvedMedia(
    val streamUrl: String,
    val isLive: Boolean = false,
    val audioOnly: Boolean = false,
    val durationSeconds: Long? = null
)

/**
 * Build the play-queue [MediaItem] for a resolved provider [item]. The item's own
 * [ProviderContentItem.durationSeconds] is the fallback when the resolved stream did not report one.
 */
fun ProviderContentItem.toMediaItem(resolved: ResolvedMedia): MediaItem = MediaItem(
    id = id,
    title = title,
    url = resolved.streamUrl,
    durationMs = ((resolved.durationSeconds ?: durationSeconds) ?: 0L) * 1000L,
    isLive = resolved.isLive || kind == ProviderContentKind.LIVE,
    audioOnly = resolved.audioOnly,
    thumbnailUrl = thumbnailUrl
)
