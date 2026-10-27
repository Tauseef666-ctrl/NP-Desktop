/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android handles the context menu with long presses through [videoContextMenu]; no bridge is
 * needed for raw secondary clicks.
 */
@Composable
internal actual fun Modifier.secondaryClickBridge(onSecondary: () -> Unit): Modifier = this
