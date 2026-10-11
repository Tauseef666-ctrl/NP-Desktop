/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.localmedia

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.PlusDialog
import net.newpipe.app.component.PlusSnackbarHost
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.viewmodel.settings.LocalMediaSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.local_media_settings_add_folder
import newpipe.shared.generated.resources.local_media_settings_file_count
import newpipe.shared.generated.resources.local_media_settings_folders_empty
import newpipe.shared.generated.resources.local_media_settings_folders_title
import newpipe.shared.generated.resources.local_media_settings_no_files
import newpipe.shared.generated.resources.local_media_settings_remove_folder
import newpipe.shared.generated.resources.local_media_settings_rescan
import newpipe.shared.generated.resources.local_media_settings_rescan_summary
import newpipe.shared.generated.resources.local_media_settings_scan_unavailable
import newpipe.shared.generated.resources.settings_category_local_media_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LocalMediaSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: LocalMediaSettingsViewModel = koinViewModel()
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val fileCount by viewModel.fileCount.collectAsStateWithLifecycle()
    val hasScanned by viewModel.hasScanned.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()

    LocalMediaSettingsScreenContent(
        folders = folders,
        isScannerAvailable = viewModel.isScannerAvailable,
        isScanning = isScanning,
        fileCount = fileCount,
        hasScanned = hasScanned,
        scanError = scanError,
        onAddFolder = viewModel::addFolder,
        onRemoveFolder = viewModel::removeFolder,
        onRescan = viewModel::rescan,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun LocalMediaSettingsScreenContent(
    folders: List<String> = emptyList(),
    isScannerAvailable: Boolean = true,
    isScanning: Boolean = false,
    fileCount: Int = 0,
    hasScanned: Boolean = false,
    scanError: String? = null,
    onAddFolder: () -> Unit = {},
    onRemoveFolder: (String) -> Unit = {},
    onRescan: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = plusColors()
    val removeLabel = stringResource(Res.string.local_media_settings_remove_folder)
    var pendingRemove by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_local_media_title),
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
            PreferenceCategoryTitle(
                stringResource(Res.string.local_media_settings_folders_title)
            )

            if (!isScannerAvailable) {
                PreferenceRow(
                    title = stringResource(Res.string.local_media_settings_scan_unavailable),
                    enabled = false
                )
            } else {
                if (folders.isEmpty()) {
                    PreferenceRow(
                        title = stringResource(Res.string.local_media_settings_folders_empty),
                        enabled = false
                    )
                } else {
                    folders.forEach { folder ->
                        PreferenceRow(
                            title = folder,
                            summary = removeLabel,
                            onClick = { pendingRemove = folder }
                        )
                    }
                }

                PreferenceRow(
                    title = stringResource(Res.string.local_media_settings_add_folder),
                    onClick = onAddFolder
                )

                PreferenceRow(
                    title = stringResource(Res.string.local_media_settings_rescan),
                    summary = stringResource(Res.string.local_media_settings_rescan_summary),
                    enabled = folders.isNotEmpty() && !isScanning,
                    onClick = onRescan
                )

                if (hasScanned) {
                    Text(
                        text = if (fileCount > 0) {
                            stringResource(
                                Res.string.local_media_settings_file_count,
                                fileCount
                            )
                        } else {
                            stringResource(Res.string.local_media_settings_no_files)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                scanError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.danger,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    val folderToRemove = pendingRemove
    if (folderToRemove != null) {
        PlusDialog(
            title = removeLabel,
            text = folderToRemove,
            confirmLabel = removeLabel,
            onConfirm = {
                onRemoveFolder(folderToRemove)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null },
            destructive = true
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun LocalMediaSettingsScreenPreview() {
    LocalMediaSettingsScreenContent(
        folders = listOf("/home/user/Videos", "/media/audio"),
        isScannerAvailable = true,
        isScanning = false,
        fileCount = 42,
        hasScanned = true,
        scanError = null
    )
}
