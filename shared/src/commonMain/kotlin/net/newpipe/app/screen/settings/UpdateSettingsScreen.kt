/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.UpdateActions
import net.newpipe.app.platform.UpdateStatus
import org.koin.compose.koinInject
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.settings_category_updates_title
import org.jetbrains.compose.resources.stringResource

/**
 * The update settings of the desktop build: a manual check against the release channel of the
 * app, a status line for its result and the elevated installer run of an available update.
 */
@Composable
fun UpdateSettingsScreen(
    navigator: Navigator = koinInject(),
    updater: UpdateActions = koinInject()
) {
    val status by updater.status.collectAsStateWithLifecycle()

    UpdateSettingsScreenContent(
        status = status,
        currentVersion = updater.currentVersion,
        onCheckNow = updater::checkManually,
        onInstall = updater::install,
        onNavigateUp = { navigator.navigateUp() }
    )
}

/**
 * The content of the update settings, split out for the previews.
 */
@Composable
fun UpdateSettingsScreenContent(
    status: UpdateStatus = UpdateStatus.Idle,
    currentVersion: String? = null,
    onCheckNow: () -> Unit = {},
    onInstall: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_updates_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .fillMaxHeight()
        ) {
            PreferenceRow(
                title = "Check for updates now",
                summary = buildStatusSummary(status, currentVersion),
                enabled = !isBusy(status),
                onClick = onCheckNow
            )
            if (status is UpdateStatus.Available) {
                PreferenceRow(
                    title = "Download and install update v${status.update.version}",
                    summary = "The installer is started with administrator rights: Windows " +
                        "will show the User Account Control prompt — click Yes to allow the update.",
                    onClick = onInstall
                )
            }
            if (currentVersion != null) {
                Text(
                    text = "Current version: $currentVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Text(
                text = "The update is downloaded from the release page of NP Desktop and " +
                    "installed through the Windows installer. When the installer starts, " +
                    "Windows will show the User Account Control prompt — click Yes to allow " +
                    "the update.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * Builds the one-line summary under the check row from the current updater state.
 */
private fun buildStatusSummary(status: UpdateStatus, currentVersion: String?): String = when (
    status
) {
    UpdateStatus.Idle -> when {
        currentVersion != null -> "You are running NP Desktop $currentVersion"
        else -> "Check the release channel for a newer version"
    }

    UpdateStatus.Checking -> "Checking for a newer version\u2026"
    is UpdateStatus.Available -> "Version v${status.update.version} is available"
    UpdateStatus.UpToDate ->
        "You are already running the latest version" + currentVersion?.let { " ($it)" }.orEmpty()

    UpdateStatus.NotAvailable ->
        "The latest release does not ship a Windows installer yet"

    UpdateStatus.Downloading -> "Downloading the installer\u2026"
    UpdateStatus.Installing -> "Starting the installer\u2026"
    is UpdateStatus.Installed ->
        "The installer is running. Finish the setup and restart NP Desktop."

    is UpdateStatus.UpdateFailed -> status.message
    is UpdateStatus.Error -> status.message
}

/**
 * Whether the updater is in a state where a second check cannot run.
 */
private fun isBusy(status: UpdateStatus): Boolean = status is UpdateStatus.Checking ||
    status is UpdateStatus.Downloading ||
    status is UpdateStatus.Installing
