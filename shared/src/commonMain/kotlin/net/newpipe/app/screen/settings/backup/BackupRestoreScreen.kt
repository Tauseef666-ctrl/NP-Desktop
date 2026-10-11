/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.PlusDialog
import net.newpipe.app.component.PlusSnackbarHost
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.chooseDirectory
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.backup.BackupContents
import net.newpipe.app.viewmodel.backup.BackupRestoreViewModel
import net.newpipe.app.viewmodel.backup.BackupStatus
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.backup_center_title
import newpipe.shared.generated.resources.backup_choose_location
import newpipe.shared.generated.resources.backup_contains_label
import newpipe.shared.generated.resources.backup_contents_file_count
import newpipe.shared.generated.resources.backup_create_failed
import newpipe.shared.generated.resources.backup_create_label
import newpipe.shared.generated.resources.backup_created_status
import newpipe.shared.generated.resources.backup_invalid_message
import newpipe.shared.generated.resources.backup_invalid_title
import newpipe.shared.generated.resources.backup_last_created
import newpipe.shared.generated.resources.backup_no_backups
import newpipe.shared.generated.resources.backup_no_location
import newpipe.shared.generated.resources.backup_restore_complete
import newpipe.shared.generated.resources.backup_restore_confirm_message
import newpipe.shared.generated.resources.backup_restore_confirm_title
import newpipe.shared.generated.resources.backup_restore_failed
import newpipe.shared.generated.resources.backup_restore_label
import newpipe.shared.generated.resources.backup_view_contents
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BackupRestoreScreen(
    navigator: Navigator = koinInject(),
    viewModel: BackupRestoreViewModel = koinViewModel()
) {
    val directory by viewModel.directory.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val lastBackupName by viewModel.lastBackupName.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val invalid by viewModel.invalid.collectAsStateWithLifecycle()

    BackupRestoreScreenContent(
        directory = directory,
        backups = backups,
        lastBackupName = lastBackupName,
        isAvailable = viewModel.isAvailable,
        status = status,
        contents = contents,
        pendingRestore = pendingRestore,
        invalid = invalid,
        onChooseFolder = { viewModel.chooseFolder(chooseDirectory()) },
        onCreateBackup = viewModel::createBackup,
        onViewContents = viewModel::viewContents,
        onDismissContents = viewModel::dismissContents,
        onRequestRestore = viewModel::requestRestore,
        onCancelRestore = viewModel::cancelRestore,
        onConfirmRestore = viewModel::confirmRestore,
        onDismissInvalid = viewModel::dismissInvalid,
        onStatusConsumed = viewModel::consumeStatus,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun BackupRestoreScreenContent(
    directory: String? = null,
    backups: List<String> = emptyList(),
    lastBackupName: String? = null,
    isAvailable: Boolean = true,
    status: BackupStatus? = null,
    contents: BackupContents? = null,
    pendingRestore: String? = null,
    invalid: Boolean = false,
    onChooseFolder: () -> Unit = {},
    onCreateBackup: () -> Unit = {},
    onViewContents: (String) -> Unit = {},
    onDismissContents: () -> Unit = {},
    onRequestRestore: (String) -> Unit = {},
    onCancelRestore: () -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onDismissInvalid: () -> Unit = {},
    onStatusConsumed: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(status) {
        val current = status ?: return@LaunchedEffect
        val message = when (current) {
            is BackupStatus.Created ->
                getString(Res.string.backup_created_status, current.fileName)

            BackupStatus.CreateFailed -> getString(Res.string.backup_create_failed)
            BackupStatus.RestoreComplete -> getString(Res.string.backup_restore_complete)
            BackupStatus.RestoreFailed -> getString(Res.string.backup_restore_failed)
        }
        snackbarHostState.showSnackbar(message)
        onStatusConsumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.backup_center_title),
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = directory ?: stringResource(Res.string.backup_no_location),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChooseFolder) {
                    Text(stringResource(Res.string.backup_choose_location))
                }
                Button(onClick = onCreateBackup, enabled = isAvailable && directory != null) {
                    Text(stringResource(Res.string.backup_create_label))
                }
            }
            if (lastBackupName != null) {
                Text(
                    text = stringResource(Res.string.backup_last_created, lastBackupName),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            if (backups.isEmpty()) {
                Text(
                    text = stringResource(Res.string.backup_no_backups),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                backups.forEach { fileName ->
                    BackupRow(
                        fileName = fileName,
                        onViewContents = { onViewContents(fileName) },
                        onRestore = { onRequestRestore(fileName) }
                    )
                }
            }
        }
    }

    contents?.let { shown ->
        PlusDialog(
            title = stringResource(Res.string.backup_view_contents),
            text = stringResource(Res.string.backup_contents_file_count, shown.entries.size),
            onDismiss = onDismissContents
        ) {
            Text(
                text = stringResource(Res.string.backup_contains_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            shown.entries.forEach { (name, byteCount) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = name, modifier = Modifier.weight(1f))
                    Text(text = byteCount.toString())
                }
            }
        }
    }

    pendingRestore?.let {
        PlusDialog(
            title = stringResource(Res.string.backup_restore_confirm_title),
            text = stringResource(Res.string.backup_restore_confirm_message),
            confirmLabel = stringResource(Res.string.backup_restore_label),
            onConfirm = onConfirmRestore,
            onDismiss = onCancelRestore,
            destructive = true
        )
    }

    if (invalid) {
        PlusDialog(
            title = stringResource(Res.string.backup_invalid_title),
            text = stringResource(Res.string.backup_invalid_message),
            onDismiss = onDismissInvalid
        )
    }
}

@Composable
private fun BackupRow(
    fileName: String,
    onViewContents: () -> Unit,
    onRestore: () -> Unit
) {
    Column {
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onViewContents) {
                Text(stringResource(Res.string.backup_view_contents))
            }
            TextButton(onClick = onRestore) {
                Text(stringResource(Res.string.backup_restore_label))
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun BackupRestoreScreenPreview() {
    BackupRestoreScreenContent(
        directory = "/home/user/backups",
        backups = listOf(
            "npp-backup-2026-10-11-09-15-00.nppbackup",
            "npp-backup-2026-10-10-18-02-11.nppbackup"
        ),
        lastBackupName = "npp-backup-2026-10-11-09-15-00.nppbackup"
    )
}
