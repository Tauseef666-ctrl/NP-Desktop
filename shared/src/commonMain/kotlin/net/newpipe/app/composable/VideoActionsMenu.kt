/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import coil3.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.ShareHandler
import net.newpipe.app.screen.stream.StreamIntents
import net.newpipe.app.service.PlaylistRepository
import net.newpipe.app.ui.Spacing
import org.koin.compose.koinInject
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_cloud_download
import newpipe.shared.generated.resources.ic_headset
import newpipe.shared.generated.resources.ic_history
import newpipe.shared.generated.resources.ic_open_in_new
import newpipe.shared.generated.resources.ic_picture_in_picture
import newpipe.shared.generated.resources.ic_playlist_add
import newpipe.shared.generated.resources.ic_play_arrow
import newpipe.shared.generated.resources.ic_skip_next
import newpipe.shared.generated.resources.ic_subscriptions
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Wraps a content entry so that a right click (desktop) or a long press (touch) opens the
 * [VideoActionsMenu] for it.
 */
@Composable
fun VideoActionsMenuHost(
    item: ContentItem,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .secondaryClickBridge {
                showMenu = true
            }
            .videoContextMenu {
                showMenu = true
            }
    ) {
        content()
        if (showMenu) {
            VideoActionsMenu(item = item, onDismiss = { showMenu = false })
        }
    }
}

/**
 * The actions offered for a video, shown as a dialog when the user right-clicks
 * (or long presses) a video item in any list.
 *
 * `Play`, `Open popup`, `Background play` and `Download` request an intent that the watch page
 * carries out after it has loaded the stream details; `Save to playlist` toggles the membership
 * of the local playlists directly.
 *
 * @param item the video the menu belongs to
 * @param onDismiss invoked when the menu is closed
 */
@Composable
fun VideoActionsMenu(
    item: ContentItem,
    onDismiss: () -> Unit,
    playlistRepository: PlaylistRepository = koinInject(),
    queueRepository: net.newpipe.app.service.QueueRepository = koinInject(),
    navigator: Navigator = koinInject(),
    shareHandler: ShareHandler = koinInject()
) {
    val playlists by playlistRepository.playlists.collectAsState()
    var saveToPlaylist by rememberSaveable { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 430.dp),
            shape = MENU_SHAPE,
            color = menuGlass,
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.08f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (saveToPlaylist) {
                    SaveToPlaylistContent(
                        item = item,
                        playlists = playlists,
                        playlistRepository = playlistRepository
                    )
                } else if (item.type == ContentType.STREAM) {
                    // The big video preview, like the YouTube context menu for a thumbnail.
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color.Black)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(
                            listOf(
                                Triple(Res.drawable.ic_play_arrow, "Play", { StreamIntents.request(item.url, StreamIntents.Action.PLAY); navigator.navigateTo(Destination.Stream(item.serviceId, item.url)); onDismiss() }),
                                Triple(Res.drawable.ic_picture_in_picture, "Open popup", { StreamIntents.request(item.url, StreamIntents.Action.POPUP); navigator.navigateTo(Destination.Stream(item.serviceId, item.url)); onDismiss() }),
                                Triple(Res.drawable.ic_headset, "Background play", { StreamIntents.request(item.url, StreamIntents.Action.BACKGROUND); navigator.navigateTo(Destination.Stream(item.serviceId, item.url)); onDismiss() })
                            ),
                            listOf(
                                Triple(Res.drawable.ic_cloud_download, "Download", { StreamIntents.request(item.url, StreamIntents.Action.DOWNLOAD); navigator.navigateTo(Destination.Stream(item.serviceId, item.url)); onDismiss() }),
                                Triple(Res.drawable.ic_playlist_add, "Save to playlist", { saveToPlaylist = true }),
                                Triple(Res.drawable.ic_history, "Watch later", { val playlistId = playlistRepository.ensurePlaylist("Watch later"); playlistRepository.toggle(playlistId, item); onDismiss() })
                            ),
                            listOf(
                                Triple(Res.drawable.ic_skip_next, "Add to queue", { queueRepository.add(item); onDismiss() })
                            )
                            ).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                row.forEach { (icon, label, action) ->
                                    ActionTile(
                                        icon = icon,
                                        label = label,
                                        onClick = action,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    // Channels and playlists have no playback actions; they open in the app or
                    // on the web.
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        ActionTile(
                            icon = if (item.type == ContentType.CHANNEL) {
                                Res.drawable.ic_subscriptions
                            } else {
                                Res.drawable.ic_playlist_add
                            },
                            label = "Open",
                            onClick = {
                                val destination = if (item.type == ContentType.CHANNEL) {
                                    Destination.Channel(item.serviceId, item.url)
                                } else {
                                    Destination.Playlist(item.serviceId, item.url)
                                }
                                navigator.navigateTo(destination)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ActionTile(
                            icon = Res.drawable.ic_open_in_new,
                            label = "Open in browser",
                            onClick = {
                                shareHandler.openUrlInBrowser(item.url)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = onDismiss,
                        content = { Text("Close") }
                    )
                }
            }
        }
    }
}

/**
 * One tile of the action grid: icon stacked over the label, centred.
 */
@Composable
private fun ActionTile(
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private val MENU_SHAPE: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp)

/**
 * The frosted glass the menu is painted on, matching the mini player bar.
 */
private val menuGlass: Color
    @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)

/**
 * A single clickable action row of the menu.
 */
@Composable
private fun ActionRow(
    icon: org.jetbrains.compose.resources.DrawableResource,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The playlist membership editor for the video.
 */
@Composable
private fun SaveToPlaylistContent(
    item: ContentItem,
    playlists: List<net.newpipe.app.model.SavedPlaylist>,
    playlistRepository: PlaylistRepository
) {
    var newName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        playlists.forEach { playlist ->
            val contains = playlist.items.any { it.url == item.url }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = contains,
                    onCheckedChange = { playlistRepository.toggle(playlist.id, item) }
                )
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (playlists.isEmpty()) {
            Text(
                text = "No playlists yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(top = Spacing.sm)
        ) {
            TextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("New playlist") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.TextButton(
                onClick = {
                    playlistRepository.create(newName)
                    newName = ""
                },
                enabled = newName.isNotBlank(),
                content = { Text("Create") }
            )
        }
    }
}
