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

class PlayerShortcutsTest {

    private val resolver = ShortcutResolver()

    @Test
    fun `default binding plays and pauses with space`() {
        assertEquals(PlayerAction.PLAY_PAUSE, resolver.actionFor(Key.Spacebar))
    }

    @Test
    fun `default binding toggles mute with M`() {
        assertEquals(PlayerAction.MUTE, resolver.actionFor(Key.M))
    }

    @Test
    fun `ctrl f and ctrl m resolve to focus-search and mini-player`() {
        assertEquals(PlayerAction.FOCUS_SEARCH, resolver.actionFor(Key.F, isCtrlPressed = true))
        assertEquals(PlayerAction.TOGGLE_MINI_PLAYER, resolver.actionFor(Key.M, isCtrlPressed = true))
    }

    @Test
    fun `plain f is unbound and plain m stays mute`() {
        assertNull(resolver.actionFor(Key.F))
        assertEquals(PlayerAction.MUTE, resolver.actionFor(Key.M))
    }

    @Test
    fun `media keys resolve to transport actions`() {
        assertEquals(PlayerAction.PLAY_PAUSE, resolver.actionFor(Key.MediaPlayPause))
        assertEquals(PlayerAction.NEXT, resolver.actionFor(Key.MediaNext))
        assertEquals(PlayerAction.PREVIOUS, resolver.actionFor(Key.MediaPrevious))
        assertEquals(PlayerAction.STOP, resolver.actionFor(Key.MediaStop))
        assertEquals(PlayerAction.VOLUME_UP, resolver.actionFor(Key.VolumeUp))
        assertEquals(PlayerAction.VOLUME_DOWN, resolver.actionFor(Key.VolumeDown))
        assertEquals(PlayerAction.MUTE, resolver.actionFor(Key.VolumeMute))
    }

    @Test
    fun `arrow keys seek and change volume`() {
        assertEquals(PlayerAction.SEEK_FORWARD, resolver.actionFor(Key.DirectionRight))
        assertEquals(PlayerAction.SEEK_BACKWARD, resolver.actionFor(Key.DirectionLeft))
        assertEquals(PlayerAction.VOLUME_UP, resolver.actionFor(Key.DirectionUp))
        assertEquals(PlayerAction.VOLUME_DOWN, resolver.actionFor(Key.DirectionDown))
    }

    @Test
    fun `first binding wins for an action with several keys`() {
        assertEquals(PlayerAction.PLAY_PAUSE, resolver.actionFor(Key.Spacebar))
        assertEquals(PlayerAction.PLAY_PAUSE, resolver.actionFor(Key.MediaPlayPause))
    }

    @Test
    fun `unknown keys are ignored`() {
        assertNull(resolver.actionFor(Key.Enter))
        assertNull(resolver.actionFor(Key.Unknown))
    }

    @Test
    fun `ctrl key changes the resolution`() {
        assertNull(resolver.actionFor(Key.S, isCtrlPressed = true))
    }

    @Test
    fun `default configuration has no conflicts`() {
        assertTrue(resolver.conflicts().isEmpty())
    }

    @Test
    fun `duplicate combinations are reported as conflicts`() {
        val duplicate = Shortcut(PlayerAction.MUTE, Key.M)
        val broken = ShortcutResolver(listOf(duplicate, Shortcut(PlayerAction.STOP, Key.M)))
        val conflicts = broken.conflicts()
        assertEquals(1, conflicts.size)
        assertEquals(Key.M, conflicts.single().key)
    }

    @Test
    fun `seek step mirrors shift state`() {
        assertEquals(10_000L, PlayerShortcuts.seekStepMillis(isShiftPressed = false))
        assertEquals(60_000L, PlayerShortcuts.seekStepMillis(isShiftPressed = true))
    }

    @Test
    fun `configured skip interval is honored for normal seeks`() {
        assertEquals(25_000L, PlayerShortcuts.seekStepMillis(isShiftPressed = false, configuredSeekMs = 25_000L))
        assertEquals(60_000L, PlayerShortcuts.seekStepMillis(isShiftPressed = true, configuredSeekMs = 25_000L))
    }

    @Test
    fun `configured skip interval is floored at one second`() {
        assertEquals(1_000L, PlayerShortcuts.seekStepMillis(isShiftPressed = false, configuredSeekMs = 100L))
    }

    @Test
    fun `reserved media keys are protected from rebinding`() {
        assertTrue(PlayerShortcuts.reservedMediaKeys.contains(Key.MediaNext))
        assertFalse(PlayerShortcuts.reservedMediaKeys.contains(Key.M))
    }
}