/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.stream

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import net.newpipe.app.composable.ContentThumbnail
import net.newpipe.app.composable.VideoActionsMenuHost
import net.newpipe.app.composable.VideoPlayer
import net.newpipe.app.model.ChannelDetails
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.MediaStream
import net.newpipe.app.model.StreamDetails
import net.newpipe.app.model.SubscribedChannel
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PlaylistRepository
import net.newpipe.app.service.SubscriptionRepository
import net.newpipe.app.ui.Breakpoints
import net.newpipe.app.ui.Motion
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import net.newpipe.app.ui.trackTextFieldFocus
import net.newpipe.app.util.formatDuration
import net.newpipe.app.util.formatSubscriberCount
import net.newpipe.app.util.formatViewCount
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_close
import newpipe.shared.generated.resources.ic_file_download
import newpipe.shared.generated.resources.ic_headset
import newpipe.shared.generated.resources.ic_link
import newpipe.shared.generated.resources.ic_more_vert
import newpipe.shared.generated.resources.ic_picture_in_picture
import newpipe.shared.generated.resources.ic_open_in_new
import newpipe.shared.generated.resources.ic_playlist_add
import newpipe.shared.generated.resources.ic_subscriptions
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * The video watch page.
 *
 * On wide windows the player and the video information form the main column while the related
 * videos scroll independently in a narrower column on the right. On narrow windows everything is
 * stacked in a single scrolling column so the player keeps as much width as possible.
 *
 * Only controls backed by the extraction engine are shown: copying the link, opening the channel
 * and opening the stream in the browser. The extraction engine offers no comments, likes or
 * subscriptions for the desktop build, so those elements are deliberately absent.
 */
@Composable
internal fun WatchPage(
    details: StreamDetails,
    channel: ChannelDetails?,
    controller: PlaybackController,
    selectedVideo: MediaStream?,
    selectedAudio: MediaStream?,
    relatedItems: List<ContentItem>,
    loadingMoreRelated: Boolean,
    hasMoreRelated: Boolean,
    modifier: Modifier = Modifier,
    onToggleImmersive: () -> Unit,
    onClosePlayer: () -> Unit,
    onNext: (() -> Unit)?,
    onPrevious: (() -> Unit)?,
    onLoadMoreRelated: () -> Unit,
    onSelectVideo: (MediaStream) -> Unit,
    onSelectAudio: (MediaStream) -> Unit,
    onPlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onOpenChannel: () -> Unit,
    onOpenItem: (ContentItem) -> Unit,
    audioOnly: Boolean,
    onToggleAudioOnly: () -> Unit,
    onOpenPopup: () -> Unit,
    onDownload: () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= Breakpoints.compact
        val relatedWidth = (maxWidth * 0.3f).coerceIn(300.dp, 420.dp)

        if (twoColumns) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    PlayerFrame(
                        details = details,
                        controller = controller,
                        selectedVideo = selectedVideo,
                        selectedAudio = selectedAudio,
                        canPlay = selectedVideo != null,
                        audioOnly = audioOnly,
                        onPlay = onPlay,
                        onSelectVideo = onSelectVideo,
                        onSelectAudio = onSelectAudio,
                        onShare = onCopyLink,
                        onToggleImmersive = onToggleImmersive,
                        onClosePlayer = onClosePlayer,
                        onNext = onNext,
                        onPrevious = onPrevious
                    )

                    WatchInformation(
                        details = details,
                        channel = channel,
                        onSeekTo = onSeekTo,
                        onCopyLink = onCopyLink,
                        onOpenInBrowser = onOpenInBrowser,
                        onOpenChannel = onOpenChannel,
                        audioOnly = audioOnly,
                        onToggleAudioOnly = onToggleAudioOnly,
                        onOpenPopup = onOpenPopup,
                        onDownload = onDownload
                    )
                }

                RelatedPanel(
                    items = relatedItems,
                    loadingMore = loadingMoreRelated,
                    hasMore = hasMoreRelated,
                    onLoadMore = onLoadMoreRelated,
                    onOpenItem = onOpenItem,
                    modifier = Modifier
                        .width(relatedWidth)
                        .fillMaxHeight()
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.md)
            ) {
                item(key = "player") {
                    PlayerFrame(
                        details = details,
                        controller = controller,
                        selectedVideo = selectedVideo,
                        selectedAudio = selectedAudio,
                        canPlay = selectedVideo != null,
                        audioOnly = audioOnly,
                        onPlay = onPlay,
                        onSelectVideo = onSelectVideo,
                        onSelectAudio = onSelectAudio,
                        onShare = onCopyLink,
                        onToggleImmersive = onToggleImmersive,
                        onClosePlayer = onClosePlayer,
                        onNext = onNext,
                        onPrevious = onPrevious
                    )
                }

                item(key = "information") {
                    WatchInformation(
                        details = details,
                        channel = channel,
                        onSeekTo = onSeekTo,
                        onCopyLink = onCopyLink,
                        onOpenInBrowser = onOpenInBrowser,
                        onOpenChannel = onOpenChannel,
                        audioOnly = audioOnly,
                        onToggleAudioOnly = onToggleAudioOnly,
                        onOpenPopup = onOpenPopup,
                        onDownload = onDownload
                    )
                }

                item(key = "related-header") {
                    RelatedHeader(
                        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm)
                    )
                }

                items(relatedItems, key = { it.url }) { item ->
                    RecommendationItem(
                        item = item,
                        onClick = { onOpenItem(item) }
                    )
                }

                item(key = "related-loader") {
                    RelatedLoader(
                        loading = loadingMoreRelated,
                        hasMore = hasMoreRelated,
                        onLoadMore = onLoadMoreRelated
                    )
                }
            }
        }
    }
}

/**
 * The player with the aspect ratio of the source, the title and the metadata below it.
 */
@Composable
private fun PlayerFrame(
    details: StreamDetails,
    controller: PlaybackController,
    selectedVideo: MediaStream?,
    selectedAudio: MediaStream?,
    canPlay: Boolean,
    audioOnly: Boolean,
    onPlay: () -> Unit,
    onSelectVideo: (MediaStream) -> Unit,
    onSelectAudio: (MediaStream) -> Unit,
    onShare: () -> Unit,
    onToggleImmersive: () -> Unit,
    onClosePlayer: () -> Unit,
    onNext: (() -> Unit)?,
    onPrevious: (() -> Unit)?
) {
    VideoPlayer(
        controller = controller,
        thumbnailUrl = details.thumbnailUrl,
        canPlay = canPlay,
        onPlay = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        videoStreams = details.videoStreams,
        selectedVideo = selectedVideo,
        onSelectVideo = onSelectVideo,
        audioStreams = details.audioStreams,
        selectedAudio = selectedAudio,
        onSelectAudio = onSelectAudio,
        onShare = onShare,
        onToggleImmersive = onToggleImmersive,
        onClose = onClosePlayer,
        onNext = onNext,
        onPrevious = onPrevious,
        audioOnly = audioOnly
    )
}

/**
 * The title, the channel row, the supported actions, the description and the stream selectors.
 */
@Composable
private fun WatchInformation(
    details: StreamDetails,
    channel: ChannelDetails?,
    onSeekTo: (Long) -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onOpenChannel: () -> Unit,
    audioOnly: Boolean,
    onToggleAudioOnly: () -> Unit,
    onOpenPopup: () -> Unit,
    onDownload: () -> Unit
) {
    var playlistDialogVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg)
    ) {
        Text(
            text = details.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.size(Spacing.md))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 720.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelRow(
                        details = details,
                        channel = channel,
                        onOpenChannel = onOpenChannel,
                        modifier = Modifier.weight(1f)
                    )

                    SubscribeButton(details = details, channel = channel)
                }
            } else {
                Column {
                    ChannelRow(
                        details = details,
                        channel = channel,
                        onOpenChannel = onOpenChannel
                    )

                    SubscribeButton(
                        details = details,
                        channel = channel,
                        modifier = Modifier.padding(top = Spacing.md)
                    )
                }
            }
        }

        WatchActionBar(
            audioOnly = audioOnly,
            onSave = { playlistDialogVisible = true },
            onToggleAudioOnly = onToggleAudioOnly,
            onOpenPopup = onOpenPopup,
            onDownload = onDownload,
            onCopyLink = onCopyLink,
            onOpenInBrowser = onOpenInBrowser
        )

        if (playlistDialogVisible) {
            PlaylistDialog(
                details = details,
                onDismiss = { playlistDialogVisible = false }
            )
        }

        Spacer(modifier = Modifier.size(Spacing.lg))

        DescriptionPanel(
            details = details,
            onSeekTo = onSeekTo
        )


    }
}

/**
 * The avatar, the name and the subscriber count of the uploader.
 */
@Composable
private fun ChannelRow(
    details: StreamDetails,
    channel: ChannelDetails?,
    onOpenChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channelClick = if (details.uploaderUrl != null) {
        Modifier.clickable(onClick = onOpenChannel)
    } else {
        Modifier
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelAvatar(
            avatarUrl = channel?.avatarUrl,
            name = details.uploader,
            modifier = channelClick
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = Spacing.md)
                .then(channelClick)
        ) {
            Text(
                text = details.uploader,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subscribers = channel?.subscriberCount
            if (subscribers != null && subscribers > 0) {
                Text(
                    text = "${formatSubscriberCount(subscribers)} subscribers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    }
}

/**
 * The avatar of the uploader with a letter fallback while the image is missing.
 */
@Composable
private fun ChannelAvatar(
    avatarUrl: String?,
    name: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Subscribes to or unsubscribes from the channel of the current stream.
 *
 * The subscription is stored locally, because the extraction engine only reads from the service.
 */
@Composable
private fun SubscribeButton(
    details: StreamDetails,
    channel: ChannelDetails?,
    modifier: Modifier = Modifier
) {
    val subscriptionRepository: SubscriptionRepository = koinInject()
    val subscriptions by subscriptionRepository.subscriptions.collectAsState()
    val channelUrl = details.uploaderUrl
    val subscribed = channelUrl != null && subscriptions.any { it.url == channelUrl }

    Button(
        onClick = {
            if (channelUrl != null) {
                subscriptionRepository.toggle(
                    SubscribedChannel(
                        serviceId = details.serviceId,
                        url = channelUrl,
                        name = details.uploader,
                        avatarUrl = channel?.avatarUrl
                    )
                )
            }
        },
        enabled = channelUrl != null,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_subscriptions),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.size(Spacing.xs))
        Text(text = if (subscribed) "Subscribed" else "Subscribe")
    }
}

/**
 * The action bar below the channel: each action shows an icon with its label underneath, like the
 * action bar of the Android client.
 */
@Composable
private fun WatchActionBar(
    audioOnly: Boolean,
    onSave: () -> Unit,
    onToggleAudioOnly: () -> Unit,
    onOpenPopup: () -> Unit,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        WatchAction(
            icon = Res.drawable.ic_playlist_add,
            label = "Save",
            onClick = onSave
        )

        WatchAction(
            icon = Res.drawable.ic_headset,
            label = if (audioOnly) "Video mode" else "Audio only",
            onClick = onToggleAudioOnly
        )

        WatchAction(
            icon = Res.drawable.ic_picture_in_picture,
            label = "Popup",
            onClick = onOpenPopup
        )

        WatchAction(
            icon = Res.drawable.ic_file_download,
            label = "Download",
            onClick = onDownload
        )

        WatchAction(
            icon = Res.drawable.ic_link,
            label = "Copy link",
            onClick = onCopyLink
        )

        MoreAction(
            onOpenInBrowser = onOpenInBrowser
        )
    }
}

/**
 * The "more" action of the action bar, with the browser and channel shortcuts in a menu.
 */
@Composable
private fun MoreAction(
    onOpenInBrowser: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        WatchAction(
            icon = Res.drawable.ic_more_vert,
            label = "More",
            onClick = { menuExpanded = true }
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open in browser") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_open_in_new),
                        contentDescription = null
                    )
                },
                onClick = {
                    menuExpanded = false
                    onOpenInBrowser()
                }
            )
        }
    }
}

/**
 * A single action of the action bar: the icon is placed above the label.
 */
@Composable
private fun WatchAction(
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .width(96.dp)
                .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Lets the user create playlists and add or remove the current video.
 */
@Composable
private fun PlaylistDialog(
    details: StreamDetails,
    onDismiss: () -> Unit
) {
    val repository: PlaylistRepository = koinInject()
    val playlists by repository.playlists.collectAsState()
    val item = details.toContentItem()
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Save to playlist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New playlist") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .trackTextFieldFocus()
                    )

                    TextButton(
                        onClick = {
                            repository.create(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text("Create")
                    }
                }

                if (playlists.isEmpty()) {
                    Text(
                        text = "No playlists yet. Create one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            val contains = playlist.items.any { it.url == details.url }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { repository.toggle(playlist.id, item) }
                                    .padding(vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = contains,
                                    onCheckedChange = null
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.items.size} videos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(onClick = { repository.delete(playlist.id) }) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_close),
                                        contentDescription = "Delete playlist",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

/**
 * Converts the loaded details into an entry of a local playlist.
 */
private fun StreamDetails.toContentItem() = ContentItem(
    serviceId = serviceId,
    url = url,
    title = title,
    type = ContentType.STREAM,
    uploader = uploader,
    uploaderUrl = uploaderUrl,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    uploadDate = uploadDate,
    liveNow = liveNow
)

/**
 * The description panel: metadata, the description and an expand action.
 */
@Composable
private fun DescriptionPanel(
    details: StreamDetails,
    onSeekTo: (Long) -> Unit
) {
    val description = details.description.orEmpty()
    var expanded by rememberSaveable(details.url) { mutableStateOf(false) }
    val canExpand = description.length > 180 || description.contains('\n')
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(tween(Motion.medium))
                .padding(Spacing.lg)
        ) {
            Text(
                text = buildWatchMetadata(details),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (description.isBlank()) {
                Text(
                    text = "No description provided",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
                return@Column
            }

            Spacer(modifier = Modifier.size(Spacing.sm))

            SelectionContainer {
                Text(
                    text = annotateDescription(
                        description = description,
                        linkStyle = linkStyle,
                        onSeekTo = onSeekTo
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (canExpand) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(top = Spacing.xs)
                ) {
                    Text(text = if (expanded) "Show less" else "Show more")
                }
            }
        }
    }
}

/**
 * Marks urls and timestamps in the description as links, so they can be opened or used to seek.
 */
private fun annotateDescription(
    description: String,
    linkStyle: SpanStyle,
    onSeekTo: (Long) -> Unit
): AnnotatedString = buildAnnotatedString {
    append(description)

    val styles = TextLinkStyles(style = linkStyle)

    URL_PATTERN.findAll(description).forEach { match ->
        addLink(
            LinkAnnotation.Url(
                url = match.value,
                styles = styles
            ),
            start = match.range.first,
            end = match.range.last + 1
        )
    }

    TIMESTAMP_PATTERN.findAll(description).forEach { match ->
        val positionMs = parseTimestamp(match.value) ?: return@forEach
        addLink(
            LinkAnnotation.Clickable(
                tag = match.value,
                styles = styles
            ) {
                onSeekTo(positionMs)
            },
            start = match.range.first,
            end = match.range.last + 1
        )
    }
}

private val URL_PATTERN = Regex("""https?://[^\s]+""")

private val TIMESTAMP_PATTERN = Regex("""(?<![\d:])(?:\d{1,2}:)?\d{1,2}:\d{2}(?![\d:])""")

private fun parseTimestamp(text: String): Long? {
    val parts = text.split(':').mapNotNull { it.toLongOrNull() }
    if (parts.isEmpty()) return null

    var seconds = 0L
    parts.forEach { seconds = seconds * 60 + it }
    return seconds * 1000
}

/**
 * The heading of the related column.
 */
@Composable
private fun RelatedHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Related videos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "More from this service",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The related videos column. It scrolls independently from the video information on wide windows.
 */
@Composable
private fun RelatedPanel(
    items: List<ContentItem>,
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenItem: (ContentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Spacing.lg)
    ) {
        item(key = "related-header") {
            RelatedHeader(modifier = Modifier.padding(bottom = Spacing.sm))
        }

        items(items, key = { it.url }) { item ->
            VideoActionsMenuHost(item = item) {
                RecommendationItem(
                    item = item,
                    onClick = { onOpenItem(item) }
                )
            }
        }

        item(key = "related-loader") {
            RelatedLoader(
                loading = loadingMore,
                hasMore = hasMore,
                onLoadMore = onLoadMore
            )
        }
    }
}

/**
 * A horizontal related video entry with a hover state.
 */
@Composable
private fun RecommendationItem(
    item: ContentItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = if (hovered) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            Color.Transparent
        },
        animationSpec = tween(Motion.fast),
        label = "recommendation-background"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(background)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        ContentThumbnail(
            item = item,
            modifier = Modifier.width(152.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.md)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val metadata = buildRecommendationMetadata(item)
            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
        }
    }
}

/**
 * The trailing entry of the related list: triggers loading more entries when it becomes visible and
 * reports when the list has been exhausted.
 */
@Composable
private fun RelatedLoader(
    loading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )

            hasMore -> {
                LaunchedEffect(Unit) { onLoadMore() }
                Text(
                    text = "Loading more\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> Text(
                text = "No more suggestions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The views, the upload date, the duration and the live state of the stream.
 */
private fun buildWatchMetadata(details: StreamDetails): String {
    val parts = mutableListOf<String>()

    details.viewCount?.let { parts.add("${formatViewCount(it)} views") }
    details.uploadDate?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    details.durationSeconds?.let { parts.add(formatDuration(it)) }
    if (details.liveNow) parts.add("LIVE")

    return parts.joinToString(" \u2022 ").ifBlank { "No metadata available" }
}

/**
 * The uploader, the view count and the upload date of a related video.
 */
private fun buildRecommendationMetadata(item: ContentItem): String {
    val parts = mutableListOf<String>()

    item.uploader?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    item.viewCount?.let { parts.add("${formatViewCount(it)} views") }
    item.uploadDate?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

    return parts.joinToString(" \u2022 ")
}
