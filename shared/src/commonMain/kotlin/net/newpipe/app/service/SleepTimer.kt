/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The sleep timer of the application.
 *
 * The timer is an application-level object, because it outlives the screen the user sets it
 * from: it keeps counting while the user browses elsewhere and acts on the playback when it
 * expires. When it expires the volume fades out over a few seconds, the playback is paused and
 * the volume is restored, so the user wakes up to the level they went to sleep with.
 */
object SleepTimer {

    /**
     * The seconds that are left, or `null` while no timer is running. Collected by the
     * timer button in the player, which turns into a countdown while this is set.
     */
    private val _remainingSeconds = MutableStateFlow<Int?>(null)
    val remainingSeconds = _remainingSeconds.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var controller: PlaybackController? = null

    /**
     * Starts the timer for the given [minutes]. A timer that is already running is replaced.
     */
    fun start(minutes: Int, playbackController: PlaybackController) {
        cancel()
        controller = playbackController
        job = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                _remainingSeconds.value = remaining
                delay(1_000)
                remaining--
            }
            _remainingSeconds.value = null
            val timedOut = controller
            this@SleepTimer.controller = null
            timedOut?.let { rampDownAndPause(it) }
        }
    }

    /**
     * Cancels the running timer without touching the playback.
     */
    fun cancel() {
        job?.cancel()
        job = null
        _remainingSeconds.value = null
        controller = null
    }

    /**
     * Fades the volume out, pauses the playback and restores the volume.
     */
    private suspend fun rampDownAndPause(playbackController: PlaybackController) {
        val state = playbackController.state.value
        if (!state.playing) {
            // Nothing to put to sleep: the video was closed or ended on its own.
            return
        }
        val originalVolume = state.volume
        val steps = 10
        for (step in steps - 1 downTo 1) {
            playbackController.setVolume(originalVolume * step / steps)
            delay(250)
        }
        playbackController.setVolume(0)
        delay(200)
        playbackController.pause()
        delay(200)
        playbackController.setVolume(originalVolume)
    }
}
