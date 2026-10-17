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
     * name derived from [manifest]. When [passphrase] is non-blank the whole archive is encrypted
     * with a standard AEAD (see [BackupEnvelope]); otherwise it is written as a plain ZIP. Returns
     * the full archive path, or `null` when the directory is unusable or writing fails. Never throws.
     */
    fun createBackup(
        directory: String,
        manifest: BackupManifest,
        entries: Map<String, ByteArray>,
        passphrase: String? = null
    ): String?

    /**
     * Reads every archive entry, including the manifest, keyed by entry name. Encrypted archives are
     * decrypted with [passphrase] first; a missing/wrong passphrase, a missing file, a corrupt
     * archive or an invalid/foreign manifest all resolve to an empty map. Never throws.
     */
    fun readBackup(filePath: String, passphrase: String? = null): Map<String, ByteArray>

    /**
     * True when [filePath] is an encrypted archive (its [BackupEnvelope.MAGIC] prefix is present), so
     * the UI can ask for a passphrase before attempting a read. `false` for missing/unreadable files.
     */
    fun isEncrypted(filePath: String): Boolean = false

    /**
     * Lists the backup file names in [directory], newest first, or an empty list when [directory] is
     * not a readable directory.
     */
    fun listBackups(directory: String): List<String>
}
