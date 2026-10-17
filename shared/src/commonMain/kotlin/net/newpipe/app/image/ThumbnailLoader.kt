/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.image

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Download the raw bytes of the thumbnail at [url], or `null` when it is blank, unreachable, returns
 * a non-success status, or the transfer fails. Implementations must never throw for network errors —
 * a missing thumbnail degrades to the caller's placeholder rather than crashing the list.
 */
expect suspend fun fetchThumbnailBytes(url: String): ByteArray?

/**
 * Decode [bytes] into an [ImageBitmap], or `null` when the payload is not a decodable image. Used by
 * [rememberThumbnailPainter] to turn cached bytes into drawable artwork.
 */
expect fun decodeThumbnailImage(bytes: ByteArray): ImageBitmap?
