/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.ErrorState
import net.newpipe.app.component.FilterChipRow
import net.newpipe.app.component.LoadingState
import net.newpipe.app.component.LocalMediaRow
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.SearchBarField
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.component.ToolbarIconButton
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.player.LocalMediaFile
import net.newpipe.app.player.localMediaMetaLine
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.theme.spaceXSmall
import net.newpipe.app.util.formatBytes
import net.newpipe.app.util.formatDuration
import net.newpipe.app.viewmodel.library.LibraryViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.continue_watching_empty_message
import newpipe.shared.generated.resources.continue_watching_empty_title
import newpipe.shared.generated.resources.downloaded_empty_message
import newpipe.shared.generated.resources.downloaded_empty_title
import newpipe.shared.generated.resources.ic_cloud_download
import newpipe.shared.generated.resources.library_filter_all
import newpipe.shared.generated.resources.library_filter_audio
import newpipe.shared.generated.resources.library_filter_videos
import newpipe.shared.generated.resources.library_no_matches_message
import newpipe.shared.generated.resources.library_no_matches_title
import newpipe.shared.generated.resources.library_recently_played_cap
import newpipe.shared.generated.resources.library_search_placeholder
import newpipe.shared.generated.resources.library_sort_date
import newpipe.shared.generated.resources.library_sort_name
import newpipe.shared.generated.resources.library_sort_size
import newpipe.shared.generated.resources.library_view_continue_watching
import newpipe.shared.generated.resources.library_view_downloaded
import newpipe.shared.generated.resources.library_view_grid
import newpipe.shared.generated.resources.library_view_list
import newpipe.shared.generated.resources.library_view_recently_played
import newpipe.shared.generated.resources.recently_played_empty_message
import newpipe.shared.generated.resources.recently_played_empty_title
import newpipe.shared.generated.resources.library_view_videos
import newpipe.shared.generated.resources.library_view_audio
import newpipe.shared.generated.resources.library_view_folders
import newpipe.shared.generated.resources.library_view_playlists
import newpipe.shared.generated.resources.library_folder_root
import newpipe.shared.generated.resources.videos_empty_title
import newpipe.shared.generated.resources.videos_empty_message
import newpipe.shared.generated.resources.audio_empty_title
import newpipe.shared.generated.resources.audio_empty_message
import newpipe.shared.generated.resources.folders_empty_title
import newpipe.shared.generated.resources.folders_empty_message
import newpipe.shared.generated.resources.playlists_empty_title
import newpipe.shared.generated.resources.playlists_empty_message
import newpipe.shared.generated.resources.library_thumbnail_pending
import newpipe.shared.generated.resources.library_missing_file
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Test tag for the library search input, shared with the search UI test. */
const val TEST_TAG_LIBRARY_SEARCH = "TEST_TAG_LIBRARY_SEARCH"

@Composable
fun LibraryScreen(
    navigator: Navigator = koinInject(),
    viewModel: LibraryViewModel = koinViewModel()
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val recentEntries by viewModel.recentEntries.collectAsStateWithLifecycle()
    val resumeEntries by viewModel.resumeEntries.collectAsStateWithLifecycle()
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    var filters by remember { mutableStateOf(LibraryFilters()) }
    var libraryView by remember { mutableStateOf(LibraryViewTab.LOCAL) }

    LaunchedEffect(recentEntries) { viewModel.refreshResumeEntries() }

    val recentItems = remember(recentEntries) { recentlyPlayed(recentEntries) }
    val continueItems = remember(resumeEntries) {
        continueWatching(resumeEntries, System.currentTimeMillis())
    }
    val downloadedItems = remember(downloadTasks) { downloadedTasks(downloadTasks) }
    val videoItems = remember(items) { videoTracks(items) }
    val audioItems = remember(items) { audioTracks(items) }
    val folderGroupsList = remember(items) { folderGroups(items) }
    val playlistItems = remember(viewModel.savedPlaylists) { playlistTracks(viewModel.savedPlaylists) }

    LibraryScreenContent(
        folders = folders,
        items = items,
        isScanning = isScanning,
        scanError = scanError,
        isScannerAvailable = viewModel.isScannerAvailable,
        onAddFolder = viewModel::addFolder,
        onRemoveFolder = viewModel::removeFolder,
        onRescan = viewModel::rescan,
        onPlay = { item ->
            viewModel.play(item)
            navigator.navigateTo(Destination.Player)
        },
        onNavigateUp = { navigator.navigateUp() },
        filters = filters,
        onFiltersChange = { filters = it },
        libraryView = libraryView,
        onLibraryViewChange = { libraryView = it },
        recentlyPlayedItems = recentItems,
        continueWatchingItems = continueItems,
        downloadedItems = downloadedItems,
        videoItems = videoItems,
        audioItems = audioItems,
        folderGroupsList = folderGroupsList,
        playlistItems = playlistItems,
        onPlayDerived = { item ->
            viewModel.playDerived(item)
            navigator.navigateTo(Destination.Player)
        }
    )
}

@Composable
fun LibraryScreenContent(
    folders: List<String> = emptyList(),
    items: List<LocalMediaFile> = emptyList(),
    isScanning: Boolean = false,
    scanError: String? = null,
    isScannerAvailable: Boolean = true,
    onAddFolder: (String) -> Unit = {},
    onRemoveFolder: (String) -> Unit = {},
    onRescan: () -> Unit = {},
    onPlay: (LocalMediaFile) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    filters: LibraryFilters = LibraryFilters(),
    onFiltersChange: (LibraryFilters) -> Unit = {},
    libraryView: LibraryViewTab = LibraryViewTab.LOCAL,
    onLibraryViewChange: (LibraryViewTab) -> Unit = {},
    recentlyPlayedItems: List<DerivedViewItem> = emptyList(),
    continueWatchingItems: List<DerivedViewItem> = emptyList(),
    downloadedItems: List<DerivedViewItem> = emptyList(),
    videoItems: List<DerivedViewItem> = emptyList(),
    audioItems: List<DerivedViewItem> = emptyList(),
    folderGroupsList: List<FolderGroup> = emptyList(),
    playlistItems: List<DerivedViewItem> = emptyList(),
    onPlayDerived: (DerivedViewItem) -> Unit = {}
) {
    PlusScaffold(
        toolbar = {
            TopToolbar(
                title = "Library",
                subtitle = if (items.isEmpty()) null else "${items.size} items",
                onBack = onNavigateUp,
                actions = {
                    if (folders.isNotEmpty()) {
                        TextButton(onClick = onRescan, enabled = !isScanning) { Text("Rescan") }
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
            LibraryViewSelector(
                selected = libraryView,
                onSelect = onLibraryViewChange
            )
            when (libraryView) {
                LibraryViewTab.LOCAL -> {
                    if (!isScannerAvailable) {
                        ErrorState(
                            message = "Folder scanning is not available on this platform yet."
                        )
                    } else {
                        FolderSection(
                            folders = folders,
                            onAddFolder = onAddFolder,
                            onRemoveFolder = onRemoveFolder
                        )
                        LibraryFilterControls(
                            filters = filters,
                            onFiltersChange = onFiltersChange
                        )
                        SectionHeader(title = "Media")
                        val visible = applyFilters(items, filters)
                        when {
                            isScanning -> LoadingState(message = "Scanning indexed folders…")
                            items.isEmpty() -> EmptyState(
                                title = "No media found",
                                message = "Add a folder above, then tap Rescan to build your library."
                            )
                            visible.isEmpty() -> EmptyState(
                                title = stringResource(Res.string.library_no_matches_title),
                                message = stringResource(Res.string.library_no_matches_message)
                            )
                            filters.view == ViewMode.GRID -> MediaGrid(items = visible, onPlay = onPlay)
                            else -> visible.forEach { item ->
                                LocalMediaRow(
                                    title = item.name,
                                    subtitle = listOfNotNull(
                                        item.extension.uppercase(),
                                        localMediaMetaLine(item)
                                    ).joinToString(" • "),
                                    durationText = item.durationMs
                                        ?.takeIf { item.hasKnownDuration }
                                        ?.let { formatDuration(it / 1000) },
                                    sizeText = formatBytes(item.sizeBytes),
                                    isMissing = item.missing,
                                    onClick = { onPlay(item) }
                                )
                            }
                        }
                        if (scanError != null) {
                            ErrorState(message = scanError)
                        }
                    }
                }

                LibraryViewTab.RECENT -> DerivedViewList(
                    items = recentlyPlayedItems,
                    emptyTitle = stringResource(Res.string.recently_played_empty_title),
                    emptyMessage = stringResource(Res.string.recently_played_empty_message),
                    hint = stringResource(Res.string.library_recently_played_cap),
                    onPlay = onPlayDerived
                )

                LibraryViewTab.CONTINUE -> DerivedViewList(
                    items = continueWatchingItems,
                    emptyTitle = stringResource(Res.string.continue_watching_empty_title),
                    emptyMessage = stringResource(Res.string.continue_watching_empty_message),
                    onPlay = onPlayDerived
                )

                LibraryViewTab.DOWNLOADED -> DerivedViewList(
                    items = downloadedItems,
                    emptyTitle = stringResource(Res.string.downloaded_empty_title),
                    emptyMessage = stringResource(Res.string.downloaded_empty_message),
                    onPlay = onPlayDerived
                )

                LibraryViewTab.VIDEOS -> DerivedViewList(
                    items = videoItems,
                    emptyTitle = stringResource(Res.string.videos_empty_title),
                    emptyMessage = stringResource(Res.string.videos_empty_message),
                    onPlay = onPlayDerived
                )

                LibraryViewTab.AUDIO -> DerivedViewList(
                    items = audioItems,
                    emptyTitle = stringResource(Res.string.audio_empty_title),
                    emptyMessage = stringResource(Res.string.audio_empty_message),
                    onPlay = onPlayDerived
                )

                LibraryViewTab.FOLDERS -> FolderGroupList(
                    groups = folderGroupsList,
                    emptyTitle = stringResource(Res.string.folders_empty_title),
                    emptyMessage = stringResource(Res.string.folders_empty_message),
                    onPlay = onPlayDerived
                )

                LibraryViewTab.PLAYLISTS -> DerivedViewList(
                    items = playlistItems,
                    emptyTitle = stringResource(Res.string.playlists_empty_title),
                    emptyMessage = stringResource(Res.string.playlists_empty_message),
                    onPlay = onPlayDerived
                )
            }
            Spacer(Modifier.height(spaceNormal))
        }
    }
}

@Composable
private fun LibraryViewSelector(
    selected: LibraryViewTab,
    onSelect: (LibraryViewTab) -> Unit
) {
    val tabs = LibraryViewTab.entries
    FilterChipRow(
        options = tabs.map { libraryViewLabel(it) },
        selectedIndex = tabs.indexOf(selected),
        onSelect = { index -> onSelect(tabs[index]) }
    )
}

@Composable
private fun libraryViewLabel(tab: LibraryViewTab): String = when (tab) {
    LibraryViewTab.LOCAL -> "Local files"
    LibraryViewTab.RECENT -> stringResource(Res.string.library_view_recently_played)
    LibraryViewTab.CONTINUE -> stringResource(Res.string.library_view_continue_watching)
    LibraryViewTab.DOWNLOADED -> stringResource(Res.string.library_view_downloaded)
    LibraryViewTab.VIDEOS -> stringResource(Res.string.library_view_videos)
    LibraryViewTab.AUDIO -> stringResource(Res.string.library_view_audio)
    LibraryViewTab.FOLDERS -> stringResource(Res.string.library_view_folders)
    LibraryViewTab.PLAYLISTS -> stringResource(Res.string.library_view_playlists)
}

@Composable
private fun DerivedViewList(
    items: List<DerivedViewItem>,
    emptyTitle: String,
    emptyMessage: String,
    onPlay: (DerivedViewItem) -> Unit,
    hint: String? = null
) {
    val colors = plusColors()
    if (hint != null) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = spaceNormal, vertical = spaceSmall)
        )
    }
    if (items.isEmpty()) {
        EmptyState(title = emptyTitle, message = emptyMessage)
    } else {
        items.forEach { item ->
            LocalMediaRow(
                title = item.title,
                subtitle = item.subtitle.takeIf { it.isNotBlank() },
                showThumbnailPlaceholder = true,
                onClick = { onPlay(item) }
            )
        }
    }
}

@Composable
private fun FolderGroupList(
    groups: List<FolderGroup>,
    emptyTitle: String,
    emptyMessage: String,
    onPlay: (DerivedViewItem) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyState(title = emptyTitle, message = emptyMessage)
    } else {
        groups.forEach { group ->
            SectionHeader(title = group.folderName.ifBlank { stringResource(Res.string.library_folder_root) })
            group.items.forEach { item ->
                LocalMediaRow(
                    title = item.title,
                    subtitle = item.subtitle.takeIf { it.isNotBlank() },
                    showThumbnailPlaceholder = true,
                    onClick = { onPlay(item) }
                )
            }
        }
    }
}

@Composable
private fun LibraryFilterControls(
    filters: LibraryFilters,
    onFiltersChange: (LibraryFilters) -> Unit
) {
    val colors = plusColors()
    Column(Modifier.fillMaxWidth()) {
        SearchBarField(
            value = filters.query,
            onValueChange = { query -> onFiltersChange(filters.copy(query = query)) },
            placeholder = stringResource(Res.string.library_search_placeholder),
            modifier = Modifier
                .testTag(TEST_TAG_LIBRARY_SEARCH)
                .padding(horizontal = spaceNormal)
        )
        FilterChipRow(
            options = MediaFilter.entries.map { mediaFilterLabel(it) },
            selectedIndex = filters.filter.ordinal,
            onSelect = { index ->
                onFiltersChange(filters.copy(filter = MediaFilter.entries[index]))
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spaceNormal, vertical = spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spaceXSmall)) {
                SortOrder.entries.forEach { order ->
                    TextButton(onClick = { onFiltersChange(filters.copy(sort = order)) }) {
                        Text(
                            text = sortLabel(order),
                            color = if (filters.sort == order) colors.accent else colors.textSecondary
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spaceXSmall)) {
                ViewMode.entries.forEach { mode ->
                    TextButton(onClick = { onFiltersChange(filters.copy(view = mode)) }) {
                        Text(
                            text = if (mode == ViewMode.LIST) {
                                stringResource(Res.string.library_view_list)
                            } else {
                                stringResource(Res.string.library_view_grid)
                            },
                            color = if (filters.view == mode) colors.accent else colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun mediaFilterLabel(filter: MediaFilter): String = when (filter) {
    MediaFilter.ALL -> stringResource(Res.string.library_filter_all)
    MediaFilter.VIDEOS -> stringResource(Res.string.library_filter_videos)
    MediaFilter.AUDIO -> stringResource(Res.string.library_filter_audio)
}

@Composable
private fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.NAME -> stringResource(Res.string.library_sort_name)
    SortOrder.SIZE -> stringResource(Res.string.library_sort_size)
    SortOrder.NEWEST -> stringResource(Res.string.library_sort_date)
}

private const val GridColumns = 4

@Composable
private fun MediaGrid(items: List<LocalMediaFile>, onPlay: (LocalMediaFile) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.chunked(GridColumns).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spaceNormal, vertical = spaceXSmall),
                horizontalArrangement = Arrangement.spacedBy(spaceSmall)
            ) {
                row.forEach { item ->
                    MediaTile(
                        item = item,
                        onClick = { onPlay(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(GridColumns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MediaTile(
    item: LocalMediaFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = plusColors()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(spaceSmall)
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = if (item.missing) colors.textSecondary else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(spaceXSmall))
        Text(
            text = if (item.missing) {
                stringResource(Res.string.library_missing_file)
            } else {
                formatBytes(item.sizeBytes)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (item.missing) MaterialTheme.colorScheme.error else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FolderSection(
    folders: List<String>,
    onAddFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit
) {
    val colors = plusColors()
    var input by remember { mutableStateOf("") }

    SectionHeader(title = "Indexed folders")
    if (folders.isEmpty()) {
        Text(
            text = "No folders indexed yet.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = spaceNormal)
        )
    } else {
        folders.forEach { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spaceNormal, vertical = spaceNormal),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = folder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                ToolbarIconButton(
                    painter = painterResource(Res.drawable.ic_cloud_download),
                    contentDescription = "Remove folder",
                    onClick = { onRemoveFolder(folder) }
                )
            }
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
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            label = { Text("Folder path") },
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                onAddFolder(input)
                input = ""
            },
            enabled = input.isNotBlank()
        ) {
            Text("Add")
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun LibraryScreenPreview() {
    LibraryScreenContent(
        folders = listOf("/home/user/Videos", "/media/audio"),
        items = listOf(
            LocalMediaFile(
                id = "1",
                path = "/home/user/Videos/demo.mp4",
                name = "demo.mp4",
                extension = "mp4",
                mimeType = "video/mp4",
                url = "file:///home/user/Videos/demo.mp4",
                sizeBytes = 29_600_000
            ),
            LocalMediaFile(
                id = "2",
                path = "/media/audio/song.flac",
                name = "song.flac",
                extension = "flac",
                mimeType = "audio/flac",
                url = "file:///media/audio/song.flac",
                sizeBytes = 41_200_000,
                audioOnly = true
            )
        )
    )
}
