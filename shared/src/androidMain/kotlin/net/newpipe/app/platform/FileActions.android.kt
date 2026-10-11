/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

/** Android has no desktop-style file opener; downloads are surfaced through the system UI. */
actual fun openDownloadedFile(path: String): Boolean = false

actual fun openDownloadedFileFolder(path: String): Boolean = false

/** Android has no synchronous folder chooser in the shared UI yet. */
actual fun chooseDirectory(): String? = null
