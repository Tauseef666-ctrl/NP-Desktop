/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.component.PlusSnackbarHost
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.NotificationsSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.notifications_settings_enable
import newpipe.shared.generated.resources.notifications_settings_enable_summary
import newpipe.shared.generated.resources.notifications_settings_test
import newpipe.shared.generated.resources.notifications_settings_test_failed
import newpipe.shared.generated.resources.notifications_settings_test_sent
import newpipe.shared.generated.resources.notifications_settings_title
import newpipe.shared.generated.resources.notifications_settings_unsupported
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationsSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: NotificationsSettingsViewModel = koinViewModel()
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()

    NotificationsSettingsScreenContent(
        enabled = enabled,
        isSupported = viewModel.isSupported,
        onEnabledChange = viewModel::setEnabled,
        onSendTest = viewModel::sendTestNotification,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun NotificationsSettingsScreenContent(
    enabled: Boolean = true,
    isSupported: Boolean = true,
    onEnabledChange: (Boolean) -> Unit = {},
    onSendTest: () -> Boolean = { false },
    onNavigateUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sentMessage = stringResource(Res.string.notifications_settings_test_sent)
    val failedMessage = stringResource(Res.string.notifications_settings_test_failed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.notifications_settings_title),
                onNavigateUp = onNavigateUp
            )
        },
        snackbarHost = { PlusSnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            PreferenceCategoryTitle(stringResource(Res.string.notifications_settings_title))
            SwitchPreference(
                title = stringResource(Res.string.notifications_settings_enable),
                summary = stringResource(Res.string.notifications_settings_enable_summary),
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = isSupported
            )
            if (!isSupported) {
                PreferenceRow(title = stringResource(Res.string.notifications_settings_unsupported))
            }
            PreferenceRow(
                title = stringResource(Res.string.notifications_settings_test),
                enabled = isSupported && enabled,
                onClick = {
                    val sent = onSendTest()
                    scope.launch {
                        snackbarHostState.showSnackbar(if (sent) sentMessage else failedMessage)
                    }
                }
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun NotificationsSettingsScreenPreview() {
    NotificationsSettingsScreenContent(enabled = true, isSupported = true)
}
