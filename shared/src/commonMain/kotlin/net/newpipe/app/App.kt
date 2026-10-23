/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.runtime.Composable
import net.newpipe.app.di.KoinApp
import net.newpipe.app.di.contentModule
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.NavDisplay
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.navigation.navModule
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.shell.ModernShell
import net.newpipe.app.theme.AppTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.plugin.module.dsl.koinConfiguration

/**
 * Entry point for the multiplatform compose application
 * @param startDestination Starting destination for the app; defaults to about
 * @param onCloseRequest Callback to close the app
 * @param withKoin Additional logic to execute after initialising Koin and setting content
 * @param playbackController The playback controller to use, or `null` to create a platform
 * specific one
 */
@Composable
fun App(
    startDestination: Destination = Destination.About,
    onCloseRequest: () -> Unit,
    withKoin: @Composable () -> Unit = {},
    playbackController: PlaybackController? = null
) {
    KoinApplication(
        configuration = koinConfiguration<KoinApp>(
            appDeclaration = {
                modules(navModule(), contentModule(playbackController))
            }
        )
    ) {
        AppTheme {
            val navigator: Navigator = koinInject {
                parametersOf(startDestination, onCloseRequest)
            }
            ModernShell(
                currentDestination = navigator.backstack.lastOrNull() ?: startDestination,
                onNavigate = navigator::switchTo
            ) {
                NavDisplay(
                    startDestination = startDestination,
                    onCloseRequest = onCloseRequest,
                    navigator = navigator
                )
            }
            withKoin()
        }
    }
}
