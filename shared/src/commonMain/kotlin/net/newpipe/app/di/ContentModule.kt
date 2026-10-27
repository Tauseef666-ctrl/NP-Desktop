/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di

import net.newpipe.app.service.ContentRepository
import net.newpipe.app.service.DownloadRepository
import net.newpipe.app.service.MediaDownloader
import net.newpipe.app.service.PlaylistRepository
import net.newpipe.app.service.QueueRepository
import net.newpipe.app.service.SearchHistoryRepository
import net.newpipe.app.service.SubscriptionRepository
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.ServiceRegistry
import net.newpipe.app.service.WatchHistoryRepository
import net.newpipe.app.service.createContentRepository
import net.newpipe.app.service.createPlaybackController
import net.newpipe.app.service.createServiceRegistry
import net.newpipe.app.ui.PlayerUiState
import org.koin.dsl.module

/**
 * Module providing access to the streaming services.
 *
 * The concrete implementations are platform specific: the desktop uses the real extraction engine,
 * the other platforms currently use stubs. See `docs/ARCHITECTURE.md` section 4.1.
 *
 * @param playbackController the playback controller to use, or `null` to create a platform specific
 * one
 */
fun contentModule(
    playbackController: PlaybackController? = null,
    playerUiState: PlayerUiState? = null
) = module {
    single<ServiceRegistry> { createServiceRegistry() }
    single<ContentRepository> { createContentRepository() }
    single<PlaybackController> { playbackController ?: createPlaybackController() }
    single { playerUiState ?: PlayerUiState() }
    single { WatchHistoryRepository(get()) }
    single { PlaylistRepository(get()) }
    single { QueueRepository(get()) }
    single { DownloadRepository(get()) }
    single { SearchHistoryRepository(get()) }
    single { SubscriptionRepository(get()) }
    single { MediaDownloader() }
}
