/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * Downloads are not supported on iOS in the shared desktop build yet, so there is no
 * download folder to synchronize.
 */
actual suspend fun synchronizeDownloadLibrary(repository: DownloadRepository): Int = 0
