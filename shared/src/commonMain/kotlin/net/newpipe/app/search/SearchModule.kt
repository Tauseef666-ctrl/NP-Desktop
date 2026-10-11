/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.search

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/**
 * Koin module for shared search infrastructure (e.g. [SearchHistoryStore]). Mirrors the other
 * `@ComponentScan` modules so the store is auto-registered with the application's Koin graph.
 */
@Module
@ComponentScan
@Configuration
object SearchModule
