/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.newpipe.app.component.AppNavRail
import net.newpipe.app.di.KoinApp
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.NavDisplay
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.navigation.navModule
import net.newpipe.app.theme.AppTheme
import net.newpipe.app.theme.plusColors
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.plugin.module.dsl.koinConfiguration

/**
 * Entry point for the multiplatform compose application
 * @param startDestination Starting destination for the app; defaults to about
 * @param onCloseRequest Callback to close the app
 * @param withKoin Additional logic to execute after initialising Koin and setting content
 */
@Composable
fun App(
    startDestination: Destination = Destination.About,
    onCloseRequest: () -> Unit,
    withKoin: @Composable () -> Unit = {}
) {
    KoinApplication(
        configuration = koinConfiguration<KoinApp>(
            appDeclaration = {
                modules(navModule())
            }
        )
    ) {
        AppTheme {
            val colors = plusColors()
            // Created once here (with its required parameters) so the app-wide rail and every
            // screen share the same Navigator singleton.
            val navigator: Navigator = koinInject {
                parametersOf(startDestination, onCloseRequest)
            }
            Row(modifier = Modifier.fillMaxSize().background(colors.background)) {
                AppNavRail(navigator = navigator)
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavDisplay(
                        startDestination = startDestination,
                        onCloseRequest = onCloseRequest,
                        navigator = navigator
                    )
                }
            }
            withKoin()
        }
    }
}
