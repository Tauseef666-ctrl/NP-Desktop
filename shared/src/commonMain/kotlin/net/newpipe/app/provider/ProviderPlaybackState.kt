/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

/**
 * Outcome of a user-triggered "play this provider item" request, surfaced by
 * [ProviderPlaybackSession] so a screen can show progress, navigate once playback starts, or report
 * an honest failure instead of pretending playback began.
 */
sealed interface ProviderPlaybackState {

    /** Nothing requested (or the last result was acknowledged by the UI). */
    data object Idle : ProviderPlaybackState

    /** The item is being resolved and handed to the engine. */
    data object Loading : ProviderPlaybackState

    /** Playback of the item started; the UI may navigate to the player. */
    data object Launched : ProviderPlaybackState

    /** Resolution failed for [reason]; the UI shows localized, honest text. */
    data class Failure(val reason: ProviderFailureReason) : ProviderPlaybackState
}
