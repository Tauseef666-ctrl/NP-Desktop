/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import net.newpipe.app.player.BackgroundPlaybackController
import net.newpipe.app.player.PlaybackController
import org.koin.core.annotation.Singleton

/**
 * Desktop access point between the shared Koin-scoped [PlaybackController] and the floating
 * mini-player window. The main window pushes its singleton controller here (inside the Koin
 * composition scope) and the always-on-top window consumes it — no second Koin context needed.
 *
 * [JVMPopOutCoordinator] binds the shared capability and simply mirrors this object's state.
 */
object DesktopMiniPlayerBridge {
    val isOpen = MutableStateFlow(false)
    val controller = MutableStateFlow<PlaybackController?>(null)
    val background = MutableStateFlow<BackgroundPlaybackController?>(null)

    fun toggle() {
        isOpen.value = !isOpen.value
    }
}

/** Adds an action to the top toolbar for the desktop mini player. */
@Singleton(binds = [PopOutCoordinator::class])
class JVMPopOutCoordinator : PopOutCoordinator {
    override val supported: Boolean = true

    override val isOpen get() = DesktopMiniPlayerBridge.isOpen

    override fun toggle() = DesktopMiniPlayerBridge.toggle()
}