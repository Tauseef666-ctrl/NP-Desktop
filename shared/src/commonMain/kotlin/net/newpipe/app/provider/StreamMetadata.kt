/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

/**
 * Descriptive metadata about a stream page, independent of any provider. Every field is optional so
 * a provider only fills in what it really knows; the UI shows the values it has and omits the rest
 * instead of inventing them.
 *
 * @param title The stream title (the only always-present field).
 * @param uploaderName Display name of the uploader, or null when unknown.
 * @param uploaderUrl The uploader's channel page, when the provider exposes one.
 * @param uploaderVerified True when the provider marks the uploader as verified.
 * @param subscriberCount Subscriber count, or null when unknown.
 * @param viewCount View count, or null when unknown.
 * @param likeCount Like count, or null when unknown.
 * @param uploadDate Human-readable upload date, or null when unknown.
 * @param durationSeconds Stream duration in seconds, or null when unknown/live.
 * @param thumbnailUrl Best available thumbnail, or null when none.
 * @param description Long description, or null when empty/unknown.
 * @param category Provider category, or null when unknown.
 * @param tags Provider tags (may be empty).
 * @param isLive True for a live stream.
 * @param related Related/recommended videos from the watch page (may be empty).
 */
data class StreamMetadata(
    val title: String,
    val uploaderName: String? = null,
    val uploaderUrl: String? = null,
    val uploaderVerified: Boolean = false,
    val subscriberCount: Long? = null,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val uploadDate: String? = null,
    val durationSeconds: Long? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val isLive: Boolean = false,
    val related: List<ProviderContentItem> = emptyList()
)
