/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import net.newpipe.app.player.MediaItem

/**
 * Turns provider content into playback. Kept as an interface so view models depend on the intent
 * ("play this provider item") without knowing how resolution or the playback engine work, and so
 * tests can supply a fake instead of a real provider plus engine.
 */
interface MediaPlayback {

    /** Resolve [item] to a playable [MediaItem] without starting playback. */
    suspend fun resolve(item: ProviderContentItem): ProviderResult<MediaItem>

    /** Resolve [item], then enqueue and start it through the shared playback controller. */
    suspend fun play(item: ProviderContentItem): ProviderResult<Unit>
}
