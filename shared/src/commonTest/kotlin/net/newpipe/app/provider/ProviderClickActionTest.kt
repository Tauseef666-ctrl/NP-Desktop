/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderClickActionTest {

    private fun item(kind: ProviderContentKind) = ProviderContentItem(
        id = "id",
        title = "Title",
        url = "https://page",
        kind = kind
    )

    @Test
    fun videoMapsToPlay() {
        assertEquals(ProviderClickAction.PLAY, providerClickAction(item(ProviderContentKind.VIDEO)))
    }

    @Test
    fun audioMapsToPlay() {
        assertEquals(ProviderClickAction.PLAY, providerClickAction(item(ProviderContentKind.AUDIO)))
    }

    @Test
    fun liveMapsToPlay() {
        assertEquals(ProviderClickAction.PLAY, providerClickAction(item(ProviderContentKind.LIVE)))
    }

    @Test
    fun channelMapsToOpenChannel() {
        assertEquals(
            ProviderClickAction.OPEN_CHANNEL,
            providerClickAction(item(ProviderContentKind.CHANNEL))
        )
    }

    @Test
    fun playlistMapsToOpenPlaylist() {
        assertEquals(
            ProviderClickAction.OPEN_PLAYLIST,
            providerClickAction(item(ProviderContentKind.PLAYLIST))
        )
    }
}
