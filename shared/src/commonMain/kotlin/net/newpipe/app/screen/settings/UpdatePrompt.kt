/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import net.newpipe.app.platform.UpdateActions
import net.newpipe.app.platform.UpdateInfo
import net.newpipe.app.platform.UpdateProgress
import net.newpipe.app.platform.UpdateStatus
import net.newpipe.app.ui.Glass
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import org.koin.compose.koinInject

/**
 * The silent delay before the startup check, so the app is fully up before the release channel
 * is asked for a newer version.
 */
private const val UPDATE_STARTUP_DELAY_MS = 15_000L

private val UPDATE_SHAPE: Shape = RoundedCornerShape(Radius.xl)

/**
 * The in-app update prompt: a silent background check runs shortly after the startup, and when a
 * newer release with a Windows installer exists the dialog asks for permission to install it.
 * The elevation with the User Account Control prompt is expected and told in every dialog text
 * that leads to the installer.
 */
@Composable
fun UpdatePrompt(updater: UpdateActions = koinInject()) {
    val status by updater.status.collectAsState()
    val progress by updater.progress.collectAsState()

    // The startup check: async, delayed past the app warm up and fully silent on every failure
    LaunchedEffect(updater) {
        delay(UPDATE_STARTUP_DELAY_MS)
        updater.checkOnStartup()
    }

    when (val current = status) {
        is UpdateStatus.Available -> UpdateDialog(
            state = current,
            updater = updater
        )

        UpdateStatus.Downloading -> UpdateDialog(
            state = UpdateStatus.Downloading,
            progress = progress,
            updater = updater
        )

        UpdateStatus.Installing -> UpdateDialog(
            state = UpdateStatus.Installing,
            updater = updater
        )

        is UpdateStatus.Installed -> UpdateDialog(
            state = current,
            updater = updater
        )

        is UpdateStatus.UpdateFailed -> UpdateDialog(
            state = current,
            updater = updater
        )

        else -> Unit
    }
}

/**
 * The frosted glass dialog of the updater with the texts and buttons of every phase.
 *
 * @param state the active updater state the dialog renders
 * @param updater the platform updater that receives the user actions
 * @param progress the live download progress, only read in the download phase
 */
@Composable
private fun UpdateDialog(
    state: UpdateStatus,
    updater: UpdateActions,
    progress: UpdateProgress? = null
) {
    Dialog(
        onDismissRequest = updater::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 420.dp, max = 520.dp),
            shape = UPDATE_SHAPE,
            color = Glass.color(0.99f),
            // The glass color is translucent, so contentColorFor returns unspecified and the
            // texts fall back to raw black: pin the content color explicitly
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
            border = Glass.borderStroke()
        ) {
            Column(
                modifier = Modifier
                    .background(Glass.shine)
                    .padding(Spacing.lg)
            ) {
                when (state) {
                    is UpdateStatus.Available -> AvailableContent(state.update, updater)
                    UpdateStatus.Downloading -> DownloadingContent(progress, updater::dismiss)
                    UpdateStatus.Installing -> InstallingContent(updater::dismiss)
                    is UpdateStatus.Installed -> InstalledContent(updater::dismiss)
                    is UpdateStatus.UpdateFailed -> FailedContent(state, updater::dismiss)
                    // The dialog only exists for the states above
                    else -> Unit
                }
            }
        }
    }
}

/**
 * The question of the available update with the three reply buttons of the specification.
 */
@Composable
private fun AvailableContent(
    update: UpdateInfo,
    updater: UpdateActions
) {
    Text(
        text = "Update available",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = "A new version is available (v${update.version}). Download and install now?",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm)
    )
    Text(
        text = "Windows will show the User Account Control prompt — click Yes to allow the " +
            "update.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = updater::skip) {
            Text(
                text = "Skip this version",
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TextButton(onClick = updater::dismiss) {
            Text(
                text = "Remind me next time",
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TextButton(onClick = updater::install) {
            Text(text = "Update now")
        }
    }
}

/**
 * The download of the installer with a linear progress line; the download itself keeps running
 * when the dialog is closed, so the close stays available in every phase.
 */
@Composable
private fun DownloadingContent(
    progress: UpdateProgress?,
    onClose: () -> Unit
) {
    Text(
        text = "Downloading update",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = progressLabel(progress),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm)
    )
    val total = progress?.totalBytes
    if (total != null && total > 0) {
        LinearProgressIndicator(
            progress = { (progress.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md)
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md)
        )
    }
    CloseButton(onClose)
}

/**
 * The phase between the finished download and the elevated installer start.
 */
@Composable
private fun InstallingContent(onClose: () -> Unit) {
    Text(
        text = "Starting the installer",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = "The installer is starting. Windows will show the User Account Control prompt " +
            "— click Yes to allow the update.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm)
    )
    CloseButton(onClose)
}

/**
 * The installer runs outside the app: the user finishes the update and restarts the app.
 */
@Composable
private fun InstalledContent(onClose: () -> Unit) {
    Text(
        text = "Update started",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = "The installer is running. Complete the setup and restart NP Desktop to " +
            "finish the update.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm)
    )
    CloseButton(onClose)
}

/**
 * A cancelled or failed installation; the reason stays readable and the user can retry later
 * from the update category of the settings.
 */
@Composable
private fun FailedContent(
    state: UpdateStatus.UpdateFailed,
    onClose: () -> Unit
) {
    Text(
        text = "The update could not be installed",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = state.message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm)
    )
    CloseButton(onClose)
}

/**
 * The trailing close button of the passive dialog phases.
 */
@Composable
private fun CloseButton(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onClose) {
            Text(
                text = "Close",
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * The one-line size info of the running download.
 */
private fun progressLabel(progress: UpdateProgress?): String {
    val total = progress?.totalBytes
    return when {
        progress == null -> "Downloading the installer\u2026"
        total != null && total > 0 ->
            "Downloading\u2026 ${formatBytes(progress.bytesDownloaded)} of ${formatBytes(total)}"

        else -> "Downloading\u2026 ${formatBytes(progress.bytesDownloaded)}"
    }
}

/**
 * Formats a byte count as the compact single unit label the dialogs show.
 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format("%.0f kB", bytes / 1_024.0)
    else -> "$bytes B"
}
