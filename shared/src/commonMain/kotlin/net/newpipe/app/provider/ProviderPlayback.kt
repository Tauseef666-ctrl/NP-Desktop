/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlaybackController
import org.koin.core.annotation.Singleton

/**
 * Default [MediaPlayback]: resolves an item's page URL through [ProviderContentRepository] and hands
 * the resulting playable [net.newpipe.app.player.MediaItem] to the shared [PlaybackController], which
 * owns the queue and engine. Resolution failures are passed through unchanged so the UI can map them
 * to honest, localized text.
 */
@Singleton(binds = [MediaPlayback::class])
class ProviderPlayback(
    private val repository: ProviderContentRepository,
    private val playback: PlaybackController
) : MediaPlayback {

    override suspend fun resolve(item: ProviderContentItem): ProviderResult<MediaItem> =
        when (val result = repository.resolve(item.url)) {
            is ProviderResult.Success -> ProviderResult.Success(item.toMediaItem(result.value))
            is ProviderResult.Failure -> result
        }

    override suspend fun play(item: ProviderContentItem): ProviderResult<Unit> =
        when (val result = resolve(item)) {
            is ProviderResult.Success -> {
                playback.playItem(result.value)
                ProviderResult.Success(Unit)
            }

            is ProviderResult.Failure -> result
        }
}
