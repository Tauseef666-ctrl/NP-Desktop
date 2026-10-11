/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JVMBackupRepositoryTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val repository = JVMBackupRepository(json)
    private val tempDirectories = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        tempDirectories.forEach { it.deleteRecursively() }
    }

    private fun tempDirectory(): File =
        Files.createTempDirectory("np-backup").toFile().also { tempDirectories += it }

    private fun manifest(createdAtEpochMs: Long = 1_700_000_000_000L) =
        BackupManifest(createdAtEpochMs = createdAtEpochMs, entries = listOf("queue.json", "playlists.json"))

    @Test
    fun createThenReadRoundTripsBytesExactly() {
        val directory = tempDirectory()
        val payload = mapOf(
            "queue.json" to byteArrayOf(1, 2, 3, 4, 5),
            "playlists.json" to "{\"playlists\":[]}".encodeToByteArray()
        )

        val path = assertNotNull(repository.createBackup(directory.absolutePath, manifest(), payload))

        assertTrue(path.endsWith("npp-backup-2023-11-14-22-13-20.nppbackup"))
        assertTrue(File(path).isFile, "a real archive must exist on disk")

        val read = repository.readBackup(path)
        assertContentEquals(payload["queue.json"], read["queue.json"])
        assertContentEquals(payload["playlists.json"], read["playlists.json"])
        assertTrue(read.containsKey(BackupOps.MANIFEST_ENTRY), "the manifest must be readable")
    }

    @Test
    fun readMissingFileReturnsEmptyMap() {
        val directory = tempDirectory()
        assertTrue(repository.readBackup(File(directory, "absent.nppbackup").absolutePath).isEmpty())
    }

    @Test
    fun readCorruptFileReturnsEmptyMap() {
        val directory = tempDirectory()
        val corrupt = File(directory, "corrupt.nppbackup").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
        }
        assertTrue(repository.readBackup(corrupt.absolutePath).isEmpty())
    }

    @Test
    fun readArchiveWithoutManifestReturnsEmptyMap() {
        val directory = tempDirectory()
        val foreign = File(directory, "foreign.nppbackup")
        ZipOutputStream(foreign.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("queue.json"))
            zip.write("{}".encodeToByteArray())
            zip.closeEntry()
        }
        assertTrue(repository.readBackup(foreign.absolutePath).isEmpty())
    }

    @Test
    fun readArchiveWithForeignManifestReturnsEmptyMap() {
        val directory = tempDirectory()
        val foreign = File(directory, "foreign.nppbackup")
        ZipOutputStream(foreign.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupOps.MANIFEST_ENTRY))
            zip.write(
                """{"appName":"NotNewPipe","schemaVersion":1,"createdAtEpochMs":1,"entries":[]}"""
                    .encodeToByteArray()
            )
            zip.closeEntry()
        }
        assertTrue(repository.readBackup(foreign.absolutePath).isEmpty())
    }

    @Test
    fun listBackupsReturnsNewestFirst() {
        val directory = tempDirectory()
        repository.createBackup(
            directory.absolutePath,
            BackupManifest(createdAtEpochMs = 1_700_000_000_000L, entries = emptyList()),
            emptyMap()
        )
        repository.createBackup(
            directory.absolutePath,
            BackupManifest(createdAtEpochMs = 1_700_000_100_000L, entries = emptyList()),
            emptyMap()
        )

        val names = repository.listBackups(directory.absolutePath)

        assertEquals(2, names.size)
        assertEquals(BackupOps.backupFileName(1_700_000_100_000L), names.first())
        assertEquals(BackupOps.backupFileName(1_700_000_000_000L), names.last())
    }

    @Test
    fun listBackupsIgnoresOtherFilesAndInvalidDirectory() {
        val directory = tempDirectory()
        File(directory, "notes.txt").writeText("ignore me")
        repository.createBackup(
            directory.absolutePath,
            BackupManifest(createdAtEpochMs = 1_700_000_000_000L, entries = emptyList()),
            emptyMap()
        )

        val names = repository.listBackups(directory.absolutePath)
        assertEquals(listOf(BackupOps.backupFileName(1_700_000_000_000L)), names)
        assertTrue(repository.listBackups(File(directory, "missing").absolutePath).isEmpty())
    }

    @Test
    fun createBackupRejectsUnusableDirectory() {
        val directory = tempDirectory()
        assertNull(
            repository.createBackup(
                File(directory, "missing").absolutePath,
                manifest(),
                emptyMap()
            )
        )
    }
}
