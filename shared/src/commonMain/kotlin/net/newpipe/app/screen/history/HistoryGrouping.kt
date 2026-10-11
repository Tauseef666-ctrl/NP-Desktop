/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.history

import net.newpipe.app.player.RecentlyPlayedEntry

/** Chronological bucket a recently-played entry falls into, newest first. */
enum class HistoryBucket { TODAY, YESTERDAY, THIS_WEEK, EARLIER }

/** One rendered section of the history: a [bucket] and the entries that belong to it. */
data class HistoryGroup(
    val bucket: HistoryBucket,
    val entries: List<RecentlyPlayedEntry>
)

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Whole "day" index for [epochMs], floored by a fixed 86,400,000 ms day (epoch-based UTC days).
 *
 * Approximation note: this is deliberately free of any timezone / `java.time` / `kotlinx-datetime`
 * dependency (none is available in commonMain), so boundaries follow UTC instead of the user's
 * local calendar and ignore DST. Grouping by fixed days is an honest, read-only approximation.
 */
private fun floorDay(epochMs: Long): Long {
    val quotient = epochMs / MILLIS_PER_DAY
    return if (epochMs < 0L && quotient * MILLIS_PER_DAY != epochMs) quotient - 1L else quotient
}

/**
 * Maps a played-at timestamp to a [HistoryBucket] relative to [nowEpochMs], using whole-day
 * boundaries. A timestamp on the same (UTC) day as now — or in the future — counts as TODAY.
 */
fun bucketFor(epochMs: Long, nowEpochMs: Long): HistoryBucket {
    val dayDelta = floorDay(nowEpochMs) - floorDay(epochMs)
    return when {
        dayDelta <= 0L -> HistoryBucket.TODAY
        dayDelta == 1L -> HistoryBucket.YESTERDAY
        dayDelta < 7L -> HistoryBucket.THIS_WEEK
        else -> HistoryBucket.EARLIER
    }
}

/**
 * Splits [entries] into ordered [HistoryGroup]s (TODAY, YESTERDAY, THIS_WEEK, EARLIER), omitting
 * empty buckets and preserving the (newest-first) input order within each group.
 */
fun groupHistory(entries: List<RecentlyPlayedEntry>, nowEpochMs: Long): List<HistoryGroup> {
    val bucketed = entries.groupBy { bucketFor(it.playedAtEpochMs, nowEpochMs) }
    return HistoryBucket.entries.mapNotNull { bucket ->
        val items = bucketed[bucket] ?: return@mapNotNull null
        if (items.isEmpty()) null else HistoryGroup(bucket, items)
    }
}