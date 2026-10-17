/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import net.newpipe.app.backup.BackupManifest
import net.newpipe.app.backup.BackupRepository
import org.koin.core.annotation.Singleton

/**
 * Android has no shared Backup & Restore implementation yet — the feature is desktop-only for this
 * milestone. This binding keeps the shared Koin graph complete on the Android target and reports
 * honestly as unavailable: no archive is ever written, reads always come back empty and the UI must
 * not offer backup creation while [isAvailable] is false. Nothing is faked.
 */
@Singleton(binds = [BackupRepository::class])
class AndroidBackupRepository : BackupRepository {

    override val isAvailable: Boolean = false

    override fun createBackup(
        directory: String,
        manifest: BackupManifest,
        entries: Map<String, ByteArray>,
        passphrase: String?
    ): String? = null

    override fun readBackup(filePath: String, passphrase: String?): Map<String, ByteArray> = emptyMap()

    override fun listBackups(directory: String): List<String> = emptyList()
}
