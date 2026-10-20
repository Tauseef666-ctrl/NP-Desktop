/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Singleton

/**
 * Android's fullscreen is owned by the system player activity (rotation + insets controller),
 * not by the shared Compose scaffold. Reported unsupported so the desktop-style window toggle
 * never appears as a dead control on phone/TV.
 */
@Singleton(binds = [FullscreenCoordinator::class])
class AndroidFullscreenCoordinator : FullscreenCoordinator {
    override val supported: Boolean = false

    private val inactive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> get() = inactive

    override fun toggle() = Unit
}
