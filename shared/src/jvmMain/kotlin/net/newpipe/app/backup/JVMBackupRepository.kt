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
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Desktop [BackupRepository] backed by `java.util.zip`.
 *
 * A plain archive is a ZIP whose first entry is the JSON [BackupManifest], followed by each payload
 * blob. When a passphrase is supplied the whole ZIP is wrapped in a [BackupEnvelope] encrypted by
 * [JVMBackupCipher]; [readBackup] transparently detects that prefix and decrypts before unzipping.
 * [readBackup] parses and validates the manifest before exposing any blob, and every failure
 * (missing file, not a ZIP, wrong passphrase, foreign manifest) resolves to an empty map instead of
 * throwing — the caller's restore flow therefore never partially applies a bad archive.
 */
@Singleton(binds = [BackupRepository::class])
class JVMBackupRepository(
    private val json: Json,
    private val cipher: JVMBackupCipher
) : BackupRepository {

    override val isAvailable: Boolean = true

    override fun createBackup(
        directory: String,
        manifest: BackupManifest,
        entries: Map<String, ByteArray>,
        passphrase: String?
    ): String? {
        if (directory.isBlank()) return null
        val targetDirectory = File(directory)
        if (!targetDirectory.isDirectory) return null
        val target = File(targetDirectory, BackupOps.backupFileName(manifest.createdAtEpochMs))
        return try {
            val zipBytes = ByteArrayOutputStream().use { buffer ->
                ZipOutputStream(BufferedOutputStream(buffer)).use { zip ->
                    zip.writeEntry(MANIFEST_ENTRY, json.encodeToString(manifest).encodeToByteArray())
                    entries.forEach { (name, bytes) -> zip.writeEntry(name, bytes) }
                }
                buffer.toByteArray()
            }
            val archive = if (passphrase.isNullOrEmpty()) {
                zipBytes
            } else {
                cipher.encrypt(zipBytes, passphrase) ?: return null
            }
            target.writeBytes(archive)
            target.absolutePath
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    override fun readBackup(filePath: String, passphrase: String?): Map<String, ByteArray> {
        if (filePath.isBlank()) return emptyMap()
        val file = File(filePath)
        if (!file.isFile) return emptyMap()
        return try {
            val stored = file.readBytes()
            val zipBytes = if (BackupEnvelope.isEncrypted(stored)) {
                cipher.decrypt(stored, passphrase ?: "") ?: return emptyMap()
            } else {
                stored
            }
            val entries = LinkedHashMap<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
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

    override fun isEncrypted(filePath: String): Boolean {
        if (filePath.isBlank()) return false
        val file = File(filePath)
        if (!file.isFile) return false
        return try {
            val prefix = ByteArray(BackupEnvelope.MAGIC.length)
            file.inputStream().use { stream ->
                var read = 0
                while (read < prefix.size) {
                    val count = stream.read(prefix, read, prefix.size - read)
                    if (count < 0) break
                    read += count
                }
            }
            BackupEnvelope.isEncrypted(prefix)
        } catch (_: Exception) {
            false
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
