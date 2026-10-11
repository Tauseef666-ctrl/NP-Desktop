/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

/**
 * Pure conversions between a live [PlayQueue] and a persisted [SavedPlaylist], plus an
 * availability filter used to build skip-unavailable behaviour.
 */

/** Snapshot the queue's current display order as a playlist; [PlayQueue.currentIndex] is ignored. */
fun PlayQueue.toSavedPlaylist(name: String, id: String = name): SavedPlaylist =
    SavedPlaylist(id = id, name = name, items = items)

/** Build a fresh queue from a playlist's items, starting at the first item. */
fun SavedPlaylist.toPlayQueue(): PlayQueue = PlayQueue(items)

/** Keep [list] order while dropping every item for which [isAvailable] is false. */
fun List<MediaItem>.skipUnavailable(isAvailable: (MediaItem) -> Boolean): List<MediaItem> =
    filter(isAvailable)

/** An item is playable only when it carries a resolved stream URL; a blank URL cannot be played. */
fun MediaItem.isAvailable(): Boolean = url.isNotBlank()
