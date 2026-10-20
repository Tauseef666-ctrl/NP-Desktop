/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform capability to switch the hosting window between windowed and fullscreen playback.
 * The desktop build toggles the real Compose window; platforms that cannot (yet) report
 * [supported] = false so the player never shows a dead fullscreen control.
 */
interface FullscreenCoordinator {
    /** Whether this platform can toggle window fullscreen right now. */
    val supported: Boolean

    /** Whether the hosting window is currently fullscreen. */
    val isActive: StateFlow<Boolean>

    /** Toggle window fullscreen. No-op when [supported] is false. */
    fun toggle()
}
