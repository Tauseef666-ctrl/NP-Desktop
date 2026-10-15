/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform capability to pop playback out of the main window into a compact, always-on-top
 * floating controller. The desktop build surfaces a real floating window; platforms that cannot
 * (yet) host one report [supported] = false so the UI never shows a dead control.
 */
interface PopOutCoordinator {
    /** Whether this platform can host a floating mini player right now. */
    val supported: Boolean

    /** Current open state of the platform mini player. */
    val isOpen: StateFlow<Boolean>

    /** Open/close the platform mini player. No-op when [supported] is false. */
    fun toggle()

    /**
     * Deterministically open ([open] = true) or close ([open] = false) the mini player. The default
     * only calls [toggle] when the state actually differs, so callers never flip an already-open
     * player closed by mistake. No-op when [supported] is false.
     */
    fun setOpen(open: Boolean) {
        if (supported && isOpen.value != open) toggle()
    }
}