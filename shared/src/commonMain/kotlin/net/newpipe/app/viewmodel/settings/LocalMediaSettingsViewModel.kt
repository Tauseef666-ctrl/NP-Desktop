/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.platform.chooseDirectory
import net.newpipe.app.player.LocalMediaScanner
import net.newpipe.app.preferences.LibraryPreferences
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the Local media settings screen: the persisted set of indexed folders, adding and
 * removing folders, and an on-demand scan that reports how many files were found while honestly
 * surfacing folders that cannot be read.
 *
 * Folder persistence reuses the exact blob written by the library
 * ([LibraryPreferences.KEY_INDEXED_FOLDERS], newline-separated) so both screens stay in sync.
 * Every list mutation and the blob codec are pure functions on the [companion object] so they can
 * be unit tested without any platform capability.
 */
@KoinViewModel
class LocalMediaSettingsViewModel(
    private val settings: Settings,
    private val scanner: LocalMediaScanner,
    private val pickFolder: suspend () -> String? = { chooseDirectory() }
) : ViewModel() {

    /** False when the platform has no folder scanner; the UI then hides folder management. */
    val isScannerAvailable: Boolean = scanner.isAvailable

    private val _folders = MutableStateFlow(loadFolders())

    /** Indexed folders, newest persisted state first. */
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _fileCount = MutableStateFlow(0)

    /** Files found by the last completed scan across every indexed folder; 0 before any scan. */
    val fileCount: StateFlow<Int> = _fileCount.asStateFlow()

    private val _hasScanned = MutableStateFlow(false)

    /** Whether a scan has completed at least once, so the UI can show a truthful count line. */
    val hasScanned: StateFlow<Boolean> = _hasScanned.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)

    /** Unreadable folders from the last scan, or null when the last scan was clean. */
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private fun loadFolders(): List<String> =
        pureDecodeFolders(
            settings.getString(
                LibraryPreferences.KEY_INDEXED_FOLDERS,
                LibraryPreferences.DEFAULT_INDEXED_FOLDERS
            )
        )

    private fun saveFolders(list: List<String>) {
        settings.putString(
            LibraryPreferences.KEY_INDEXED_FOLDERS,
            pureEncodeFolders(list)
        )
    }

    /** Ask the platform for a folder; persist it when chosen and not already indexed. */
    fun addFolder() {
        viewModelScope.launch {
            val picked = pickFolder() ?: return@launch
            val updated = pureAppendFolder(_folders.value, picked)
            if (updated != _folders.value) {
                _folders.value = updated
                saveFolders(updated)
            }
        }
    }

    /** Remove [path] from the indexed folders and persist the change. */
    fun removeFolder(path: String) {
        val updated = pureRemoveFolder(_folders.value, path)
        if (updated != _folders.value) {
            _folders.value = updated
            saveFolders(updated)
        }
    }

    /** Re-read the persisted folders (e.g. after the library screen changed them). */
    fun refresh() {
        _folders.value = loadFolders()
    }

    /** Scan every indexed folder; report the total found and any unreadable folders. */
    fun rescan() {
        if (_isScanning.value) return
        val roots = _folders.value
        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            var found = 0
            val errors = mutableListOf<String>()
            for (root in roots) {
                try {
                    found += scanner.scanFolder(root).size
                } catch (t: Throwable) {
                    errors += "$root: ${t.message ?: "unreadable"}"
                }
            }
            _fileCount.value = found
            _hasScanned.value = true
            _scanError.value = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
            _isScanning.value = false
        }
    }

    companion object {

        /** Decode the persisted blob: split, trim, drop blanks and de-duplicate keeping order. */
        fun pureDecodeFolders(blob: String): List<String> =
            blob.split(LibraryPreferences.FOLDER_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        /** Encode folders into the same newline-separated blob the library writes. */
        fun pureEncodeFolders(folders: List<String>): String =
            folders.joinToString(LibraryPreferences.FOLDER_SEPARATOR)

        /** Return [folders] with trimmed [path] appended, ignoring blanks and duplicates. */
        fun pureAppendFolder(folders: List<String>, path: String): List<String> {
            val trimmed = path.trim()
            if (trimmed.isEmpty() || trimmed in folders) return folders
            return folders + trimmed
        }

        /** Return [folders] without [path]; unchanged when it is not indexed. */
        fun pureRemoveFolder(folders: List<String>, path: String): List<String> =
            if (path in folders) folders - path else folders
    }
}
