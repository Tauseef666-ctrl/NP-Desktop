/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.annotation.Singleton

/**
 * Desktop access point between the shared Koin-scoped [FullscreenCoordinator] and the real Compose
 * window. The desktop shell (`Main.kt`) installs the window's fullscreen toggle here and mirrors
 * the window's current fullscreen state back, so the player screen button and the F11 binding can
 * drive and reflect the actual window — no platform types leak into shared code.
 */
object DesktopFullscreenBridge {
    val isActive = MutableStateFlow(false)
    var onToggle: (() -> Unit)? = null

    fun toggle() {
        onToggle?.invoke()
    }
}

@Singleton(binds = [FullscreenCoordinator::class])
class JVMFullscreenCoordinator : FullscreenCoordinator {
    override val supported: Boolean = true

    override val isActive get() = DesktopFullscreenBridge.isActive

    override fun toggle() = DesktopFullscreenBridge.toggle()
}
