/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.provider

import org.koin.core.annotation.Singleton

/**
 * Registers a [MediaProvider] with the shared [ProviderRegistry] as soon as Koin starts.
 *
 * Registration is a side effect that must happen eagerly, before the first screen asks for content;
 * `createdAtStart = true` makes Koin instantiate this holder during graph creation. The provider is
 * injected as the [MediaProvider] interface (not the concrete type) so the registrar works with any
 * binding and stays trivial to unit test.
 */
@Singleton(createdAtStart = true)
class ExtractorProviderRegistrar(
    registry: ProviderRegistry,
    provider: MediaProvider
) {
    init {
        registry.register(provider)
    }
}
