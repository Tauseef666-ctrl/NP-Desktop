/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

/**
 * Applies the platform's dark title bar treatment to the top-level window with the given title,
 * so the window chrome matches the black design language of the app instead of the bright
 * system default. The title is needed because the window is looked up by name on the desktop
 * target; a blank title returns `false` without doing anything.
 *
 * @return whether the treatment was applied (never throws)
 */
expect fun applyDarkTitlebar(windowTitle: String): Boolean
