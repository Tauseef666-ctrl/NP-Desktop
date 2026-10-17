/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.image

/**
 * A bounded, recency-ordered cache of downloaded thumbnail response bytes keyed by their source URL.
 *
 * It keeps the most recently used thumbnails (both by entry count and total byte budget) and evicts
 * the least recently used ones first, so scrolling a long feed never re-downloads an image it just
 * showed and never grows the heap without limit. It is intentionally free of any I/O or Compose
 * types: it only stores [ByteArray]s, which keeps the eviction logic pure and unit-testable, while
 * the actual fetch/decode lives in [fetchThumbnailBytes] / [decodeThumbnailImage].
 */
class ThumbnailCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxBytes: Long = DEFAULT_MAX_BYTES
) {

    private val entries = LinkedHashMap<String, ByteArray>()
    private var cachedBytes = 0L

    /** Number of thumbnails currently cached. */
    val size: Int get() = entries.size

    /** Total size in bytes of the currently cached thumbnails. */
    val bytes: Long get() = cachedBytes

    /**
     * Return the cached bytes for [url], marking the entry as most recently used, or `null` on a
     * miss.
     */
    fun get(url: String): ByteArray? {
        val bytes = entries.remove(url) ?: return null
        entries[url] = bytes
        return bytes
    }

    /**
     * Store [bytes] for [url], replacing any previous value and evicting least-recently-used entries
     * until both the entry-count and byte budgets are satisfied. Blank keys and empty payloads are
     * ignored (nothing to cache).
     */
    fun put(url: String, bytes: ByteArray) {
        if (url.isBlank() || bytes.isEmpty()) return
        entries.remove(url)?.let { cachedBytes -= it.size }
        entries[url] = bytes
        cachedBytes += bytes.size
        evict()
    }

    /** Whether [url] is currently cached. */
    fun contains(url: String): Boolean = entries.containsKey(url)

    /** Remove every cached thumbnail. */
    fun clear() {
        entries.clear()
        cachedBytes = 0L
    }

    private fun evict() {
        while (entries.isNotEmpty() && (entries.size > maxEntries || cachedBytes > maxBytes)) {
            val oldest = entries.keys.first()
            entries.remove(oldest)?.let { cachedBytes -= it.size }
        }
    }

    companion object {
        /** Default maximum number of cached thumbnails. */
        const val DEFAULT_MAX_ENTRIES: Int = 256

        /** Default maximum total size of the cached thumbnails (64 MiB). */
        const val DEFAULT_MAX_BYTES: Long = 64L * 1024 * 1024
    }
}
