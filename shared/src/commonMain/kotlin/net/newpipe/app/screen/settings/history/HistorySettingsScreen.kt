/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.history

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.component.PlusDialog
import net.newpipe.app.component.PlusSnackbarHost
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.HistorySettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.history_settings_clear_history
import newpipe.shared.generated.resources.history_settings_clear_resume
import newpipe.shared.generated.resources.history_settings_cleared
import newpipe.shared.generated.resources.history_settings_items_count
import newpipe.shared.generated.resources.history_settings_remember
import newpipe.shared.generated.resources.history_settings_remember_summary
import newpipe.shared.generated.resources.history_settings_resume_toggle
import newpipe.shared.generated.resources.history_settings_title
import newpipe.shared.generated.resources.settings_category_history_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** What a pending destructive confirmation refers to; null means no dialog is shown. */
private enum class PendingClear { HISTORY, RESUME }

@Composable
fun HistorySettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: HistorySettingsViewModel = koinViewModel()
) {
    val watchHistoryEnabled by viewModel.watchHistoryEnabled.collectAsStateWithLifecycle()
    val resumeEnabled by viewModel.resumeEnabled.collectAsStateWithLifecycle()
    val historyCount by viewModel.historyCount.collectAsStateWithLifecycle()
    val resumeCount by viewModel.resumeCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    HistorySettingsScreenContent(
        watchHistoryEnabled = watchHistoryEnabled,
        resumeEnabled = resumeEnabled,
        historyCount = historyCount,
        resumeCount = resumeCount,
        onWatchHistoryChange = viewModel::setWatchHistoryEnabled,
        onResumeChange = viewModel::setResumeEnabled,
        onClearHistory = viewModel::clearWatchHistory,
        onClearResume = viewModel::clearResumePositions,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun HistorySettingsScreenContent(
    watchHistoryEnabled: Boolean = true,
    resumeEnabled: Boolean = true,
    historyCount: Int = 0,
    resumeCount: Int = 0,
    onWatchHistoryChange: (Boolean) -> Unit = {},
    onResumeChange: (Boolean) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onClearResume: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clearedMessage = stringResource(Res.string.history_settings_cleared)
    var pendingClear by remember { mutableStateOf<PendingClear?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.history_settings_title),
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
            PreferenceCategoryTitle(stringResource(Res.string.settings_category_history_title))
            SwitchPreference(
                title = stringResource(Res.string.history_settings_remember),
                summary = stringResource(Res.string.history_settings_remember_summary),
                checked = watchHistoryEnabled,
                onCheckedChange = onWatchHistoryChange
            )
            SwitchPreference(
                title = stringResource(Res.string.history_settings_resume_toggle),
                checked = resumeEnabled,
                onCheckedChange = onResumeChange
            )
            PreferenceRow(
                title = stringResource(Res.string.history_settings_clear_history),
                summary = stringResource(Res.string.history_settings_items_count, historyCount),
                enabled = historyCount > 0,
                onClick = { pendingClear = PendingClear.HISTORY }
            )
            PreferenceRow(
                title = stringResource(Res.string.history_settings_clear_resume),
                summary = stringResource(Res.string.history_settings_items_count, resumeCount),
                enabled = resumeCount > 0,
                onClick = { pendingClear = PendingClear.RESUME }
            )
        }
    }

    when (pendingClear) {
        PendingClear.HISTORY -> PlusDialog(
            title = stringResource(Res.string.history_settings_clear_history),
            text = stringResource(Res.string.history_settings_items_count, historyCount),
            confirmLabel = stringResource(Res.string.history_settings_clear_history),
            onConfirm = {
                onClearHistory()
                pendingClear = null
                scope.launch { snackbarHostState.showSnackbar(clearedMessage) }
            },
            onDismiss = { pendingClear = null },
            destructive = true
        )

        PendingClear.RESUME -> PlusDialog(
            title = stringResource(Res.string.history_settings_clear_resume),
            text = stringResource(Res.string.history_settings_items_count, resumeCount),
            confirmLabel = stringResource(Res.string.history_settings_clear_resume),
            onConfirm = {
                onClearResume()
                pendingClear = null
                scope.launch { snackbarHostState.showSnackbar(clearedMessage) }
            },
            onDismiss = { pendingClear = null },
            destructive = true
        )

        null -> Unit
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun HistorySettingsScreenPreview() {
    HistorySettingsScreenContent(
        watchHistoryEnabled = true,
        resumeEnabled = true,
        historyCount = 12,
        resumeCount = 3
    )
}
