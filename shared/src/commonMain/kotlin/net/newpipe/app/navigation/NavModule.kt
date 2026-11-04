/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.navigation

import androidx.compose.runtime.mutableStateListOf
import co.touchlab.kermit.Logger
import net.newpipe.app.screen.about.AboutScreen
import net.newpipe.app.screen.channel.ChannelScreen
import net.newpipe.app.screen.downloads.DownloadsScreen
import net.newpipe.app.screen.feed.FeedScreen
import net.newpipe.app.screen.home.HomeScreen
import net.newpipe.app.screen.playlist.PlaylistScreen
import net.newpipe.app.screen.search.SearchScreen
import net.newpipe.app.screen.settings.AccountSettingsScreen
import net.newpipe.app.screen.settings.AppearanceSettingsScreen
import net.newpipe.app.screen.settings.DebugSettingsScreen
import net.newpipe.app.screen.settings.DownloadSettingsScreen
import net.newpipe.app.screen.settings.PlayerSettingsScreen
import net.newpipe.app.screen.settings.SettingsHomeScreen
import net.newpipe.app.screen.settings.VideoAudioSettingsScreen
import net.newpipe.app.screen.stream.StreamScreen
import net.newpipe.app.screen.subscriptions.SubscriptionsScreen
import net.newpipe.app.screen.history.HistoryScreen
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

    navigation<Destination.About> {
        AboutScreen()
    }

    navigation<Destination.Settings> {
        SettingsHomeScreen()
    }

    navigation<Destination.AppearanceSettings> {
        AppearanceSettingsScreen()
    }

    navigation<Destination.AccountSettings> {
        AccountSettingsScreen()
    }

    navigation<Destination.VideoAudioSettings> {
        VideoAudioSettingsScreen()
    }

    navigation<Destination.PlayerSettings> {
        PlayerSettingsScreen()
    }

    navigation<Destination.DebugSettings> {
        DebugSettingsScreen()
    }

    navigation<Destination.DownloadSettings> {
        DownloadSettingsScreen()
    }

    navigation<Destination.Home> {
        HomeScreen()
    }

    navigation<Destination.Search> {
        SearchScreen()
    }

    navigation<Destination.Feed> { destination ->
        FeedScreen(
            kioskId = destination.kioskId,
            title = destination.title
        )
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

    navigation<Destination.Stream> { destination ->
        StreamScreen(
            serviceId = destination.serviceId,
            url = destination.url
        )
    }

    navigation<Destination.Channel> { destination ->
        ChannelScreen(
            serviceId = destination.serviceId,
            url = destination.url
        )
    }

    navigation<Destination.Playlist> { destination ->
        PlaylistScreen(
            serviceId = destination.serviceId,
            url = destination.url
        )
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
    fun navigateTo(destination: Destination) {
        Logger.i(messageString = "Navigating to $destination")
        backstack.add(destination)
    }

    /**
     * Switches to a top level destination, as selected in the navigation rail.
     *
     * Everything above the start destination is removed, but the start destination itself stays
     * at the bottom of the stack: switching between rail sections does not destroy the state of
     * the home screen, so it does not re-fetch its whole feed on every rail selection.
     */
    fun switchTo(destination: Destination) {
        Logger.i(messageString = "Switching to $destination")
        if (backstack.lastOrNull() == destination) return
        while (backstack.size > 1) {
            backstack.removeAt(backstack.size - 1)
        }
        if (destination != startDestination && destination != backstack.firstOrNull()) {
            backstack.add(destination)
        }
    }

    /**
     * Navigates to the previous entry in the backstack.
     *
     * When there is no previous entry the user is taken back to the home screen instead of the
     * whole application being closed. Only the home screen itself closes the application when
     * there is nothing to go back to.
     */
    fun navigateUp() = when {
        backstack.size > 1 -> backstack.removeLastOrNull()

        backstack.lastOrNull() != Destination.Home -> {
            backstack.clear()
            backstack.add(Destination.Home)
        }

        else -> {
            Logger.i(messageString = "Cannot remove the only entry in backstack!")
            onCloseRequest()
        }
    }
}
