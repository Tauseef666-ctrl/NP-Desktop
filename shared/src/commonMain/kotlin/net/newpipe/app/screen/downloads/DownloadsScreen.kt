/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.DownloadItem
import net.newpipe.app.component.DownloadStatus
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.ErrorState
import net.newpipe.app.component.FilterChipRow
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.download.DownloadFormat
import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.download.isReorderable
import net.newpipe.app.download.reorder
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.chooseDirectory
import net.newpipe.app.platform.openDownloadedFile
import net.newpipe.app.platform.openDownloadedFileFolder
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.util.formatBytes
import net.newpipe.app.viewmodel.downloads.DownloadsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.downloads_duplicate_message
import newpipe.shared.generated.resources.downloads_duplicate_title
import newpipe.shared.generated.resources.downloads_format_hint
import newpipe.shared.generated.resources.downloads_open_file
import newpipe.shared.generated.resources.downloads_open_folder
import newpipe.shared.generated.resources.downloads_quality_high
import newpipe.shared.generated.resources.downloads_quality_low
import newpipe.shared.generated.resources.downloads_quality_medium
import newpipe.shared.generated.resources.downloads_quality_original
import newpipe.shared.generated.resources.downloads_reorder_hint
import newpipe.shared.generated.resources.downloads_reorder_move_down
import newpipe.shared.generated.resources.downloads_reorder_move_up
import newpipe.shared.generated.resources.downloads_subtitle_hint
import newpipe.shared.generated.resources.downloads_subtitle_language_label
import newpipe.shared.generated.resources.downloads_subtitle_url_label
import newpipe.shared.generated.resources.downloads_tab_all
import newpipe.shared.generated.resources.downloads_tab_completed
import newpipe.shared.generated.resources.downloads_tab_downloading
import newpipe.shared.generated.resources.downloads_tab_empty_message
import newpipe.shared.generated.resources.downloads_tab_empty_title
import newpipe.shared.generated.resources.downloads_tab_failed
import newpipe.shared.generated.resources.downloads_tab_paused
import newpipe.shared.generated.resources.downloads_tab_queued
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DownloadsScreen(
    navigator: Navigator = koinInject(),
    viewModel: DownloadsViewModel = koinViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val duplicateRejected by viewModel.lastAddRejectedDuplicate.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf(DownloadCategory.ALL) }
    DownloadsScreenContent(
        tasks = tasks,
        selectedCategory = selectedCategory,
        onCategorySelect = { selectedCategory = it },
        isEngineAvailable = viewModel.isEngineAvailable,
        defaultDirectory = viewModel.defaultDirectory.collectAsStateWithLifecycle().value,
        lastAddRejectedDuplicate = duplicateRejected,
        onAdd = { url, folder, format, subtitleUrl, subtitleLanguage ->
            viewModel.addFromUrl(url, folder, format, subtitleUrl, subtitleLanguage)
        },
        onBrowseFolder = { chooseDirectory() },
        onDismissDuplicate = viewModel::dismissDuplicate,
        onReorder = viewModel::reorder,
        onStart = viewModel::start,
        onPause = viewModel::pause,
        onCancel = viewModel::cancel,
        onRemove = viewModel::remove,
        onClearCompleted = viewModel::clearCompleted,
        onOpenFile = { task -> openDownloadedFile(task.filePath ?: task.request.destinationPath) },
        onOpenFolder = { task -> openDownloadedFileFolder(task.filePath ?: task.request.destinationPath) },
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun DownloadsScreenContent(
    tasks: List<DownloadTask> = emptyList(),
    selectedCategory: DownloadCategory = DownloadCategory.ALL,
    onCategorySelect: (DownloadCategory) -> Unit = {},
    isEngineAvailable: Boolean = true,
    defaultDirectory: String = "",
    lastAddRejectedDuplicate: Boolean = false,
    onAdd: (String, String, DownloadFormat, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onBrowseFolder: () -> String? = { null },
    onDismissDuplicate: () -> Unit = {},
    onReorder: (Int, Int) -> Unit = { _, _ -> },
    onStart: (String) -> Unit = {},
    onPause: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    onRemove: (String) -> Unit = {},
    onClearCompleted: () -> Unit = {},
    onOpenFile: (DownloadTask) -> Unit = {},
    onOpenFolder: (DownloadTask) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val colors = plusColors()
    PlusScaffold(
        toolbar = {
            TopToolbar(
                title = "Downloads",
                subtitle = when {
                    tasks.isEmpty() -> null
                    else -> "${tasks.count { it.status == DownloadState.DOWNLOADING }} active • ${tasks.size} total"
                },
                onBack = onNavigateUp,
                actions = {
                    if (tasks.any { it.status == DownloadState.COMPLETED }) {
                        TextButton(onClick = onClearCompleted) { Text("Clear completed") }
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = spaceNormal)
        ) {
            NewDownloadSection(
                defaultDirectory = defaultDirectory,
                enabled = isEngineAvailable,
                lastAddRejectedDuplicate = lastAddRejectedDuplicate,
                onDismissDuplicate = onDismissDuplicate,
                onBrowseFolder = onBrowseFolder,
                onAdd = onAdd
            )
            val tabs = DownloadCategory.tabsInOrder
            val counts = tabCounts(tasks)
            val tabLabels = mutableListOf<String>()
            for (category in tabs) {
                tabLabels += "${category.label()} (${counts[category] ?: 0})"
            }
            FilterChipRow(
                options = tabLabels,
                selectedIndex = tabs.indexOf(selectedCategory),
                onSelect = { index -> onCategorySelect(tabs[index]) }
            )
            SectionHeader(title = "Missions")
            if (tasks.any { it.isReorderable() }) {
                Text(
                    text = stringResource(Res.string.downloads_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = spaceNormal, vertical = spaceSmall)
                )
            }
            val visibleTasks = selectedCategory.select(tasks)
            when {
                !isEngineAvailable -> ErrorState(
                    message = "Downloads are not available on this platform yet."
                )
                tasks.isEmpty() -> EmptyState(
                    title = "No downloads yet",
                    message = "Add a URL above and it will be transferred to your chosen folder."
                )
                visibleTasks.isEmpty() -> EmptyState(
                    title = stringResource(Res.string.downloads_tab_empty_title),
                    message = stringResource(Res.string.downloads_tab_empty_message)
                )
                else -> visibleTasks.forEach { task ->
                    val index = tasks.indexOfFirst { it.id == task.id }
                    val canMoveUp = index > 0 && reorder(tasks, index, index - 1) != tasks
                    val canMoveDown = reorder(tasks, index, index + 1) != tasks
                    DownloadRow(
                        task = task,
                        canMoveUp = canMoveUp,
                        canMoveDown = canMoveDown,
                        onMoveUp = { if (canMoveUp) onReorder(index, index - 1) },
                        onMoveDown = { if (canMoveDown) onReorder(index, index + 1) },
                        onStart = onStart,
                        onPause = onPause,
                        onCancel = onCancel,
                        onRemove = onRemove,
                        onOpenFile = onOpenFile,
                        onOpenFolder = onOpenFolder
                    )
                }
            }
            Spacer(Modifier.height(spaceNormal))
        }
    }
}

@Composable
private fun NewDownloadSection(
    defaultDirectory: String,
    enabled: Boolean,
    lastAddRejectedDuplicate: Boolean,
    onDismissDuplicate: () -> Unit,
    onBrowseFolder: () -> String?,
    onAdd: (String, String, DownloadFormat, String?, String?) -> Unit
) {
    val colors = plusColors()
    var url by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf(defaultDirectory) }
    var format by remember { mutableStateOf(DownloadFormat.ORIGINAL) }
    var subtitleUrl by remember { mutableStateOf("") }
    var subtitleLanguage by remember { mutableStateOf("") }

    SectionHeader(title = "New download")
    Text(
        text = if (defaultDirectory.isNotBlank()) {
            "Files are saved under $defaultDirectory. The engine writes a .part file while downloading."
        } else {
            "Enter a destination folder to save the downloaded file."
        },
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = spaceNormal, vertical = spaceSmall)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spaceNormal, vertical = spaceNormal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spaceNormal)
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                if (lastAddRejectedDuplicate) onDismissDuplicate()
            },
            singleLine = true,
            label = { Text("File URL") },
            modifier = Modifier.weight(1f)
        )
    }
    val formatLabels = mutableListOf<String>()
    for (option in DownloadFormat.entries) {
        formatLabels += option.label()
    }
    FilterChipRow(
        options = formatLabels,
        selectedIndex = format.ordinal,
        onSelect = { index -> format = DownloadFormat.entries[index] },
        contentPadding = PaddingValues(horizontal = spaceNormal)
    )
    Text(
        text = stringResource(Res.string.downloads_format_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = spaceNormal, vertical = spaceSmall)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spaceNormal, vertical = spaceSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spaceNormal)
    ) {
        OutlinedTextField(
            value = subtitleUrl,
            onValueChange = { subtitleUrl = it },
            singleLine = true,
            label = { Text(stringResource(Res.string.downloads_subtitle_url_label)) },
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = subtitleLanguage,
            onValueChange = { subtitleLanguage = it },
            singleLine = true,
            label = { Text(stringResource(Res.string.downloads_subtitle_language_label)) },
            modifier = Modifier.width(180.dp)
        )
    }
    Text(
        text = stringResource(Res.string.downloads_subtitle_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = spaceNormal, vertical = spaceSmall)
    )
    if (lastAddRejectedDuplicate) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spaceNormal, vertical = spaceSmall)
        ) {
            Text(
                text = stringResource(Res.string.downloads_duplicate_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.danger
            )
            Text(
                text = stringResource(Res.string.downloads_duplicate_message),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spaceNormal, vertical = spaceNormal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spaceNormal)
    ) {
        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            singleLine = true,
            label = { Text("Destination folder") },
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = {
                val picked = onBrowseFolder()
                if (!picked.isNullOrBlank()) destination = picked
            },
            enabled = enabled
        ) {
            Text("Browse…")
        }
        Button(
            onClick = {
                onAdd(
                    url.trim(),
                    destination.ifBlank { defaultDirectory },
                    format,
                    subtitleUrl.trim().ifBlank { null },
                    subtitleLanguage.trim().ifBlank { null }
                )
            },
            enabled = enabled && url.isNotBlank() && destination.isNotBlank()
        ) {
            Text("Download")
        }
    }
}

@Composable
private fun DownloadFormat.label(): String = stringResource(
    when (this) {
        DownloadFormat.ORIGINAL -> Res.string.downloads_quality_original
        DownloadFormat.HIGH -> Res.string.downloads_quality_high
        DownloadFormat.MEDIUM -> Res.string.downloads_quality_medium
        DownloadFormat.LOW -> Res.string.downloads_quality_low
    }
)

@Composable
private fun DownloadRow(
    task: DownloadTask,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onStart: (String) -> Unit,
    onPause: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    onOpenFile: (DownloadTask) -> Unit,
    onOpenFolder: (DownloadTask) -> Unit
) {
    val progress = if (task.totalBytes > 0L) task.progress else null
    val sizeText = buildString {
        append(formatBytes(task.downloadedBytes))
        if (task.totalBytes > 0L) append(" / ").append(formatBytes(task.totalBytes))
    }
    DownloadItem(
        title = task.request.title.ifBlank { task.request.url },
        status = task.status.toUiStatus(),
        subtitle = task.request.url,
        progress = if (task.status == DownloadState.DOWNLOADING) progress else null,
        speedText = if (task.bytesPerSecond > 0L) "${formatBytes(task.bytesPerSecond)}/s" else null,
        sizeText = sizeText,
        actions = {
            if (task.isReorderable()) {
                TextButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Text(stringResource(Res.string.downloads_reorder_move_up))
                }
                TextButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Text(stringResource(Res.string.downloads_reorder_move_down))
                }
            }
            when (task.status) {
                DownloadState.DOWNLOADING -> TextButton(onClick = { onPause(task.id) }) { Text("Pause") }
                DownloadState.PAUSED, DownloadState.QUEUED -> TextButton(onClick = { onStart(task.id) }) { Text("Resume") }
                DownloadState.FAILED -> TextButton(onClick = { onStart(task.id) }) { Text("Retry") }
                DownloadState.COMPLETED -> {
                    TextButton(onClick = { onOpenFile(task) }) { Text(stringResource(Res.string.downloads_open_file)) }
                    TextButton(onClick = { onOpenFolder(task) }) { Text(stringResource(Res.string.downloads_open_folder)) }
                    TextButton(onClick = { onRemove(task.id) }) { Text("Remove") }
                }
            }
            if (!task.isTerminal && task.status != DownloadState.QUEUED) {
                TextButton(onClick = { onCancel(task.id) }) { Text("Cancel") }
            }
        }
    )
}

private fun DownloadState.toUiStatus(): DownloadStatus = when (this) {
    DownloadState.QUEUED -> DownloadStatus.QUEUED
    DownloadState.DOWNLOADING -> DownloadStatus.DOWNLOADING
    DownloadState.PAUSED -> DownloadStatus.PAUSED
    DownloadState.COMPLETED -> DownloadStatus.COMPLETED
    DownloadState.FAILED -> DownloadStatus.FAILED
}

@Composable
private fun DownloadCategory.label(): String = stringResource(
    when (this) {
        DownloadCategory.ALL -> Res.string.downloads_tab_all
        DownloadCategory.QUEUED -> Res.string.downloads_tab_queued
        DownloadCategory.DOWNLOADING -> Res.string.downloads_tab_downloading
        DownloadCategory.PAUSED -> Res.string.downloads_tab_paused
        DownloadCategory.COMPLETED -> Res.string.downloads_tab_completed
        DownloadCategory.FAILED -> Res.string.downloads_tab_failed
    }
)

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun DownloadsScreenPreview() {
    DownloadsScreenContent(
        tasks = listOf(
            DownloadTask(
                request = net.newpipe.app.download.DownloadRequest(
                    id = "1",
                    url = "https://example.org/demo.mp4",
                    destinationPath = "/home/user/Downloads/demo.mp4",
                    title = "demo.mp4"
                ),
                status = DownloadState.DOWNLOADING,
                downloadedBytes = 12_400_000,
                totalBytes = 29_600_000,
                bytesPerSecond = 1_200_000
            ),
            DownloadTask(
                request = net.newpipe.app.download.DownloadRequest(
                    id = "2",
                    url = "https://example.org/track.mp3",
                    destinationPath = "/home/user/Downloads/track.mp3",
                    title = "track.mp3"
                ),
                status = DownloadState.COMPLETED,
                downloadedBytes = 41_200_000,
                totalBytes = 42_500_000,
                filePath = "/home/user/Downloads/track.mp3"
            )
        )
    )
}