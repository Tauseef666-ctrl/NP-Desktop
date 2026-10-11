/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.newpipe.app.backup.BackupOps.FILE_SUFFIX
import net.newpipe.app.backup.BackupOps.MANIFEST_ENTRY
import org.koin.core.annotation.Singleton
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Desktop [BackupRepository] backed by `java.util.zip`.
 *
 * An archive is a plain ZIP whose first entry is the JSON [BackupManifest], followed by each payload
 * blob. [readBackup] parses and validates that manifest before exposing any blob, and every failure
 * (missing file, not a ZIP, foreign manifest) resolves to an empty map instead of throwing — the
 * caller's restore flow therefore never partially applies a bad archive.
 */
@Singleton(binds = [BackupRepository::class])
class JVMBackupRepository(
    private val json: Json
) : BackupRepository {

    override val isAvailable: Boolean = true

    override fun createBackup(
        directory: String,
        manifest: BackupManifest,
        entries: Map<String, ByteArray>
    ): String? {
        if (directory.isBlank()) return null
        val targetDirectory = File(directory)
        if (!targetDirectory.isDirectory) return null
        val target = File(targetDirectory, BackupOps.backupFileName(manifest.createdAtEpochMs))
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
                zip.writeEntry(MANIFEST_ENTRY, json.encodeToString(manifest).encodeToByteArray())
                entries.forEach { (name, bytes) -> zip.writeEntry(name, bytes) }
            }
            target.absolutePath
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    override fun readBackup(filePath: String): Map<String, ByteArray> {
        if (filePath.isBlank()) return emptyMap()
        val file = File(filePath)
        if (!file.isFile) return emptyMap()
        return try {
            val entries = LinkedHashMap<String, ByteArray>()
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val manifestBytes = entries[MANIFEST_ENTRY] ?: return emptyMap()
            if (BackupOps.validateBackup(manifestBytes.decodeToString()) == null) return emptyMap()
            entries
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override fun listBackups(directory: String): List<String> {
        if (directory.isBlank()) return emptyList()
        val folder = File(directory)
        if (!folder.isDirectory) return emptyList()
        return folder.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
            ?.map { it.name }
            ?.sortedDescending()
            ?: emptyList()
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}
