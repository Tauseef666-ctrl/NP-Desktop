/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.subscription

private val SCHEME_REGEX = Regex("^(https?)://(.+)$", RegexOption.IGNORE_CASE)

/**
 * Normalizes a user-supplied channel URL so identical channels compare equal. Trims surrounding
 * whitespace, lower-cases the scheme and host while keeping the path case, and drops a trailing
 * slash. Returns `null` for blank input or anything that is not an `http`/`https` URL with a host.
 */
fun normalizeChannelUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val match = SCHEME_REGEX.matchEntire(trimmed) ?: return null
    val scheme = match.groupValues[1].lowercase()
    if (scheme != "http" && scheme != "https") return null
    val rest = match.groupValues[2]
    val slash = rest.indexOf('/')
    val host = if (slash == -1) rest else rest.substring(0, slash)
    if (host.isBlank()) return null
    val path = if (slash == -1) "" else rest.substring(slash)
    return "$scheme://${host.lowercase()}${path.trimEnd('/')}"
}

/**
 * Deterministic subscription id for [raw]: the [normalizeChannelUrl] result lower-cased. Returns
 * `null` when [raw] is not a valid channel URL, so callers never invent an id.
 */
fun subscriptionIdFor(raw: String): String? = normalizeChannelUrl(raw)?.lowercase()

/**
 * Heuristic used to flag obviously-bad manual input: a valid `http(s)` URL whose host looks like a
 * domain (contains a dot) and that carries a path segment rather than pointing at a bare host.
 */
fun isLikelyChannelUrl(raw: String): Boolean {
    val normalized = normalizeChannelUrl(raw) ?: return false
    val rest = normalized.substringAfter("://")
    val host = rest.substringBefore('/')
    val path = rest.substringAfter('/', "")
    return host.contains('.') && path.isNotBlank()
}
