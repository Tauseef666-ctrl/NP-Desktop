/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.navigation

import androidx.compose.runtime.mutableStateListOf
import co.touchlab.kermit.Logger
import net.newpipe.app.screen.about.AboutScreen
import net.newpipe.app.screen.channel.ChannelScreen
import net.newpipe.app.screen.playlist.PlaylistScreen
import net.newpipe.app.screen.search.SearchScreen
import net.newpipe.app.screen.settings.AppearanceSettingsScreen
import net.newpipe.app.screen.settings.DebugSettingsScreen
import net.newpipe.app.screen.settings.DownloadSettingsScreen
import net.newpipe.app.screen.settings.PlayerSettingsScreen
import net.newpipe.app.screen.settings.SettingsHomeScreen
import net.newpipe.app.screen.settings.VideoAudioSettingsScreen
import net.newpipe.app.screen.stream.StreamScreen
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

    navigation<Destination.Search> {
        SearchScreen()
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
    fun navigateTo(destination: Destination) = backstack.add(destination)

    /**
     * Switches to a top level destination, as selected in the navigation rail.
     *
     * The backstack is reset so that repeated use of the rail does not pile up screens. The start
     * destination stays at the bottom of the stack as the place "back" returns to, unless the
     * requested destination is the start destination itself.
     */
    fun switchTo(destination: Destination) {
        if (backstack.lastOrNull() == destination) return
        backstack.clear()
        if (destination != startDestination) {
            backstack.add(startDestination)
        }
        backstack.add(destination)
    }

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
