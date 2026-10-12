/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

/**
 * The kind of content a [ProviderContentItem] represents. Kept provider-agnostic so any backing
 * implementation (the JVM NewPipeExtractor provider, a future Android provider, a test fake) maps
 * into the same model.
 */
enum class ProviderContentKind { VIDEO, AUDIO, LIVE, PLAYLIST, CHANNEL }

/**
 * A single piece of provider content (a video, an audio track, a live stream, a playlist or a
 * channel) rendered in search results, trending and channel listings.
 *
 * @param id Stable identity for the item; provider-agnostic enough to key UI lists.
 * @param title Display title.
 * @param url Canonical page URL used to open the item.
 * @param thumbnailUrl Best available artwork, or null when the provider gave none.
 * @param uploader Name of the uploader/channel, or null when unknown.
 * @param uploaderUrl URL of the uploader/channel, or null when unknown.
 * @param durationSeconds Duration in seconds, or null when unknown/live.
 * @param kind The kind of content.
 */
data class ProviderContentItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
    val uploaderUrl: String? = null,
    val durationSeconds: Long? = null,
    val kind: ProviderContentKind = ProviderContentKind.VIDEO
)

/**
 * One page of [ProviderContentItem]s plus an optional opaque token for the next page. A null
 * [nextPageToken] means there is no more content to load.
 */
data class ProviderContentPage(
    val items: List<ProviderContentItem>,
    val nextPageToken: String? = null
)

/**
 * Why a provider operation failed. Kept as a small enum so the UI can map each case to localized,
 * honest text instead of a raw exception string.
 */
enum class ProviderFailureReason {
    /** No connected, available provider supports the requested operation. */
    NO_PROVIDER,

    /** A provider exists but does not implement this operation. */
    UNSUPPORTED,

    /** The provider could not be reached (offline, timeout, DNS, 5xx). */
    NETWORK,

    /** The provider answered, but the response could not be understood. */
    PARSE,

    /** Anything else. [ProviderResult.Failure.detail] carries what we know. */
    UNKNOWN
}

/**
 * Outcome of a provider operation. [Failure] carries a machine-readable [reason] so callers never
 * have to parse error strings, and an optional [detail] for diagnostics.
 */
sealed interface ProviderResult<out T> {
    data class Success<T>(val value: T) : ProviderResult<T>
    data class Failure(
        val reason: ProviderFailureReason,
        val detail: String? = null
    ) : ProviderResult<Nothing>
}
