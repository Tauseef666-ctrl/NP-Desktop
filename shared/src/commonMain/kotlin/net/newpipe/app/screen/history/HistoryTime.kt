/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.history

/**
 * Best-effort display label for an item date in history rows.
 *
 * Extractors usually provide [uploadDate] as already-human text (often relative, e.g.
 * "2 days ago"). When no parseable watched-at timestamp exists in the model, keep the
 * extractor text and avoid inventing precision.
 */
internal fun historyDateLabel(uploadDate: String?): String? {
    val value = uploadDate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return value
}