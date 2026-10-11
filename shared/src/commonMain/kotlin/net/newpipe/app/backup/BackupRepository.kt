/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

/**
 * Platform seam for reading and writing backup archives. The JVM target provides the real
 * [java.util.zip]-based implementation; other targets may report [isAvailable] `false` and refuse
 * every operation rather than fabricate data.
 */
interface BackupRepository {

    /** Whether this platform can actually create and read archives. */
    val isAvailable: Boolean

    /**
     * Writes [entries] (plus the [BackupOps.MANIFEST_ENTRY] header) into [directory] under a
     * name derived from [manifest]. Returns the full archive path, or `null` when the directory is
     * unusable or writing fails. Never throws.
     */
    fun createBackup(
        directory: String,
        manifest: BackupManifest,
        entries: Map<String, ByteArray>
    ): String?

    /**
     * Reads every archive entry, including the manifest, keyed by entry name. Returns an empty map
     * for a missing file, a corrupt archive or an invalid/foreign manifest. Never throws.
     */
    fun readBackup(filePath: String): Map<String, ByteArray>

    /**
     * Lists the backup file names in [directory], newest first, or an empty list when [directory] is
     * not a readable directory.
     */
    fun listBackups(directory: String): List<String>
}
