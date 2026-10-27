/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType

/**
 * The playback queue of the session: the "up next" videos the user added while browsing.
 *
 * The queue lives in memory; when the current playback reaches its end the head of the queue is
 * taken over through [onAutoAdvance], which the application shell wires to the navigation so the
 * watch page of the next video opens and starts playing. Queue entries the user opens by hand are
 * removed by the user interface at the same time.
 */
class QueueRepository(
    private val controller: PlaybackController
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var watching = false

    /**
     * Invoked with the next queue entry when the current playback reaches its end; the head of
     * the queue is already removed then. The shell sets the navigation callback.
     */
    var onAutoAdvance: ((ContentItem) -> Unit)? = null

    private val _queue = MutableStateFlow<List<ContentItem>>(emptyList())

    /**
     * The queue of the session, newest entries at the end.
     */
    val queue: StateFlow<List<ContentItem>> = _queue.asStateFlow()

    /**
     * Adds an item at the end of the queue; duplicates and non stream entries are ignored.
     */
    fun add(item: ContentItem) {
        if (item.type != ContentType.STREAM) return
        if (_queue.value.any { it.url == item.url }) return
        _queue.value = _queue.value + item
    }

    /**
     * Removes an entry by url.
     */
    fun remove(url: String) {
        _queue.value = _queue.value.filterNot { it.url == url }
    }

    /**
     * Moves the entry at [index] one position up in the queue.
     */
    fun moveUp(index: Int) {
        move(index, index - 1)
    }

    /**
     * Moves the entry at [index] one position down in the queue.
     */
    fun moveDown(index: Int) {
        move(index, index + 1)
    }

    /**
     * Removes every entry from the queue.
     */
    fun clear() {
        _queue.value = emptyList()
    }

    /**
     * Watches the playback state and advances the queue when the current playback has reached
     * its end. The monitor runs once per process; usually the shell starts it right after the
     * controller is ready.
     */
    fun startWatching() {
        synchronized(this) {
            if (watching) return
            watching = true
        }
        scope.launch {
            var armed = false
            var lastUrl = ""
            controller.state.collect { state ->
                val url = state.webUrl
                if (url != lastUrl) {
                    lastUrl = url
                    armed = false
                }

                val duration = state.durationMs
                val finished = duration > 0 &&
                    state.positionMs > 0 &&
                    state.positionMs >= duration - END_GRACE_MS &&
                    !state.playing
                val mid = duration > 0 &&
                    state.positionMs in 1 until (duration - MID_GUARD_MS)

                // Arming keeps the advance from firing while the user seeks or pauses somewhere
                // in the video; only a playback that visibly passed through the middle reaches
                // the "ended" state.
                if (mid && !state.loading && state.error == null) {
                    armed = true
                }
                if (finished && armed && !state.loading && state.error == null) {
                    armed = false
                    val head = _queue.value.firstOrNull() ?: return@collect
                    _queue.value = _queue.value.drop(1)
                    Logger.i(messageString = "Queue: the playback ended; advancing to ${head.url}")
                    onAutoAdvance?.invoke(head)
                }
            }
        }
    }

    private fun move(current: Int, target: Int) {
        val queue = _queue.value
        if (current !in queue.indices) return
        if (target !in queue.indices) return
        val mutable = queue.toMutableList()
        val item = mutable.removeAt(current)
        mutable.add(target, item)
        _queue.value = mutable
    }

    companion object {
        private const val END_GRACE_MS = 250L
        private const val MID_GUARD_MS = 800L
    }
}
