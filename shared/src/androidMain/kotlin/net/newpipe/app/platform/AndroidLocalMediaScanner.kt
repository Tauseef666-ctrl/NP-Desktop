/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import net.newpipe.app.player.LocalMediaFile
import net.newpipe.app.player.LocalMediaScanner
import org.koin.core.annotation.Singleton

/**
 * Android has no shared folder scanner yet — the `:app` local-media flow is still the Android
 * reference implementation. This binding keeps the shared Koin graph complete on Android and reports
 * honestly as unavailable: [scanFolder] never fabricates results, so callers must gate on
 * [isAvailable] (the library UI already surfaces scanner unavailability).
 */
@Singleton(binds = [LocalMediaScanner::class])
class AndroidLocalMediaScanner : LocalMediaScanner {

    override val isAvailable: Boolean = false

    override suspend fun scanFolder(folderPath: String): List<LocalMediaFile> = emptyList()
}