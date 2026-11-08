/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import java.awt.Desktop
import java.io.File

actual fun openInFileBrowser(path: String): Boolean = runCatching {
    val file = File(path)
    val target = if (file.isDirectory) file else file.parentFile ?: file
    if (!Desktop.isDesktopSupported()) return@runCatching false
    Desktop.getDesktop().open(target)
    true
}.getOrDefault(false)