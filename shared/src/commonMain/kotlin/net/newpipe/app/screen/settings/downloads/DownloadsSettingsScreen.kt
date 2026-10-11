/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.component.StatusChip
import net.newpipe.app.component.StatusKind
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.chooseDirectory
import net.newpipe.app.platform.openDownloadedFileFolder
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.DownloadsSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.downloads_settings_clear_completed
import newpipe.shared.generated.resources.downloads_settings_engine_unavailable
import newpipe.shared.generated.resources.downloads_settings_folder_choose
import newpipe.shared.generated.resources.downloads_settings_folder_reset
import newpipe.shared.generated.resources.downloads_settings_folder_title
import newpipe.shared.generated.resources.downloads_settings_open_folder
import newpipe.shared.generated.resources.downloads_settings_saved
import newpipe.shared.generated.resources.downloads_settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DownloadsSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: DownloadsSettingsViewModel = koinViewModel()
) {
    val directory by viewModel.directory.collectAsStateWithLifecycle()
    DownloadsSettingsScreenContent(
        currentDirectory = directory,
        platformDefault = viewModel.platformDefault,
        isEngineAvailable = viewModel.isEngineAvailable,
        onChooseDirectory = {
            chooseDirectory()
                ?.takeIf { it.isNotBlank() }
                ?.let(viewModel::setDirectory)
        },
        onResetDirectory = viewModel::resetDirectory,
        onOpenFolder = {
            openDownloadedFileFolder(directory.ifBlank { viewModel.platformDefault })
        },
        onClearCompleted = viewModel::clearCompleted,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun DownloadsSettingsScreenContent(
    currentDirectory: String = "",
    platformDefault: String = "",
    isEngineAvailable: Boolean = true,
    onChooseDirectory: () -> Unit = {},
    onResetDirectory: () -> Unit = {},
    onOpenFolder: () -> Unit = {},
    onClearCompleted: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(Res.string.downloads_settings_saved)
    val effectiveDirectory = currentDirectory.ifBlank { platformDefault }

    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveDirectory) {
        if (initialized) snackbarHostState.showSnackbar(savedMessage)
        initialized = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.downloads_settings_title),
                onNavigateUp = onNavigateUp
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            PreferenceCategoryTitle(
                title = stringResource(Res.string.downloads_settings_folder_title)
            )
            PreferenceRow(
                title = stringResource(Res.string.downloads_settings_folder_choose),
                summary = effectiveDirectory.takeIf { it.isNotBlank() },
                onClick = onChooseDirectory
            )
            PreferenceRow(
                title = stringResource(Res.string.downloads_settings_folder_reset),
                enabled = currentDirectory != platformDefault,
                onClick = onResetDirectory
            )
            PreferenceRow(
                title = stringResource(Res.string.downloads_settings_open_folder),
                enabled = effectiveDirectory.isNotBlank(),
                onClick = onOpenFolder
            )
            if (!isEngineAvailable) {
                StatusChip(
                    text = stringResource(Res.string.downloads_settings_engine_unavailable),
                    kind = StatusKind.WARNING,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            PreferenceRow(
                title = stringResource(Res.string.downloads_settings_clear_completed),
                enabled = isEngineAvailable,
                onClick = onClearCompleted
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun DownloadsSettingsScreenPreview() {
    DownloadsSettingsScreenContent(
        currentDirectory = "/home/user/Downloads",
        platformDefault = "/home/user/Downloads"
    )
}
