/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.util

/**
 * Formats a duration in seconds the way the NewPipe user interface does.
 *
 * @param seconds the duration in seconds
 * @return the formatted duration, for example `1:02:03` or `2:03`
 */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0:00"

    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60

    return if (hours > 0) {
        "$hours:${minutes.padded()}:${remainingSeconds.padded()}"
    } else {
        "$minutes:${remainingSeconds.padded()}"
    }
}

/**
 * Formats a view count in the compact form used by NewPipe, for example `1.2M views`.
 *
 * @param count the number of views
 * @return the formatted view count
 */
fun formatViewCount(count: Long): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> "${(count / 1_000.0).formatOneDecimal()}K"
    count < 1_000_000_000 -> "${(count / 1_000_000.0).formatOneDecimal()}M"
    else -> "${(count / 1_000_000_000.0).formatOneDecimal()}B"
}

/**
 * Formats a subscriber count in the compact form used by NewPipe.
 */
fun formatSubscriberCount(count: Long): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> "${(count / 1_000.0).formatOneDecimal()}K"
    count < 1_000_000_000 -> "${(count / 1_000_000.0).formatOneDecimal()}M"
    else -> "${(count / 1_000_000_000.0).formatOneDecimal()}B"
}

private fun Double.formatOneDecimal(): String {
    val rounded = Math.round(this * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

private fun Long.padded(): String = if (this < 10) "0$this" else this.toString()
