/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.PlusDialog
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.player.MediaItem
import net.newpipe.app.player.SavedPlaylist
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.theme.spaceXXSmall
import net.newpipe.app.viewmodel.playlists.PlaylistsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.playlists_delete
import newpipe.shared.generated.resources.playlists_delete_confirm_message
import newpipe.shared.generated.resources.playlists_delete_confirm_title
import newpipe.shared.generated.resources.playlists_empty_message
import newpipe.shared.generated.resources.playlists_empty_title
import newpipe.shared.generated.resources.playlists_play
import newpipe.shared.generated.resources.playlists_screen_title
import newpipe.shared.generated.resources.playlists_track_count
import newpipe.shared.generated.resources.playlists_tracks_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Lists user-saved playlists (F6). A playlist can be expanded to reveal its tracks, played (which
 * restores it as the queue and starts playback) or deleted behind a confirmation dialog.
 */
@Composable
fun PlaylistsScreen(
    navigator: Navigator = koinInject(),
    viewModel: PlaylistsViewModel = koinViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    PlaylistsScreenContent(
        playlists = playlists,
        onPlay = { saved ->
            viewModel.play(saved)
            navigator.navigateTo(Destination.Player)
        },
        onDelete = viewModel::delete,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun PlaylistsScreenContent(
    playlists: List<SavedPlaylist> = emptyList(),
    onPlay: (SavedPlaylist) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    var pendingDelete by remember { mutableStateOf<SavedPlaylist?>(null) }

    PlusScaffold(
        toolbar = {
            TopToolbar(
                title = stringResource(Res.string.playlists_screen_title),
                onBack = onNavigateUp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = spaceNormal)
        ) {
            if (playlists.isEmpty()) {
                EmptyState(
                    title = stringResource(Res.string.playlists_empty_title),
                    message = stringResource(Res.string.playlists_empty_message)
                )
            } else {
                playlists.forEach { saved ->
                    PlaylistEntry(
                        saved = saved,
                        onPlay = onPlay,
                        onRequestDelete = { pendingDelete = saved }
                    )
                }
            }
        }
    }

    pendingDelete?.let { saved ->
        PlusDialog(
            title = stringResource(Res.string.playlists_delete_confirm_title),
            text = stringResource(Res.string.playlists_delete_confirm_message),
            confirmLabel = stringResource(Res.string.playlists_delete),
            onConfirm = {
                onDelete(saved.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
            destructive = true
        )
    }
}

@Composable
private fun PlaylistEntry(
    saved: SavedPlaylist,
    onPlay: (SavedPlaylist) -> Unit,
    onRequestDelete: () -> Unit
) {
    val colors = plusColors()
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        PreferenceRow(
            title = saved.name,
            summary = stringResource(Res.string.playlists_track_count, saved.items.size),
            onClick = { expanded = !expanded }
        )
        if (expanded) {
            SectionHeader(title = stringResource(Res.string.playlists_tracks_label))
            saved.items.forEach { item ->
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        horizontal = spaceLarge,
                        vertical = spaceXXSmall
                    )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spaceLarge, vertical = spaceSmall),
                horizontalArrangement = Arrangement.spacedBy(spaceSmall)
            ) {
                TextButton(onClick = { onPlay(saved) }) {
                    Text(stringResource(Res.string.playlists_play))
                }
                TextButton(onClick = onRequestDelete) {
                    Text(stringResource(Res.string.playlists_delete))
                }
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlaylistsScreenPreview() {
    PlaylistsScreenContent(
        playlists = listOf(
            SavedPlaylist(
                id = "queue",
                name = "Queue",
                items = listOf(
                    MediaItem(id = "a", title = "First track", url = "file:///a"),
                    MediaItem(id = "b", title = "Second track", url = "file:///b")
                )
            )
        )
    )
}
