/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

/**
 * What should happen when a [ProviderContentItem] is tapped in a content listing. Kept as a small
 * enum so every screen routes taps the same way instead of each re-deriving the kind check.
 */
enum class ProviderClickAction { PLAY, OPEN_CHANNEL, OPEN_PLAYLIST }

/** Maps a content [ProviderContentItem.kind] to the action a listing should take on tap. */
fun providerClickAction(item: ProviderContentItem): ProviderClickAction = when (item.kind) {
    ProviderContentKind.CHANNEL -> ProviderClickAction.OPEN_CHANNEL
    ProviderContentKind.PLAYLIST -> ProviderClickAction.OPEN_PLAYLIST
    else -> ProviderClickAction.PLAY
}
