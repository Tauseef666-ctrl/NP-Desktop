/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.history

import net.newpipe.app.player.RecentlyPlayedEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryGroupingTest {

    private val day = 86_400_000L

    @Test
    fun sameUtcDayIsToday() {
        val now = 10 * day + 36_000L
        assertEquals(HistoryBucket.TODAY, bucketFor(epochMs = now, nowEpochMs = now))
        assertEquals(HistoryBucket.TODAY, bucketFor(epochMs = now - 36_000L, nowEpochMs = now))
    }

    @Test
    fun exactPreviousUtcDayIsYesterday() {
        val now = 10 * day
        assertEquals(HistoryBucket.YESTERDAY, bucketFor(epochMs = 9 * day + 36_000L, nowEpochMs = now))
        assertEquals(HistoryBucket.YESTERDAY, bucketFor(epochMs = 9 * day, nowEpochMs = now))
    }

    @Test
    fun olderThanYesterdayButWithinAWeekIsThisWeek() {
        val now = 10 * day
        assertEquals(HistoryBucket.THIS_WEEK, bucketFor(epochMs = 8 * day, nowEpochMs = now))
        assertEquals(HistoryBucket.THIS_WEEK, bucketFor(epochMs = 4 * day, nowEpochMs = now))
    }

    @Test
    fun sevenOrMoreUtcDaysAgoIsEarlier() {
        val now = 10 * day
        assertEquals(HistoryBucket.EARLIER, bucketFor(epochMs = 3 * day, nowEpochMs = now))
        assertEquals(HistoryBucket.EARLIER, bucketFor(epochMs = 0L, nowEpochMs = now))
    }

    @Test
    fun futureTimestampIsToday() {
        val now = 10 * day
        assertEquals(HistoryBucket.TODAY, bucketFor(epochMs = now + day, nowEpochMs = now))
    }

    @Test
    fun groupHistoryOrdersBucketsNewestFirstAndPreservesInputOrderWithinGroups() {
        val now = 10 * day + 3_600_000L
        val entries = listOf(
            RecentlyPlayedEntry("early", playedAtEpochMs = 2 * day),
            RecentlyPlayedEntry("thisWeek", playedAtEpochMs = 6 * day),
            RecentlyPlayedEntry("yesterday", playedAtEpochMs = 9 * day),
            RecentlyPlayedEntry("today2", playedAtEpochMs = now - 60_000L),
            RecentlyPlayedEntry("today1", playedAtEpochMs = now - 30_000L)
        )

        val groups = groupHistory(entries, now)

        assertEquals(
            listOf(HistoryBucket.TODAY, HistoryBucket.YESTERDAY, HistoryBucket.THIS_WEEK, HistoryBucket.EARLIER),
            groups.map { it.bucket }
        )
        assertEquals(listOf("today2", "today1"), groups[0].entries.map { it.id })
        assertEquals(listOf("yesterday"), groups[1].entries.map { it.id })
        assertEquals(listOf("thisWeek"), groups[2].entries.map { it.id })
        assertEquals(listOf("early"), groups[3].entries.map { it.id })
    }

    @Test
    fun groupHistoryOmitsEmptyBuckets() {
        val now = 10 * day
        val groups = groupHistory(
            listOf(RecentlyPlayedEntry("now", playedAtEpochMs = now)),
            now
        )

        assertEquals(1, groups.size)
        assertEquals(HistoryBucket.TODAY, groups.single().bucket)
    }

    @Test
    fun groupHistoryOfEmptyInputIsEmpty() {
        assertTrue(groupHistory(emptyList(), nowEpochMs = 10 * day).isEmpty())
    }
}