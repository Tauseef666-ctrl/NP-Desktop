/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.PlusDialog
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.PlusSnackbarHost
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.player.RecentlyPlayedEntry
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.util.formatDuration
import net.newpipe.app.viewmodel.history.HistoryViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.history_clear_all_summary
import newpipe.shared.generated.resources.history_clear_all_title
import newpipe.shared.generated.resources.history_cleared
import newpipe.shared.generated.resources.history_empty_message
import newpipe.shared.generated.resources.history_empty_title
import newpipe.shared.generated.resources.history_group_earlier
import newpipe.shared.generated.resources.history_group_this_week
import newpipe.shared.generated.resources.history_group_today
import newpipe.shared.generated.resources.history_group_yesterday
import newpipe.shared.generated.resources.history_remove
import newpipe.shared.generated.resources.history_screen_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * History destination: the real recently-played list, grouped by day, with per-item removal and
 * clear-all behind a destructive confirmation.
 */
@Composable
fun HistoryScreen(
    navigator: Navigator = koinInject(),
    viewModel: HistoryViewModel = koinViewModel()
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val isEmpty by viewModel.isEmpty.collectAsStateWithLifecycle()

    HistoryScreenContent(
        groups = groups,
        isEmpty = isEmpty,
        onPlay = viewModel::play,
        onRemove = viewModel::remove,
        onClearAll = viewModel::clearAll,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun HistoryScreenContent(
    groups: List<HistoryGroup> = emptyList(),
    isEmpty: Boolean = true,
    onPlay: (RecentlyPlayedEntry) -> Unit = {},
    onRemove: (String) -> Unit = {},
    onClearAll: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clearedMessage = stringResource(Res.string.history_cleared)
    var pendingClear by remember { mutableStateOf(false) }

    PlusScaffold(
        toolbar = {
            TopToolbar(
                title = stringResource(Res.string.history_screen_title),
                onBack = onNavigateUp,
                actions = {
                    if (!isEmpty) {
                        TextButton(onClick = { pendingClear = true }) {
                            Text(stringResource(Res.string.history_clear_all_title))
                        }
                    }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = spaceNormal)
            ) {
                if (isEmpty) {
                    EmptyState(
                        title = stringResource(Res.string.history_empty_title),
                        message = stringResource(Res.string.history_empty_message)
                    )
                } else {
                    groups.forEach { group ->
                        SectionHeader(title = historyBucketTitle(group.bucket))
                        group.entries.forEach { entry ->
                            HistoryRow(
                                entry = entry,
                                onPlay = onPlay,
                                onRemove = onRemove
                            )
                        }
                    }
                }
            }
            PlusSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (pendingClear) {
        PlusDialog(
            title = stringResource(Res.string.history_clear_all_title),
            text = stringResource(Res.string.history_clear_all_summary),
            confirmLabel = stringResource(Res.string.history_clear_all_title),
            onConfirm = {
                onClearAll()
                pendingClear = false
                scope.launch { snackbarHostState.showSnackbar(clearedMessage) }
            },
            onDismiss = { pendingClear = false },
            destructive = true
        )
    }
}

@Composable
private fun historyBucketTitle(bucket: HistoryBucket): String = when (bucket) {
    HistoryBucket.TODAY -> stringResource(Res.string.history_group_today)
    HistoryBucket.YESTERDAY -> stringResource(Res.string.history_group_yesterday)
    HistoryBucket.THIS_WEEK -> stringResource(Res.string.history_group_this_week)
    HistoryBucket.EARLIER -> stringResource(Res.string.history_group_earlier)
}

@Composable
private fun HistoryRow(
    entry: RecentlyPlayedEntry,
    onPlay: (RecentlyPlayedEntry) -> Unit,
    onRemove: (String) -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay(entry) },
        headlineContent = {
            Text(
                text = entry.title.ifBlank { entry.id },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = if (entry.durationMs > 0L) {
            { Text(text = formatDuration(entry.durationMs / 1000L)) }
        } else {
            null
        },
        trailingContent = {
            TextButton(onClick = { onRemove(entry.id) }) {
                Text(text = stringResource(Res.string.history_remove))
            }
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun HistoryScreenPreview() {
    HistoryScreenContent(
        groups = listOf(
            HistoryGroup(
                bucket = HistoryBucket.TODAY,
                entries = listOf(
                    RecentlyPlayedEntry(id = "a", title = "Morning mix", url = "file:///a", durationMs = 185_000L),
                    RecentlyPlayedEntry(id = "b", url = "file:///b")
                )
            ),
            HistoryGroup(
                bucket = HistoryBucket.EARLIER,
                entries = listOf(
                    RecentlyPlayedEntry(
                        id = "c",
                        title = "Old favourite",
                        url = "file:///c",
                        durationMs = 3_725_000L
                    )
                )
            )
        ),
        isEmpty = false
    )
}