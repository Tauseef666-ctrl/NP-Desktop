/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

/**
 * Opens the given path in the platform file browser, if supported.
 */
expect fun openInFileBrowser(path: String): Boolean