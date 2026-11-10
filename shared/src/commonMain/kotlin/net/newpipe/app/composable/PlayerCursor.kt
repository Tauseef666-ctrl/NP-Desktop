/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.ui.Modifier

/**
 * Controls the mouse pointer over the player.
 *
 * The desktop implementation uses an invisible custom cursor: hiding the pointer this way goes
 * through the Compose window machinery itself, so Compose can never override it with the default
 * cursor while the mouse rests over the video. The other platforms have no hover pointer, so
 * they ignore the request.
 *
 * @param hidden whether the pointer should currently be invisible over this area, for example
 * over a fullscreen player whose controls have auto hidden
 */
expect fun Modifier.playerCursor(hidden: Boolean): Modifier
