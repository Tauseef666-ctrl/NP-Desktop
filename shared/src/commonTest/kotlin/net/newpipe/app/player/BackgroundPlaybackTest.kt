/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.MutableStateFlow
import net.newpipe.app.platform.PopOutCoordinator
import net.newpipe.app.preferences.VideoAudioPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakePopOutCoordinator(override val supported: Boolean = true) : PopOutCoordinator {
    override val isOpen = MutableStateFlow(false)
    var toggleCount = 0
        private set

    override fun toggle() {
        toggleCount++
        isOpen.value = !isOpen.value
    }
}

class BackgroundPlaybackTest {

    @Test
    fun closeActionMapsKnownPreferences() {
        assertEquals(
            CloseAction.STOP,
            closeActionFor(VideoAudioPreferences.MINIMIZE_ON_EXIT_NONE)
        )
        assertEquals(
            CloseAction.BACKGROUND,
            closeActionFor(VideoAudioPreferences.MINIMIZE_ON_EXIT_BACKGROUND)
        )
        assertEquals(
            CloseAction.POPUP,
            closeActionFor(VideoAudioPreferences.MINIMIZE_ON_EXIT_POPUP)
        )
    }

    @Test
    fun closeActionFallsBackToBackgroundForUnknownOrBlank() {
        assertEquals(CloseAction.BACKGROUND, closeActionFor(null))
        assertEquals(CloseAction.BACKGROUND, closeActionFor(""))
        assertEquals(CloseAction.BACKGROUND, closeActionFor("   "))
        assertEquals(CloseAction.BACKGROUND, closeActionFor("nonsense"))
    }

    @Test
    fun closeActionIgnoresSurroundingWhitespace() {
        assertEquals(
            CloseAction.STOP,
            closeActionFor("  ${VideoAudioPreferences.MINIMIZE_ON_EXIT_NONE}  ")
        )
    }

    @Test
    fun noneActionExitsAndLeavesForeground() {
        val settings = MapSettings()
        settings.putString(
            VideoAudioPreferences.KEY_MINIMIZE_ON_EXIT,
            VideoAudioPreferences.MINIMIZE_ON_EXIT_NONE
        )
        val popOut = FakePopOutCoordinator()
        val controller = BackgroundPlaybackController(settings, popOut)

        assertFalse(controller.onCloseRequested())
        assertFalse(controller.isBackgroundActive.value)
        assertEquals(0, popOut.toggleCount)
    }

    @Test
    fun backgroundActionKeepsRunningWithoutMiniPlayer() {
        val settings = MapSettings()
        settings.putString(
            VideoAudioPreferences.KEY_MINIMIZE_ON_EXIT,
            VideoAudioPreferences.MINIMIZE_ON_EXIT_BACKGROUND
        )
        val popOut = FakePopOutCoordinator()
        val controller = BackgroundPlaybackController(settings, popOut)

        assertTrue(controller.onCloseRequested())
        assertTrue(controller.isBackgroundActive.value)
        assertEquals(0, popOut.toggleCount)
    }

    @Test
    fun popupActionKeepsRunningAndOpensMiniPlayer() {
        val settings = MapSettings()
        settings.putString(
            VideoAudioPreferences.KEY_MINIMIZE_ON_EXIT,
            VideoAudioPreferences.MINIMIZE_ON_EXIT_POPUP
        )
        val popOut = FakePopOutCoordinator()
        val controller = BackgroundPlaybackController(settings, popOut)

        assertTrue(controller.onCloseRequested())
        assertTrue(controller.isBackgroundActive.value)
        assertTrue(popOut.isOpen.value)
        assertEquals(1, popOut.toggleCount)
    }

    @Test
    fun popupActionDoesNotToggleAnAlreadyOpenMiniPlayer() {
        val settings = MapSettings()
        settings.putString(
            VideoAudioPreferences.KEY_MINIMIZE_ON_EXIT,
            VideoAudioPreferences.MINIMIZE_ON_EXIT_POPUP
        )
        val popOut = FakePopOutCoordinator()
        popOut.isOpen.value = true
        val controller = BackgroundPlaybackController(settings, popOut)

        assertTrue(controller.onCloseRequested())
        assertTrue(popOut.isOpen.value)
        assertEquals(0, popOut.toggleCount)
    }

    @Test
    fun unsetPreferenceDefaultsToBackground() {
        val controller = BackgroundPlaybackController(MapSettings(), FakePopOutCoordinator())
        assertEquals(CloseAction.BACKGROUND, controller.resolveCloseAction())
    }

    @Test
    fun returnToForegroundClearsBackgroundState() {
        val settings = MapSettings()
        settings.putString(
            VideoAudioPreferences.KEY_MINIMIZE_ON_EXIT,
            VideoAudioPreferences.MINIMIZE_ON_EXIT_BACKGROUND
        )
        val controller = BackgroundPlaybackController(settings, FakePopOutCoordinator())
        controller.onCloseRequested()

        controller.returnToForeground()

        assertFalse(controller.isBackgroundActive.value)
    }

    @Test
    fun setOpenOnUnsupportedPopOutIsNoOp() {
        val popOut = FakePopOutCoordinator(supported = false)
        popOut.setOpen(true)
        assertFalse(popOut.isOpen.value)
        assertEquals(0, popOut.toggleCount)
    }
}
