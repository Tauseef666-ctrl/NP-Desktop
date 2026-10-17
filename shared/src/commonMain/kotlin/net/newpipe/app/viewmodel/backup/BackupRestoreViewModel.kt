/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.backup

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.backup.BackupContent
import net.newpipe.app.backup.BackupManifest
import net.newpipe.app.backup.BackupOps
import net.newpipe.app.backup.BackupRepository
import org.koin.core.annotation.KoinViewModel

/** One-shot status surfaced to the user after an action. */
sealed interface BackupStatus {
    /** A real archive named [fileName] was written to the chosen folder. */
    data class Created(val fileName: String) : BackupStatus

    /** The archive could not be written. */
    data object CreateFailed : BackupStatus

    /** The archive was fully validated and written back to local storage. */
    data object RestoreComplete : BackupStatus

    /** The archive was read but could not be applied; local data is untouched. */
    data object RestoreFailed : BackupStatus

    /** The archive is passphrase-encrypted and the supplied passphrase was missing or wrong. */
    data object RestoreWrongPassphrase : BackupStatus
}

/** Names and byte counts of a single backup's payload, for the "View contents" dialog. */
data class BackupContents(
    val fileName: String,
    val entries: List<Pair<String, Long>>
)

/**
 * Drives the Backup & Restore center. State is derived only from real files and stores — the view
 * never receives fabricated progress or counts. Restore is non-destructive: the archive is fully read
 * and validated before any store is touched.
 */
@KoinViewModel
class BackupRestoreViewModel(
    private val repository: BackupRepository,
    private val content: BackupContent
) : ViewModel() {

    private val _directory = MutableStateFlow<String?>(null)
    val directory: StateFlow<String?> = _directory.asStateFlow()

    private val _backups = MutableStateFlow<List<String>>(emptyList())
    val backups: StateFlow<List<String>> = _backups.asStateFlow()

    private val _lastBackupName = MutableStateFlow<String?>(null)
    val lastBackupName: StateFlow<String?> = _lastBackupName.asStateFlow()

    private val _status = MutableStateFlow<BackupStatus?>(null)
    val status: StateFlow<BackupStatus?> = _status.asStateFlow()

    private val _contents = MutableStateFlow<BackupContents?>(null)
    val contents: StateFlow<BackupContents?> = _contents.asStateFlow()

    private val _pendingRestore = MutableStateFlow<String?>(null)
    val pendingRestore: StateFlow<String?> = _pendingRestore.asStateFlow()

    private val _invalid = MutableStateFlow(false)
    val invalid: StateFlow<Boolean> = _invalid.asStateFlow()

    private val _encryptedLocked = MutableStateFlow(false)
    val encryptedLocked: StateFlow<Boolean> = _encryptedLocked.asStateFlow()

    /** Whether the current platform can actually create and read archives. */
    val isAvailable: Boolean get() = repository.isAvailable

    /** Records the folder picked by the platform chooser and lists its backups. */
    fun chooseFolder(path: String?) {
        if (path.isNullOrBlank()) return
        _directory.value = path
        refresh()
    }

    /** Re-reads the chosen folder so newly created backups appear immediately. */
    fun refresh() {
        val directory = _directory.value ?: return
        _backups.value = repository.listBackups(directory)
    }

    /**
     * Packs every readable store blob and writes a real archive into the chosen folder. A non-blank
     * [passphrase] encrypts the whole archive with a standard AEAD; blank keeps it a plain ZIP.
     */
    fun createBackup(passphrase: String? = null) {
        val directory = _directory.value
        if (directory.isNullOrBlank() || !repository.isAvailable) {
            _status.value = BackupStatus.CreateFailed
            return
        }
        val nowEpochMs = System.currentTimeMillis()
        val entries = content.collectEntries()
        val manifest = BackupManifest(
            createdAtEpochMs = nowEpochMs,
            entries = entries.keys.toList()
        )
        val path = repository.createBackup(directory, manifest, entries, passphrase)
        if (path == null) {
            _status.value = BackupStatus.CreateFailed
            return
        }
        val fileName = BackupOps.backupFileName(nowEpochMs)
        _lastBackupName.value = fileName
        _status.value = BackupStatus.Created(fileName)
        refresh()
    }

    /**
     * Reads a listed backup and exposes its payload names/byte counts. Encrypted archives are
     * decrypted with [passphrase]; a missing/wrong one raises the honest locked signal instead of a
     * generic "invalid archive".
     */
    fun viewContents(fileName: String, passphrase: String? = null) {
        val directory = _directory.value ?: return
        val path = resolvePath(directory, fileName)
        val entries = repository.readBackup(path, passphrase)
        val manifest = manifestOf(entries)
        if (manifest == null) {
            if (repository.isEncrypted(path)) _encryptedLocked.value = true else _invalid.value = true
            return
        }
        val payload = entries.filterKeys { it != BackupOps.MANIFEST_ENTRY }
        val byteCounts = manifest.entries.map { payload[it]?.size ?: 0 }
        _contents.value = BackupContents(
            fileName = fileName,
            entries = BackupOps.describeEntries(manifest, byteCounts)
        )
    }

    fun dismissContents() {
        _contents.value = null
    }

    /** Opens the restore confirmation for [fileName]; nothing is written until confirmed. */
    fun requestRestore(fileName: String) {
        _pendingRestore.value = fileName
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    /**
     * Reads the archive fully, validates it, then writes every blob back through the stores.
     * Encrypted archives are decrypted with [passphrase]; a missing/wrong one is reported distinctly
     * and leaves local data untouched.
     */
    fun confirmRestore(passphrase: String? = null) {
        val directory = _directory.value
        val fileName = _pendingRestore.value
        _pendingRestore.value = null
        if (directory.isNullOrBlank() || fileName.isNullOrBlank()) {
            _status.value = BackupStatus.RestoreFailed
            return
        }
        val path = resolvePath(directory, fileName)
        val entries = repository.readBackup(path, passphrase)
        if (manifestOf(entries) == null) {
            _status.value = if (repository.isEncrypted(path)) {
                BackupStatus.RestoreWrongPassphrase
            } else {
                BackupStatus.RestoreFailed
            }
            return
        }
        val payload = entries.filterKeys { it != BackupOps.MANIFEST_ENTRY }
        _status.value = if (content.restoreEntries(payload)) {
            BackupStatus.RestoreComplete
        } else {
            BackupStatus.RestoreFailed
        }
    }

    fun dismissInvalid() {
        _invalid.value = false
    }

    fun dismissEncryptedLocked() {
        _encryptedLocked.value = false
    }

    /** Clears the one-shot status once the UI has shown it. */
    fun consumeStatus() {
        _status.value = null
    }

    private fun manifestOf(entries: Map<String, ByteArray>): BackupManifest? {
        val manifestBytes = entries[BackupOps.MANIFEST_ENTRY] ?: return null
        return BackupOps.validateBackup(manifestBytes.decodeToString())
    }

    private fun resolvePath(directory: String, fileName: String): String {
        val separator = if (directory.endsWith("/") || directory.endsWith("\\")) "" else "/"
        return directory + separator + fileName
    }
}
