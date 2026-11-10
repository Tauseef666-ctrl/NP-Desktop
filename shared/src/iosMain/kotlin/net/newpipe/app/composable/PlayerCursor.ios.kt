/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.ui.Modifier

/**
 * iOS has no hover mouse pointer, so there is nothing to hide.
 */
actual fun Modifier.playerCursor(hidden: Boolean): Modifier = this
