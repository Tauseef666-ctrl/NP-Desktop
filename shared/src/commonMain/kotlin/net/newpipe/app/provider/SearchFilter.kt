/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

/**
 * A provider-agnostic search content filter. [id] is stable and used by providers that support
 * filtered search; [label] is the display text for the UI. [ALL] means an unfiltered search.
 */
enum class SearchFilter(val id: String, val label: String) {
    ALL("all", "All"),
    VIDEOS("videos", "Videos"),
    CHANNELS("channels", "Channels"),
    PLAYLISTS("playlists", "Playlists");

    companion object {
        /** Resolve a filter from a persisted [id], falling back to [ALL] when it is unknown/blank. */
        fun fromId(id: String?): SearchFilter =
            entries.firstOrNull { it.id == id } ?: ALL
    }
}
