/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform bridge that turns raw secondary clicks into [onSecondary] callbacks for the
 * surrounding area. Only the desktop rebuild forwards the events; touch platforms handle the
 * context menu with the normal pointer pipeline of [videoContextMenu].
 */
@Composable
internal expect fun Modifier.secondaryClickBridge(onSecondary: () -> Unit): Modifier
