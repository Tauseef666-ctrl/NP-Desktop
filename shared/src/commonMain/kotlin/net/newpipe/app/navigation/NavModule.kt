/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.navigation

import androidx.compose.runtime.mutableStateListOf
import co.touchlab.kermit.Logger
import net.newpipe.app.screen.about.AboutScreen
import net.newpipe.app.screen.channels.ChannelsScreen
import net.newpipe.app.screen.downloads.DownloadsScreen
import net.newpipe.app.screen.history.HistoryScreen
import net.newpipe.app.screen.home.HomeScreen
import net.newpipe.app.screen.library.LibraryScreen
import net.newpipe.app.screen.player.PlayerScreen
import net.newpipe.app.screen.playlist.PlaylistScreen
import net.newpipe.app.screen.playlists.PlaylistsScreen
import net.newpipe.app.screen.search.SearchScreen
import net.newpipe.app.screen.settings.AppearanceSettingsScreen
import net.newpipe.app.screen.settings.PlayerSettingsScreen
import net.newpipe.app.screen.settings.SettingsHomeScreen
import net.newpipe.app.screen.settings.VideoAudioSettingsScreen
import net.newpipe.app.screen.settings.advanced.AdvancedSettingsScreen
import net.newpipe.app.screen.settings.backup.BackupRestoreScreen
import net.newpipe.app.screen.settings.content.ContentSettingsScreen
import net.newpipe.app.screen.settings.downloads.DownloadsSettingsScreen
import net.newpipe.app.screen.settings.history.HistorySettingsScreen
import net.newpipe.app.screen.settings.localmedia.LocalMediaSettingsScreen
import net.newpipe.app.screen.settings.notifications.NotificationsSettingsScreen
import net.newpipe.app.screen.settings.search.SearchServicesSettingsScreen
import net.newpipe.app.screen.subscriptions.SubscriptionsScreen
import net.newpipe.app.screen.trending.TrendingScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Singleton
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.single

/**
 * Navigation module to make navigation easier with nav3
 *
 * There is currently no annotation to handle this so we are using DSL API of Koin
 */
@OptIn(KoinExperimentalAPI::class)
fun navModule() = module {
    single<Navigator>()

    navigation<Destination.Home> {
        HomeScreen()
    }

    navigation<Destination.Player> {
        PlayerScreen()
    }

    navigation<Destination.Library> {
        LibraryScreen()
    }

    navigation<Destination.Playlists> {
        PlaylistsScreen()
    }

    navigation<Destination.Subscriptions> {
        SubscriptionsScreen()
    }

    navigation<Destination.Downloads> {
        DownloadsScreen()
    }

    navigation<Destination.History> {
        HistoryScreen()
    }

    navigation<Destination.Search> {
        SearchScreen()
    }

    navigation<Destination.Trending> {
        TrendingScreen()
    }

    navigation<Destination.Channels> {
        ChannelsScreen()
    }

    navigation<Destination.Playlist> {
        PlaylistScreen()
    }

    navigation<Destination.About> {
        AboutScreen()
    }

    navigation<Destination.Settings> {
        SettingsHomeScreen()
    }

    navigation<Destination.BackupRestore> {
        BackupRestoreScreen()
    }

    navigation<Destination.DownloadsSettings> {
        DownloadsSettingsScreen()
    }

    navigation<Destination.HistorySettings> {
        HistorySettingsScreen()
    }

    navigation<Destination.ContentSettings> {
        ContentSettingsScreen()
    }

    navigation<Destination.LocalMediaSettings> {
        LocalMediaSettingsScreen()
    }

    navigation<Destination.SearchServicesSettings> {
        SearchServicesSettingsScreen()
    }

    navigation<Destination.AdvancedSettings> {
        AdvancedSettingsScreen()
    }

    navigation<Destination.NotificationsSettings> {
        NotificationsSettingsScreen()
    }

    navigation<Destination.AppearanceSettings> {
        AppearanceSettingsScreen()
    }

    navigation<Destination.VideoAudioSettings> {
        VideoAudioSettingsScreen()
    }

    navigation<Destination.PlayerSettings> {
        PlayerSettingsScreen()
    }
}

/**
 * Helper to navigate up and to different destinations in compose
 */
@Singleton
class Navigator(
    @Provided
    private val startDestination: Destination,

    @Provided
    private val onCloseRequest: () -> Unit
) {

    /**
     * Navigation backstack in compose
     */
    val backstack = mutableStateListOf(startDestination)

    /**
     * Navigates to the given destination
     */
    fun navigateTo(destination: Destination) = backstack.add(destination)

    /**
     * Navigates to the previous entry in the backstack
     */
    fun navigateUp() = when {
        backstack.size > 1 -> backstack.removeLastOrNull()

        else -> {
            Logger.i(messageString = "Cannot remove the only entry in backstack!")
            onCloseRequest()
        }
    }
}
