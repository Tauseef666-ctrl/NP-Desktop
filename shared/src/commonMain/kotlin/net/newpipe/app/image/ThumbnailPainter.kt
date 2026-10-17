/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter

/** Process-wide thumbnail cache shared by every `VideoCard`/`LocalMediaRow` in the app. */
val SharedThumbnailCache = ThumbnailCache()

/**
 * Asynchronously load the thumbnail at [url] and expose it as a [Painter], or `null` while it is
 * loading, when [url] is blank, or when it cannot be fetched/decoded. Results are memoised in
 * [cache] so re-entering the same screen never re-downloads an image, and each composable only
 * decodes an image once per URL.
 */
@Composable
fun rememberThumbnailPainter(
    url: String?,
    cache: ThumbnailCache = SharedThumbnailCache
): Painter? {
    val key = url?.takeIf { it.isNotBlank() }
    var painter by remember(key) { mutableStateOf<Painter?>(null) }
    LaunchedEffect(key) {
        painter = null
        if (key == null) return@LaunchedEffect
        val bytes = cache.get(key)
            ?: fetchThumbnailBytes(key)?.takeIf { it.isNotEmpty() }?.also { cache.put(key, it) }
        painter = bytes?.let { decodeThumbnailImage(it) }?.let { BitmapPainter(it) }
    }
    return painter
}
