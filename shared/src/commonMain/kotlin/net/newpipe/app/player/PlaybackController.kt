/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.newpipe.app.preferences.PlayerPreferences
import org.koin.core.annotation.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Single shared owner of the play [PlayQueue] and the [PlayerEngine]. Any screen (player, library,
 * downloads) can enqueue and control playback through this one instance, so the queue survives
 * navigation. The engine is a singleton and is intentionally never released here.
 *
 * Also owns smart playback resume (F9): positions are persisted via [PlaybackResumeStore] and
 * replayed as [MediaItem.resumePositionMs] whenever an item is loaded.
 *
 * The play queue itself is persisted via [PlayQueueStore] (F6): the last queue (order, current
 * item, repeat, shuffle) survives a restart without auto-playing anything.
 */
@Singleton
class PlaybackController(
    private val engine: PlayerEngine,
    private val resumeStore: PlaybackResumeStore,
    private val queueStore: PlayQueueStore,
    private val settings: Settings
) {

    // Read on demand rather than copied at construction: this controller is injected during the
    // first composition, and a blocking engine-availability probe (VLC discovery) there would
    // delay the first painted frame.
    val isEngineAvailable: Boolean get() = engine.isAvailable
    val state: StateFlow<PlaybackState> = engine.state

    /** The platform engine behind this controller, for video-surface hosts (mini player). */
    val playerEngine: PlayerEngine get() = engine

    private val resumeEnabled: Boolean
        get() = settings.getBoolean(
            PlayerPreferences.KEY_RESUME_PLAYBACK,
            PlayerPreferences.DEFAULT_RESUME_PLAYBACK
        )

    /**
     * Persisted default playback speed, resolved to a supported preset on every read so a corrupt
     * or missing value can never reach the engine. Applied to each newly prepared item (see
     * [prepareResumed]); the player UI may still override the speed for the current session.
     */
    val defaultPlaybackSpeed: Float
        get() = resolveDefaultSpeed(
            settings.getStringOrNull(PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED)
        )

    private val queue = PlayQueue()

    private val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val playlists = PlaylistStore(settings, Json)

    private val _queueState = MutableStateFlow(PlayerQueueState())
    val queueState: StateFlow<PlayerQueueState> = _queueState.asStateFlow()

    private val _savedPlaylists = MutableStateFlow(loadSavedPlaylists())
    val savedPlaylists: StateFlow<List<SavedPlaylist>> = _savedPlaylists.asStateFlow()

    init {
        queueStore.load()?.let { restored ->
            queue.restore(
                newItems = restored.items,
                current = restored.currentIndex,
                repeat = restored.repeatMode,
                shuffled = restored.isShuffled
            )
        }
        publishQueue()
        // Persist positions as playback progresses — especially whenever an item is paused or ends.
        watchEngineForResumeSaves()
    }

    fun play() = engine.play()
    fun pause() = engine.pause()
    fun stop() = engine.stop()
    fun togglePlayPause() = engine.togglePlayPause()
    fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
    fun setSpeed(speed: Float) = engine.setSpeed(speed)
    fun setVolume(volume: Float) = engine.setVolume(volume)
    fun setMuted(muted: Boolean) = engine.setMuted(muted)
    fun toggleMute() = engine.setMuted(!state.value.isMuted)
    fun setQuality(quality: Quality) = engine.setQuality(quality)

    /** Replace the whole queue and start the item at [startIndex]. */
    suspend fun setQueue(items: List<MediaItem>, startIndex: Int = 0) {
        queue.replaceAll(items, startIndex)
        publishQueue()
        queue.current?.let { prepareResumed(it) }
    }

    /** Play [item]: jump to it if already queued, otherwise enqueue and select it. */
    suspend fun playItem(item: MediaItem) {
        val existing = queue.items.indexOf(item)
        val target = if (existing >= 0) {
            existing
        } else {
            queue.enqueue(item)
            queue.items.lastIndex
        }
        queue.jumpTo(target)
        publishQueue()
        prepareResumed(queue.current ?: item)
    }

    suspend fun next() {
        queue.next()?.let { prepareResumed(it) }
        publishQueue()
    }

    suspend fun previous() {
        queue.previous()?.let { prepareResumed(it) }
        publishQueue()
    }

    /** Non-suspending next/previous for callers without their own scope (e.g. the mini player). */
    fun skipToNext() = callScope.launch { next() }

    fun skipToPrevious() = callScope.launch { previous() }

    fun toggleShuffle() {
        queue.toggleShuffle()
        publishQueue()
    }

    fun setRepeatMode(mode: RepeatMode) {
        queue.repeatMode = mode
        publishQueue()
    }

    /** Empty the queue (F6). Stops nothing already playing; it just has no next up. */
    fun clearQueue() {
        queue.clear()
        publishQueue()
    }

    /**
     * Reorder the queue (F6). Invalid indexes and equal from/to are ignored. The currently-playing
     * item is preserved by identity, so moving it keeps playback on the same track. Persists on success.
     */
    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (!queue.move(fromIndex, toIndex)) return
        publishQueue()
    }

    /**
     * Drop the queued item at [index] (F6). Removing the current item does not stop the engine —
     * [PlayQueue] semantics move the current selection to the following item (or the previous one
     * when the removed item was last). Persists on success.
     */
    fun removeItem(index: Int) {
        if (queue.removeAt(index) == null) return
        publishQueue()
    }

    /** Save the current queue as a named playlist (F6). Returns false when the queue is empty. */
    suspend fun saveQueueAsPlaylist(): Boolean {
        if (queue.isEmpty) return false
        playlists.save(queue.toSavedPlaylist(name = QUEUE_PLAYLIST_NAME, id = QUEUE_PLAYLIST_ID))
        refreshSavedPlaylists()
        return true
    }

    /** Replace the queue with [saved]'s items and start its first item (F6). */
    suspend fun restorePlaylist(saved: SavedPlaylist) {
        val rebuilt = saved.toPlayQueue()
        queue.replaceAll(rebuilt.items, rebuilt.currentIndex)
        publishQueue()
        queue.current?.let { prepareResumed(it) }
    }

    /** Remove a saved playlist by [id] (F6). Other playlists are untouched. */
    fun deletePlaylist(id: String) {
        playlists.delete(id)
        refreshSavedPlaylists()
    }

    /**
     * Drop every queued item that has no playable URL (F6). Keeps the current item when it survived;
     * otherwise replays the new current so playback does not silently point at a removed item.
     */
    suspend fun skipUnavailableInQueue() {
        val currentBefore = queue.current
        val available = queue.items.skipUnavailable { it.isAvailable() }
        if (available.size == queue.items.size) {
            publishQueue()
            return
        }
        queue.items.withIndex()
            .filter { (_, item) -> !item.isAvailable() }
            .map { it.index }
            .sortedDescending()
            .forEach { queue.removeAt(it) }
        publishQueue()
        val currentAfter = queue.current
        if (currentAfter != null && currentAfter != currentBefore) {
            prepareResumed(currentAfter)
        }
    }

    /** Explicitly persist the current position (used by periodic/exit-point observers). */
    fun saveCurrentPosition() {
        val current = state.value
        val id = current.mediaId ?: return
        if (current.positionMs > 0L) {
            resumeStore.save(id, current.positionMs, current.durationMs)
        }
    }

    /** Forget any stored resume position for [mediaId] (privacy/cleanup). */
    fun clearResumePosition(mediaId: String) = resumeStore.clear(mediaId)

    /** Forget every stored resume position (privacy/clear-history). */
    fun clearResumePositions() = resumeStore.clearAll()

    /** Enables/disables smart resume for the whole app (reuses [PlayerPreferences.KEY_RESUME_PLAYBACK]). */
    fun setResumeEnabled(enabled: Boolean) {
        settings.putBoolean(PlayerPreferences.KEY_RESUME_PLAYBACK, enabled)
    }

    /**
     * Persist the default speed applied to future items. [speed] is clamped to the nearest
     * supported preset and stored in its canonical form (e.g. `1.5`).
     */
    fun setDefaultSpeed(speed: Float) {
        settings.putString(
            PlayerPreferences.KEY_DEFAULT_PLAYBACK_SPEED,
            canonicalSpeed(speed)
        )
    }

    private suspend fun prepareResumed(item: MediaItem) {
        val resumePosition = resumePositionFor(item)
        val prepared = if (resumePosition > 0L) item.copy(resumePositionMs = resumePosition) else item
        engine.prepare(prepared)
        engine.setSpeed(defaultPlaybackSpeed)
    }

    private fun resumePositionFor(item: MediaItem): Long {
        if (!resumeEnabled) return 0L
        if (item.isLive) return 0L
        if (item.durationMs in 1 until PlayerPreferences.SHORT_CLIP_MAX_MS) {
            resumeStore.clear(item.id)
            return 0L
        }
        return resumeStore.load(item.id)
    }

    private fun watchEngineForResumeSaves() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var periodicId: String? = null
        var periodicAt = 0L
        scope.launch {
            engine.state.collect { current ->
                val id = current.mediaId ?: return@collect
                when (current.status) {
                    PlaybackStatus.PAUSED, PlaybackStatus.ENDED, PlaybackStatus.READY -> {
                        if (current.positionMs > 0L) {
                            resumeStore.save(id, current.positionMs, current.durationMs)
                            periodicId = id
                            periodicAt = System.currentTimeMillis()
                        }
                    }
                    PlaybackStatus.PLAYING -> {
                        val now = System.currentTimeMillis()
                        val due = periodicId != id || now - periodicAt >= SAVE_INTERVAL_MS
                        if (current.positionMs > 0L && due) {
                            resumeStore.save(id, current.positionMs, current.durationMs)
                            periodicId = id
                            periodicAt = now
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun publishQueue() {
        _queueState.value = PlayerQueueState(
            items = queue.items,
            currentIndex = queue.currentIndex,
            isShuffled = queue.isShuffled,
            repeatMode = queue.repeatMode
        )
        persistQueue()
    }

    private fun persistQueue() {
        if (queue.isEmpty) {
            queueStore.clear()
        } else {
            queueStore.save(
                PlayQueueSnapshot(
                    items = queue.items,
                    currentIndex = queue.currentIndex,
                    repeatMode = queue.repeatMode,
                    isShuffled = queue.isShuffled
                )
            )
        }
    }

    private fun loadSavedPlaylists(): List<SavedPlaylist> =
        playlists.loadAll()?.playlists.orEmpty()

    private fun refreshSavedPlaylists() {
        _savedPlaylists.value = loadSavedPlaylists()
    }

    companion object {
        private const val SAVE_INTERVAL_MS = 5_000L
        private const val QUEUE_PLAYLIST_ID = "queue"
        private const val QUEUE_PLAYLIST_NAME = "Queue"

        /**
         * Resolve a persisted speed string to a supported preset. Blank, corrupt, or unsupported
         * values fall back to [PlayerPreferences.DEFAULT_PLAYBACK_SPEED], so playback is never
         * left at a nonsensical rate.
         */
        fun resolveDefaultSpeed(raw: String?): Float {
            val parsed = raw?.trim()?.toFloatOrNull() ?: return PlayerPreferences.DEFAULT_PLAYBACK_SPEED
            return PlayerPreferences.SUPPORTED_PLAYBACK_SPEEDS.firstOrNull { it == parsed }
                ?: PlayerPreferences.DEFAULT_PLAYBACK_SPEED
        }

        /** Clamp any [speed] to the closest supported preset. */
        fun nearestSupportedSpeed(speed: Float): Float {
            if (speed.isNaN()) return PlayerPreferences.DEFAULT_PLAYBACK_SPEED
            return PlayerPreferences.SUPPORTED_PLAYBACK_SPEEDS.minByOrNull { abs(it - speed) }
                ?: PlayerPreferences.DEFAULT_PLAYBACK_SPEED
        }

        /**
         * Canonical persisted representation of [speed], e.g. `0.5`, `1.0`, `0.75`. The value is
         * first clamped to a supported preset, so the result always round-trips through
         * [resolveDefaultSpeed].
         */
        fun canonicalSpeed(speed: Float): String {
            val hundredths = (nearestSupportedSpeed(speed) * 100f).roundToInt()
            val whole = hundredths / 100
            val remainder = hundredths % 100
            return when {
                remainder == 0 -> "$whole.0"
                remainder % 10 == 0 -> "$whole.${remainder / 10}"
                else -> "$whole.${remainder.toString().padStart(2, '0')}"
            }
        }
    }
}