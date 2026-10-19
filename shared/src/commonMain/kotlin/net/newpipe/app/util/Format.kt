/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.util

/**
 * Formats a duration in seconds as `m:ss` (or `h:mm:ss` when at least an hour long).
 * Multiplatform-safe: does not rely on JVM `String.format`.
 */
fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "0:00"
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0L) {
        "$hours:${pad2(minutes)}:${pad2(seconds)}"
    } else {
        "$minutes:${pad2(seconds)}"
    }
}

/**
 * Formats an approximate count using SI-style suffixes: `999`, `1.2K`, `3.4M`, `5.6B`.
 * Values below 1000 are returned unchanged.
 */
fun formatCount(count: Long): String {
    if (count < 0L) return "0"
    return when {
        count < 1_000L -> count.toString()
        count < 1_000_000L -> trimTrailingZero(count / 1_000.0) + "K"
        count < 1_000_000_000L -> trimTrailingZero(count / 1_000_000.0) + "M"
        else -> trimTrailingZero(count / 1_000_000_000.0) + "B"
    }
}

private fun pad2(value: Long): String = if (value < 10L) "0$value" else value.toString()

private fun trimTrailingZero(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

/**
 * Formats the Android-style metadata line for a feed item: `1.2M views · 3 days ago`. Any part
 * the provider did not report is omitted; returns `null` when nothing is known.
 */
fun formatItemDetails(viewCount: Long?, uploadDate: String?): String? {
    val views = viewCount
        ?.takeIf { it >= 0L }
        ?.let { "${formatCount(it)} views" }
    val date = uploadDate?.takeIf { it.isNotBlank() }
    return listOfNotNull(views, date)
        .joinToString(" \u00b7 ")
        .takeIf { it.isNotEmpty() }
}

/**
 * Formats a byte count in a human-friendly way: `0 B`, `512 B`, `1.1 KB`, `29.6 MB`, `1.2 GB`.
 * Decimal (1000-based) units are used to match common file-manager display conventions.
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return "0 B"
    if (bytes < 1000L) return "$bytes B"
    val value = bytes.toDouble()
    val unit = when {
        bytes < 1000L * 1000L -> "KB"
        bytes < 1000L * 1000L * 1000L -> "MB"
        else -> "GB"
    }
    val scaled = when (unit) {
        "KB" -> value / 1000.0
        "MB" -> value / 1_000_000.0
        else -> value / 1_000_000_000.0
    }
    val rounded = (scaled * 10).toLong() / 10.0
    val number = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    return "$number $unit"
}
