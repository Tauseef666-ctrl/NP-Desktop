/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.search

/**
 * Pure suggestion matcher: returns [history] entries that contain [query] (case-insensitive),
 * excluding an entry that is exactly the query, preserving [history] order and taking at most
 * [limit] results. A blank query (or non-positive [limit]) yields no suggestions.
 */
fun matchSuggestions(query: String, history: List<String>, limit: Int = 5): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || limit <= 0) return emptyList()
    return history
        .filter { it.contains(trimmed, ignoreCase = true) }
        .filterNot { it.equals(trimmed, ignoreCase = true) }
        .take(limit)
}
