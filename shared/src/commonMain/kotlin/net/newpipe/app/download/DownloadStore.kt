/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.download

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton

/** Serializable snapshot of the whole download mission list. */
@Serializable
data class DownloadSnapshot(val tasks: List<DownloadTask>)

/**
 * Persists the download mission list in [Settings] so a restart of the app does not lose the queue
 * (mirrors F6/F9 storage style). Partial files already survive on disk; this keeps the task/status
 * view, and resume still works from the `.part` file via HTTP `Range`.
 *
 * On restore, a task recorded as [DownloadState.DOWNLOADING] becomes [DownloadState.QUEUED]: no
 * transfer is actually running just because the app restarted. Speeds are reset to `0`.
 */
@Singleton
class DownloadStore(
    private val settings: Settings,
    private val json: Json
) {

    fun save(snapshot: DownloadSnapshot) {
        settings.putString(KEY, json.encodeToString(snapshot))
    }

    /** @return the last snapshot, or `null` when blank/missing/corrupt/empty. */
    fun load(): DownloadSnapshot? {
        val raw = settings.getStringOrNull(KEY) ?: return null
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString<DownloadSnapshot>(raw).takeIf { it.tasks.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        settings.remove(KEY)
    }

    /**
     * Persist the user's preferred mission order as a list of ids. Stored separately from the task
     * snapshot so the engine overwriting [KEY] on progress updates never drops the order.
     */
    fun saveOrder(ids: List<String>) {
        settings.putString(KEY_ORDER, ids.joinToString("\n"))
    }

    /** @return the last saved order, or an empty list when none was stored. */
    fun loadOrder(): List<String> =
        settings.getStringOrNull(KEY_ORDER)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    companion object {
        const val KEY = "npp_download_tasks"
        const val KEY_ORDER = "npp_download_order"

        /** Prepare a stored snapshot for reloading into an engine (see the contract above). */
        fun restoreTasks(snapshot: DownloadSnapshot): List<DownloadTask> =
            snapshot.tasks.map { task ->
                task.copy(
                    status = if (task.status == DownloadState.DOWNLOADING) {
                        DownloadState.QUEUED
                    } else {
                        task.status
                    },
                    bytesPerSecond = 0L
                )
            }
    }
}