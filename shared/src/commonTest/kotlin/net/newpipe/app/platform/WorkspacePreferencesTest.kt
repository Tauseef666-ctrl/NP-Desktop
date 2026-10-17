/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspacePreferencesTest {

    private fun store(settings: MapSettings = MapSettings()) =
        WorkspacePreferences(settings, Json)

    private fun bounds() = WindowBounds(x = 40, y = 60, width = 1280, height = 800)

    @Test
    fun modeDefaultsToBalancedWhenMissing() {
        val s = store()
        assertEquals(WorkspaceMode.BALANCED, s.mode())
        assertNull(s.windowBounds())
    }

    @Test
    fun modeDefaultsToBalancedWhenCorrupt() {
        val settings = MapSettings()
        settings.putString("npp_workspace", "not json {")
        assertEquals(WorkspaceMode.BALANCED, WorkspacePreferences(settings, Json).mode())
        assertNull(WorkspacePreferences(settings, Json).windowBounds())
    }

    @Test
    fun modeAndBoundsRoundTrip() {
        val s = store()
        s.setMode(WorkspaceMode.EXPANDED)
        s.setWindowBounds(bounds())
        assertEquals(WorkspaceMode.EXPANDED, s.mode())
        assertEquals(bounds(), s.windowBounds())
    }

    @Test
    fun setModeDoesNotClobberBounds() {
        val s = store()
        s.setWindowBounds(bounds())
        s.setMode(WorkspaceMode.COMPACT)
        assertEquals(WorkspaceMode.COMPACT, s.mode())
        assertEquals(bounds(), s.windowBounds())
    }

    @Test
    fun setWindowBoundsDoesNotClobberMode() {
        val s = store()
        s.setMode(WorkspaceMode.EXPANDED)
        s.setWindowBounds(bounds())
        assertEquals(WorkspaceMode.EXPANDED, s.mode())
        assertEquals(bounds(), s.windowBounds())
    }

    @Test
    fun invalidBoundsReturnNull() {
        val s = store()
        s.setWindowBounds(WindowBounds(width = 0, height = 0))
        assertNull(s.windowBounds())
        s.setWindowBounds(WindowBounds(width = -800, height = 600))
        assertNull(s.windowBounds())
        s.setWindowBounds(WindowBounds(width = 800, height = -600))
        assertNull(s.windowBounds())
        s.setWindowBounds(WindowBounds(width = 800, height = 0))
        assertNull(s.windowBounds())
    }

    @Test
    fun clearRemovesKey() {
        val s = store()
        s.setMode(WorkspaceMode.EXPANDED)
        s.setWindowBounds(bounds())
        s.setMiniPlayerBounds(bounds())
        s.setMiniPlayerCompact(true)
        s.clear()
        assertEquals(WorkspaceMode.BALANCED, s.mode())
        assertNull(s.windowBounds())
        assertNull(s.miniPlayerBounds())
        assertEquals(false, s.miniPlayerCompact())
    }

    @Test
    fun miniPlayerBoundsRoundTrip() {
        val s = store()
        val mini = WindowBounds(x = 10, y = 20, width = 420, height = 160)
        s.setMiniPlayerBounds(mini)
        assertEquals(mini, s.miniPlayerBounds())
    }

    @Test
    fun miniPlayerCompactDefaultsFalseAndRoundTrips() {
        val s = store()
        assertEquals(false, s.miniPlayerCompact())
        s.setMiniPlayerCompact(true)
        assertEquals(true, s.miniPlayerCompact())
        s.setMiniPlayerCompact(false)
        assertEquals(false, s.miniPlayerCompact())
    }

    @Test
    fun invalidMiniPlayerBoundsReturnNull() {
        val s = store()
        s.setMiniPlayerBounds(WindowBounds(width = 0, height = 0))
        assertNull(s.miniPlayerBounds())
        s.setMiniPlayerBounds(WindowBounds(width = -1, height = 100))
        assertNull(s.miniPlayerBounds())
    }

    @Test
    fun miniPlayerDoesNotClobberMainWorkspace() {
        val s = store()
        s.setMode(WorkspaceMode.EXPANDED)
        s.setWindowBounds(bounds())
        s.setMiniPlayerBounds(WindowBounds(x = 1, y = 2, width = 420, height = 160))
        s.setMiniPlayerCompact(true)
        assertEquals(WorkspaceMode.EXPANDED, s.mode())
        assertEquals(bounds(), s.windowBounds())
    }

    @Test
    fun unrelatedKeysUntouched() {
        val settings = MapSettings()
        settings.putString("other_key", "keep")
        val s = WorkspacePreferences(settings, Json)
        s.setWindowBounds(bounds())
        s.clear()
        assertEquals("keep", settings.getString("other_key", ""))
    }
}