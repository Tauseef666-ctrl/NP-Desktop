/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppShortcutScopeTest {

    private val resolver = ShortcutResolver()

    private fun appAction(key: Key, ctrl: Boolean = false, shift: Boolean = false): PlayerAction? =
        AppShortcutScope.appActionFor(resolver, key, isCtrlPressed = ctrl, isShiftPressed = shift)

    @Test
    fun `media keys map to app-wide transport actions`() {
        assertEquals(PlayerAction.PLAY_PAUSE, appAction(Key.MediaPlayPause))
        assertEquals(PlayerAction.STOP, appAction(Key.MediaStop))
        assertEquals(PlayerAction.NEXT, appAction(Key.MediaNext))
        assertEquals(PlayerAction.PREVIOUS, appAction(Key.MediaPrevious))
    }

    @Test
    fun `only transport media keys are flagged as media keys`() {
        assertTrue(AppShortcutScope.isMediaKey(Key.MediaPlayPause))
        assertTrue(AppShortcutScope.isMediaKey(Key.MediaStop))
        assertTrue(AppShortcutScope.isMediaKey(Key.MediaNext))
        assertTrue(AppShortcutScope.isMediaKey(Key.MediaPrevious))
        assertFalse(AppShortcutScope.isMediaKey(Key.VolumeUp))
        assertFalse(AppShortcutScope.isMediaKey(Key.VolumeDown))
        assertFalse(AppShortcutScope.isMediaKey(Key.VolumeMute))
        assertFalse(AppShortcutScope.isMediaKey(Key.Spacebar))
    }

    @Test
    fun `space and horizontal arrows are app-wide`() {
        assertEquals(PlayerAction.PLAY_PAUSE, appAction(Key.Spacebar))
        assertEquals(PlayerAction.SEEK_BACKWARD, appAction(Key.DirectionLeft))
        assertEquals(PlayerAction.SEEK_FORWARD, appAction(Key.DirectionRight))
    }

    @Test
    fun `player-only keys never resolve app-wide`() {
        assertNull(appAction(Key.S))
        assertNull(appAction(Key.M))
        assertNull(appAction(Key.DirectionUp))
        assertNull(appAction(Key.DirectionDown))
        assertNull(appAction(Key.VolumeUp))
        assertNull(appAction(Key.VolumeDown))
        assertNull(appAction(Key.VolumeMute))
        assertNull(appAction(Key.Enter))
        assertNull(appAction(Key.Unknown))
    }

    @Test
    fun `ctrl-modified keys are not claimed app-wide`() {
        assertNull(appAction(Key.Spacebar, ctrl = true))
        assertNull(appAction(Key.MediaNext, ctrl = true))
    }

    @Test
    fun `ctrl f and ctrl m are the focus-search and mini-player app-wide bindings`() {
        assertEquals(PlayerAction.FOCUS_SEARCH, appAction(Key.F, ctrl = true))
        assertEquals(PlayerAction.TOGGLE_MINI_PLAYER, appAction(Key.M, ctrl = true))
    }

    @Test
    fun `F11 is the app-wide fullscreen binding`() {
        assertEquals(PlayerAction.TOGGLE_FULLSCREEN, appAction(Key.F11))
        assertNull(appAction(Key.F11, ctrl = true))
    }

    @Test
    fun `plain f and plain m are never claimed app-wide`() {
        assertNull(appAction(Key.F, ctrl = false))
        // Plain M stays a player-only MUTE and must not be hijacked as the mini-player toggle.
        assertNull(appAction(Key.M, ctrl = false))
    }

    @Test
    fun `shift does not change the resolved app-wide action`() {
        assertEquals(PlayerAction.SEEK_FORWARD, appAction(Key.DirectionRight, shift = true))
        assertEquals(PlayerAction.PLAY_PAUSE, appAction(Key.Spacebar, shift = true))
    }

    @Test
    fun `app-wide bindings are a strict subset of the player resolver`() {
        val keys = listOf(
            Key.MediaPlayPause,
            Key.MediaStop,
            Key.MediaNext,
            Key.MediaPrevious,
            Key.Spacebar,
            Key.DirectionLeft,
            Key.DirectionRight
        )
        keys.forEach { key ->
            val appWide = appAction(key)
            assertTrue(appWide != null, "expected app-wide binding for $key")
            assertEquals(
                appWide,
                resolver.actionFor(key),
                "app-wide action must match the player resolver for $key"
            )
        }
    }
}
