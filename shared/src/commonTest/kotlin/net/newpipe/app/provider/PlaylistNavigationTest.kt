/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistNavigationTest {

    @Test
    fun selectStoresSelection() {
        val navigation = PlaylistNavigation()

        navigation.select("https://playlist", "My playlist")

        assertEquals(
            PlaylistNavigation.Selection(url = "https://playlist", title = "My playlist"),
            navigation.selected.value
        )
    }

    @Test
    fun selectWithBlankUrlIsIgnored() {
        val navigation = PlaylistNavigation()

        navigation.select("   ", "My playlist")

        assertNull(navigation.selected.value)
    }

    @Test
    fun selectTwiceKeepsLatest() {
        val navigation = PlaylistNavigation()

        navigation.select("https://first", "First")
        navigation.select("https://second", "Second")

        assertEquals(
            PlaylistNavigation.Selection(url = "https://second", title = "Second"),
            navigation.selected.value
        )
    }

    @Test
    fun clearResetsSelection() {
        val navigation = PlaylistNavigation()
        navigation.select("https://playlist", "My playlist")

        navigation.clear()

        assertNull(navigation.selected.value)
    }
}
