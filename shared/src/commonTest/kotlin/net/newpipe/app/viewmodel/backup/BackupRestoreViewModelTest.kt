/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.backup

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.newpipe.app.backup.BackupContent
import net.newpipe.app.backup.BackupManifest
import net.newpipe.app.backup.BackupOps
import net.newpipe.app.backup.BackupRepository
import net.newpipe.app.download.DownloadStore
import net.newpipe.app.player.PlayQueueStore
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.PlaylistStore
import net.newpipe.app.player.RecentlyPlayedStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupRestoreViewModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun content(settings: MapSettings) = BackupContent(
        queueStore = PlayQueueStore(settings, json),
        playlistStore = PlaylistStore(settings, json),
        resumeStore = PlaybackResumeStore(settings),
        recentlyPlayedStore = RecentlyPlayedStore(settings, json),
        downloadStore = DownloadStore(settings, json),
        json = json
    )

    private fun manifestJson(entries: List<String> = listOf("queue.json")) =
        json.encodeToString(BackupManifest(createdAtEpochMs = 1L, entries = entries))

    private class FakeRepository(
        override val isAvailable: Boolean = true,
        var encrypted: Boolean = false,
        var readResult: Map<String, ByteArray> = emptyMap(),
        var createResult: String? = "dir/npp-backup.nppbackup"
    ) : BackupRepository {
        var lastCreatePassphrase: String? = null
        var lastReadPassphrase: String? = null

        override fun createBackup(
            directory: String,
            manifest: BackupManifest,
            entries: Map<String, ByteArray>,
            passphrase: String?
        ): String? {
            lastCreatePassphrase = passphrase
            return createResult
        }

        override fun readBackup(filePath: String, passphrase: String?): Map<String, ByteArray> {
            lastReadPassphrase = passphrase
            return readResult
        }

        override fun isEncrypted(filePath: String): Boolean = encrypted

        override fun listBackups(directory: String): List<String> = emptyList()
    }

    private fun viewModel(repository: FakeRepository) =
        BackupRestoreViewModel(repository, content(MapSettings())).apply {
            chooseFolder("/backups")
        }

    @Test
    fun createBackupForwardsPassphraseToRepository() {
        val repository = FakeRepository()
        val model = viewModel(repository)

        model.createBackup("hunter2")

        assertEquals("hunter2", repository.lastCreatePassphrase)
        assertTrue(model.status.value is BackupStatus.Created)
    }

    @Test
    fun createBackupWithoutPassphrasePassesNull() {
        val repository = FakeRepository()
        val model = viewModel(repository)

        model.createBackup()

        assertNull(repository.lastCreatePassphrase)
    }

    @Test
    fun createBackupFailsWhenRepositoryCannotWrite() {
        val repository = FakeRepository(createResult = null)
        val model = viewModel(repository)

        model.createBackup("pw")

        assertEquals(BackupStatus.CreateFailed, model.status.value)
    }

    @Test
    fun confirmRestoreReportsWrongPassphraseForEncryptedArchive() {
        val repository = FakeRepository(encrypted = true, readResult = emptyMap())
        val model = viewModel(repository)

        model.requestRestore("enc.nppbackup")
        model.confirmRestore("bad")

        assertEquals(BackupStatus.RestoreWrongPassphrase, model.status.value)
        assertEquals("bad", repository.lastReadPassphrase)
    }

    @Test
    fun confirmRestoreReportsGenericFailureForPlainBadArchive() {
        val repository = FakeRepository(encrypted = false, readResult = emptyMap())
        val model = viewModel(repository)

        model.requestRestore("broken.nppbackup")
        model.confirmRestore()

        assertEquals(BackupStatus.RestoreFailed, model.status.value)
    }

    @Test
    fun confirmRestoreSucceedsWithValidManifest() {
        val read = mapOf(BackupOps.MANIFEST_ENTRY to manifestJson().encodeToByteArray())
        val repository = FakeRepository(readResult = read)
        val model = viewModel(repository)

        model.requestRestore("good.nppbackup")
        model.confirmRestore()

        assertEquals(BackupStatus.RestoreComplete, model.status.value)
    }

    @Test
    fun viewContentsLocksEncryptedArchiveItCannotRead() {
        val repository = FakeRepository(encrypted = true, readResult = emptyMap())
        val model = viewModel(repository)

        model.viewContents("enc.nppbackup")

        assertTrue(model.encryptedLocked.value)
        assertFalse(model.invalid.value)
        assertNull(model.contents.value)
    }

    @Test
    fun viewContentsFlagsPlainInvalidArchive() {
        val repository = FakeRepository(encrypted = false, readResult = emptyMap())
        val model = viewModel(repository)

        model.viewContents("broken.nppbackup")

        assertTrue(model.invalid.value)
        assertFalse(model.encryptedLocked.value)
    }

    @Test
    fun viewContentsExposesEntriesForReadableArchive() {
        val read = mapOf(
            BackupOps.MANIFEST_ENTRY to manifestJson(entries = listOf("queue.json")).encodeToByteArray(),
            "queue.json" to byteArrayOf(1, 2, 3)
        )
        val repository = FakeRepository(readResult = read)
        val model = viewModel(repository)

        model.viewContents("good.nppbackup")

        val contents = assertNotNull(model.contents.value)
        assertEquals("good.nppbackup", contents.fileName)
        assertEquals(3L, contents.entries.single().second)
    }

    @Test
    fun dismissEncryptedLockedClearsTheFlag() {
        val repository = FakeRepository(encrypted = true, readResult = emptyMap())
        val model = viewModel(repository)

        model.viewContents("enc.nppbackup")
        model.dismissEncryptedLocked()

        assertFalse(model.encryptedLocked.value)
    }
}
