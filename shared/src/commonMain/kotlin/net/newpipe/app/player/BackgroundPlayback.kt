/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import net.newpipe.app.preferences.VideoAudioPreferences

/**
 * What the host should do when the user closes the main window, derived from the stored
 * `minimize_on_exit` preference.
 *
 * - [STOP] requests the app to exit; playback ends with it.
 * - [BACKGROUND] keeps the app alive with audio still playing while the window is hidden
 *   (background playback).
 * - [POPUP] keeps playing and asks the platform for its floating mini player (picture-in-picture).
 */
enum class CloseAction { STOP, BACKGROUND, POPUP }

/**
 * Resolve the close action from a raw `minimize_on_exit` preference value. Unknown, blank and
 * `null` values fall back to [CloseAction.BACKGROUND], matching
 * [VideoAudioPreferences.DEFAULT_MINIMIZE_ON_EXIT], so the honest default keeps audio playing.
 */
fun closeActionFor(minimizeOnExit: String?): CloseAction = when (minimizeOnExit?.trim()) {
    VideoAudioPreferences.MINIMIZE_ON_EXIT_NONE -> CloseAction.STOP
    VideoAudioPreferences.MINIMIZE_ON_EXIT_POPUP -> CloseAction.POPUP
    VideoAudioPreferences.MINIMIZE_ON_EXIT_BACKGROUND -> CloseAction.BACKGROUND
    else -> CloseAction.BACKGROUND
}
