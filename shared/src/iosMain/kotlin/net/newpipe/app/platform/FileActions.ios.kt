/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

/** iOS has no desktop-style file opener exposed yet. */
actual fun openDownloadedFile(path: String): Boolean = false

actual fun openDownloadedFileFolder(path: String): Boolean = false

/** iOS has no shared folder chooser yet. */
actual fun chooseDirectory(): String? = null
