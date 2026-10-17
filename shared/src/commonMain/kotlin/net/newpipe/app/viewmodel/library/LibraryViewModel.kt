/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.download.DownloadEngine
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.player.LocalMediaFile
import net.newpipe.app.player.LocalMediaScanner
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackResumeStore
import net.newpipe.app.player.RecentlyPlayedEntry
import net.newpipe.app.player.RecentlyPlayedStore
import net.newpipe.app.player.ResumeEntry
import net.newpipe.app.player.SavedPlaylist
import net.newpipe.app.player.isUnderFolder
import net.newpipe.app.player.reconcileScanned
import net.newpipe.app.preferences.LibraryPreferences
import net.newpipe.app.screen.library.DerivedViewItem
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the local-media library: the set of indexed folders (persisted), folder scanning via the
 * platform [LocalMediaScanner], and hand-off of a scanned list to the shared [PlaybackController].
 *
 * It also exposes the raw inputs for the three derived views: [recentEntries] (newest first, live),
 * [resumeEntries] (Continue watching inputs) and [downloadTasks] (Downloaded inputs).
 */
@KoinViewModel
class LibraryViewModel(
    private val scanner: LocalMediaScanner,
    private val settings: Settings,
    private val playback: PlaybackController,
    private val resumeStore: PlaybackResumeStore,
    recentlyPlayedStore: RecentlyPlayedStore,
    downloadEngine: DownloadEngine
) : ViewModel() {

    val isScannerAvailable: Boolean = scanner.isAvailable

    /** Newest-first playback history; emits whenever something new starts playing. */
    val recentEntries: StateFlow<List<RecentlyPlayedEntry>> = recentlyPlayedStore.entries

    /** Stored resume positions, refreshed on load and whenever the history changes. */
    val resumeEntries: StateFlow<List<ResumeEntry>>
        field = MutableStateFlow(resumeStore.entries())

    /** Live download missions; the Downloaded view filters these to completed ones. */
    val downloadTasks: StateFlow<List<DownloadTask>> = downloadEngine.tasks

    /** Saved playlists from playback controller (read-only). */
    val savedPlaylists: List<SavedPlaylist>
        get() = playback.savedPlaylists.value

    /** Re-read the (non-observable) resume store, e.g. when the library screen is shown. */
    fun refreshResumeEntries() {
        resumeEntries.value = resumeStore.entries()
    }

    val folders: StateFlow<List<String>>
        field = MutableStateFlow(loadFolders())

    val items: StateFlow<List<LocalMediaFile>>
        field = MutableStateFlow(emptyList())

    val isScanning: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val scanError: StateFlow<String?>
        field = MutableStateFlow(null)

    private fun loadFolders(): List<String> =
        settings.getString(
            LibraryPreferences.KEY_INDEXED_FOLDERS,
            LibraryPreferences.DEFAULT_INDEXED_FOLDERS
        )
            .split(LibraryPreferences.FOLDER_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun saveFolders(list: List<String>) {
        settings.putString(
            LibraryPreferences.KEY_INDEXED_FOLDERS,
            list.joinToString(LibraryPreferences.FOLDER_SEPARATOR)
        )
    }

    fun addFolder(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed in folders.value) return
        folders.value = folders.value + trimmed
        saveFolders(folders.value)
    }

    fun removeFolder(path: String) {
        if (path !in folders.value) return
        folders.value = folders.value - path
        saveFolders(folders.value)
        items.value = items.value.filterNot { isUnderFolder(it.path, path) }
    }

    /** Re-scan every indexed folder. Unreadable folders are reported, not silently dropped. */
    fun rescan() {
        val roots = folders.value
        viewModelScope.launch {
            isScanning.value = true
            scanError.value = null
            val collected = mutableListOf<LocalMediaFile>()
            val scanned = mutableListOf<String>()
            val errors = mutableListOf<String>()
            for (root in roots) {
                try {
                    collected += scanner.scanFolder(root)
                    scanned += root
                } catch (t: Throwable) {
                    errors += "$root: ${t.message ?: "unreadable"}"
                }
            }
            items.value = reconcileScanned(
                previous = items.value,
                current = collected,
                scannedFolders = scanned
            )
            scanError.value = errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
            isScanning.value = false
        }
    }

    /** Queue the whole library (starting at [item]) on the shared controller and play it. */
    fun play(item: LocalMediaFile) {
        if (item.missing) return
        val current = items.value.filterNot { it.missing }
        val index = current.indexOf(item)
        if (index < 0) return
        viewModelScope.launch {
            playback.setQueue(current.map { it.toMediaItem() }, index)
        }
    }

    /**
     * Play one derived-view item (Recently played / Continue watching / Downloaded). Continue
     * watching resumes at the stored position; everything else starts from the beginning.
     */
    fun playDerived(item: DerivedViewItem) {
        viewModelScope.launch {
            if (item.url.isNotBlank()) {
                playback.playItem(
                    MediaItem(
                        id = item.id,
                        title = item.title,
                        url = item.url,
                        resumePositionMs = item.playablePositionMs
                    )
                )
            }
        }
    }
}
