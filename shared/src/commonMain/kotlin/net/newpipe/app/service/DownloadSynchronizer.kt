/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

/**
 * Detects the media files that are already lying in the platform download folder and registers
 * them in the [DownloadRepository], so every video that was saved by this or an earlier version
 * of the app shows up in the downloads section again.
 *
 * Only registered entries are kept from earlier sessions, so files that were removed from the
 * library but not from disk are imported again here.
 *
 * @return how many videos were newly recognised
 */
expect suspend fun synchronizeDownloadLibrary(repository: DownloadRepository): Int
