/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

/**
 * Provider capabilities for [MediaProvider].
 *
 * Foundation for F10; no provider is bundled yet.
 */
data class ProviderCapabilities(
    val search: Boolean = false,
    val trending: Boolean = false,
    val subscriptions: Boolean = false,
    val channels: Boolean = false,
    val playlists: Boolean = false
)

/**
 * Foundation for F10; no provider is bundled yet.
 */
interface MediaProvider {
    val id: String
    val name: String
    val isAvailable: Boolean
    val capabilities: ProviderCapabilities
}