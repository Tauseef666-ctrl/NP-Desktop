/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.download.DownloadEngine
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadTask
import org.koin.core.annotation.Singleton

/**
 * Android has no shared download engine yet — downloads still live in the `:app` giga/ExoPlayer
 * stack. This binding keeps the shared Koin graph complete on the Android target and reports
 * honestly as unavailable: no missions are ever created (every [enqueue] resolves to a FAILED task
 * with a truthful message) and the UI must not offer download creation while `isAvailable` is false.
 */
@Singleton(binds = [DownloadEngine::class])
class AndroidDownloadEngine : DownloadEngine {

    override val isAvailable: Boolean = false

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val tasks: StateFlow<List<DownloadTask>> = _tasks

    override fun enqueue(request: DownloadRequest): DownloadTask {
        val rejected = DownloadTask(
            request = request,
            status = DownloadState.FAILED,
            error = "Downloads are not available on Android yet."
        )
        _tasks.value = _tasks.value + rejected
        return rejected
    }

    override fun start(id: String) = Unit

    override fun pause(id: String) = Unit

    override fun cancel(id: String) = Unit

    override fun remove(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
    }

    override fun clearCompleted() = Unit
}