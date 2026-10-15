/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.platform.PopOutCoordinator
import net.newpipe.app.preferences.VideoAudioPreferences
import org.koin.core.annotation.Singleton

/**
 * Applies the user's `minimize_on_exit` preference to real window behaviour (F2). The desktop host
 * calls [onCloseRequested] when the main window is closed and the platform mini player is driven
 * through [PopOutCoordinator]; the app never invents an action the platform cannot honour.
 *
 * Background playback itself needs no engine change: the shared playback engine keeps decoding
 * audio while its video surface is not visible, so keeping the app alive is enough.
 */
@Singleton
class BackgroundPlaybackController(
    private val settings: Settings,
    private val popOut: PopOutCoordinator
) {

    private val _isBackgroundActive = MutableStateFlow(false)

    /** True while playback is meant to continue with the main window hidden/minimised. */
    val isBackgroundActive: StateFlow<Boolean> = _isBackgroundActive.asStateFlow()

    /** The action the stored preference maps to right now (read fresh, never cached). */
    fun resolveCloseAction(): CloseAction = closeActionFor(
        settings.getString(
            VideoAudioPreferences.KEY_MINIMIZE_ON_EXIT,
            VideoAudioPreferences.DEFAULT_MINIMIZE_ON_EXIT
        )
    )

    /**
     * Apply the resolved close action to the app.
     *
     * @return `true` when the app must keep running (window should be hidden/minimised) and `false`
     *   when it should actually exit.
     */
    fun onCloseRequested(): Boolean = when (resolveCloseAction()) {
        CloseAction.STOP -> {
            _isBackgroundActive.value = false
            false
        }
        CloseAction.BACKGROUND -> {
            _isBackgroundActive.value = true
            true
        }
        CloseAction.POPUP -> {
            _isBackgroundActive.value = true
            popOut.setOpen(true)
            true
        }
    }

    /** Called when the main window is shown/hidden again, so the UI can drop its background hint. */
    fun returnToForeground() {
        _isBackgroundActive.value = false
    }
}
