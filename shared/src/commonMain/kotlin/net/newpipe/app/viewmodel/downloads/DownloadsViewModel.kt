/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.newpipe.app.download.DownloadEngine
import net.newpipe.app.download.DownloadFormat
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadStore
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.download.formatLabel
import net.newpipe.app.download.isDuplicate
import net.newpipe.app.download.subtitleCompanionRequest
import net.newpipe.app.download.reorder as reorderTasks
import net.newpipe.app.platform.defaultDownloadDirectory
import net.newpipe.app.preferences.DownloadsPreferences
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the downloads screen against the shared [DownloadEngine]. The engine owns all missions and
 * performs real transfers; this ViewModel only forwards user actions and derives display data.
 *
 * Mission ordering is a UI concern persisted in [DownloadStore] (separate key) and overlaid on the
 * engine's insertion-ordered list, so reordering never disturbs the running transfers.
 */
@KoinViewModel
class DownloadsViewModel(
    private val engine: DownloadEngine,
    private val store: DownloadStore,
    private val settings: Settings
) : ViewModel() {

    val isEngineAvailable: Boolean = engine.isAvailable

    private val _defaultDirectory = MutableStateFlow(
        settings
            .getString(
                DownloadsPreferences.KEY_DOWNLOAD_DIRECTORY,
                DownloadsPreferences.DEFAULT_DOWNLOAD_DIRECTORY
            )
            .ifBlank { defaultDownloadDirectory() }
    )

    /**
     * Effective destination folder for new downloads: the persisted override when set, otherwise the
     * platform default. Updated by [setDefaultDirectory] so the downloads screen reflects changes
     * made on the settings screen without a restart.
     */
    val defaultDirectory: StateFlow<String> = _defaultDirectory.asStateFlow()

    /** Persist the preferred download directory; a blank [path] resets to the platform default. */
    fun setDefaultDirectory(path: String) {
        val trimmed = path.trim()
        settings.putString(DownloadsPreferences.KEY_DOWNLOAD_DIRECTORY, trimmed)
        _defaultDirectory.value = trimmed.ifBlank { defaultDownloadDirectory() }
    }

    private val persistedOrder = MutableStateFlow(store.loadOrder())

    /** Missions in engine order with the user's saved/reordered order applied on top. */
    val tasks: StateFlow<List<DownloadTask>> =
        combine(engine.tasks, persistedOrder) { list, order -> applyOrder(list, order) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = applyOrder(engine.tasks.value, persistedOrder.value)
            )

    private val _lastAddRejectedDuplicate = MutableStateFlow(false)

    /** True when the most recent add attempt was rejected because the URL was already active. */
    val lastAddRejectedDuplicate: StateFlow<Boolean> = _lastAddRejectedDuplicate.asStateFlow()

    /** Builder for a destination path: [directory]/[name], kept under the given folder. */
    fun destinationIn(directory: String, fileName: String): String {
        val clean = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        if (clean.isEmpty()) return ""
        val separator = if (directory.contains('\\')) "\\" else "/"
        return directory.trimEnd('\\', '/') + separator + clean
    }

    /** Name derived from a URL (empty when none can be derived). */
    fun fileNameFor(url: String): String {
        val name = url.substringBefore('?').substringAfterLast('/').substringAfterLast('\\').trim()
        return if (name.isEmpty()) name else name
    }

    /** True when [url] is already active (queued/downloading/paused) in the mission list. */
    fun isDuplicateUrl(url: String): Boolean = isDuplicate(engine.tasks.value, url)

    /**
     * Register and start a download, recording the chosen [format] as metadata on the request.
     * Rejects a URL that is already active (see [isDuplicateUrl]) and reports it via
     * [lastAddRejectedDuplicate]; returns `true` only when a new mission was started.
     *
     * When [subtitleUrl] is non-blank, a real companion mission downloads the subtitle document
     * beside the main file (see [subtitleDestinationPath]); [subtitleLanguageTag] names that file.
     */
    fun addFromUrl(
        url: String,
        folder: String,
        format: DownloadFormat,
        subtitleUrl: String? = null,
        subtitleLanguageTag: String? = null
    ): Boolean {
        val clean = url.trim()
        if (clean.isEmpty() || folder.isBlank()) return false
        if (isDuplicate(engine.tasks.value, clean)) {
            _lastAddRejectedDuplicate.value = true
            return false
        }
        _lastAddRejectedDuplicate.value = false
        val fileName = fileNameFor(clean).ifBlank { "download" }
        val destinationPath = destinationIn(folder, fileName)
        if (destinationPath.isBlank()) return false
        val request = DownloadRequest(
            id = "dl-" + clean.hashCode().toUInt().toString(16) + "-" + destinationPath.hashCode().toUInt().toString(16),
            url = clean,
            destinationPath = destinationPath,
            title = fileName,
            format = formatLabel(format),
            subtitleUrl = subtitleUrl?.trim()?.ifBlank { null },
            subtitleLanguageTag = subtitleLanguageTag?.trim()?.ifBlank { null }
        )
        engine.enqueue(request).let { engine.start(it.id) }
        subtitleCompanionRequest(request)?.let { engine.enqueue(it).let { sub -> engine.start(sub.id) } }
        return true
    }

    /**
     * Reposition a mission using [reorderTasks] and persist the resulting id order. No-op when the
     * move is rejected (out of bounds, equal indices, or a pinned active/completed neighbour).
     */
    fun reorder(fromIndex: Int, toIndex: Int) {
        val current = tasks.value
        val reordered = reorderTasks(current, fromIndex, toIndex)
        if (reordered == current) return
        persistedOrder.value = reordered.map { it.id }
        store.saveOrder(persistedOrder.value)
    }

    /** Clears the duplicate banner once the user edits the URL again. */
    fun dismissDuplicate() {
        _lastAddRejectedDuplicate.value = false
    }

    fun start(id: String) = engine.start(id)
    fun pause(id: String) = engine.pause(id)
    fun cancel(id: String) = engine.cancel(id)
    fun remove(id: String) = engine.remove(id)
    fun clearCompleted() = engine.clearCompleted()

    private fun applyOrder(list: List<DownloadTask>, order: List<String>): List<DownloadTask> {
        if (order.isEmpty() || list.size < 2) return list
        val rank = HashMap<String, Int>(order.size)
        order.forEachIndexed { index, id -> rank[id] = index }
        return list.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }
}
