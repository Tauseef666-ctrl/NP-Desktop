/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.player

import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.ErrorState
import net.newpipe.app.component.FilterChipRow
import net.newpipe.app.component.PlayerFrame
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.component.StatusChip
import net.newpipe.app.component.StatusKind
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.PopOutCoordinator
import net.newpipe.app.player.PlaybackController
import net.newpipe.app.player.PlaybackState
import net.newpipe.app.player.PlaybackStatus
import net.newpipe.app.player.PlayerAction
import net.newpipe.app.player.PlayerEngine
import net.newpipe.app.player.PlayerQueueState
import net.newpipe.app.player.PlayerShortcuts
import net.newpipe.app.player.PlayerVideoSurface
import net.newpipe.app.player.Quality
import net.newpipe.app.player.RepeatMode
import net.newpipe.app.player.ShortcutResolver
import net.newpipe.app.player.VideoSettings
import net.newpipe.app.player.onPlayerKeyEvent
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.provider.StreamMetadata
import net.newpipe.app.provider.SubtitleTrack
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.util.formatCount
import net.newpipe.app.util.formatDuration
import net.newpipe.app.viewmodel.captions.CaptionsState
import net.newpipe.app.viewmodel.captions.CaptionsViewModel
import net.newpipe.app.viewmodel.player.PlayerViewModel
import net.newpipe.app.viewmodel.streaminfo.StreamMetadataState
import net.newpipe.app.viewmodel.streaminfo.StreamMetadataViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val SpeedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun PlayerScreen(
    navigator: Navigator = koinInject(),
    viewModel: PlayerViewModel = koinViewModel(),
    engine: PlayerEngine = koinInject(),
    popOut: PopOutCoordinator = koinInject(),
    controller: PlaybackController = koinInject(),
    streamMetadataViewModel: StreamMetadataViewModel = koinViewModel(),
    captionsViewModel: CaptionsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queue by viewModel.queueState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val savedPlaylists by controller.savedPlaylists.collectAsStateWithLifecycle()
    val metadataState by streamMetadataViewModel.state.collectAsStateWithLifecycle()
    val captionsState by captionsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(queue.current?.id) {
        streamMetadataViewModel.load(queue.current?.id)
    }
    LaunchedEffect(queue.current?.id) {
        captionsViewModel.load(queue.current?.id)
    }
    PlayerScreenContent(
        state = state,
        queue = queue,
        settings = settings,
        metadataState = metadataState,
        captionsState = captionsState,
        engine = engine,
        isEngineAvailable = viewModel.isEngineAvailable,
        popOut = popOut,
        onPlayPause = viewModel::togglePlayPause,
        onStop = viewModel::stop,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onSeek = viewModel::seekTo,
        onSpeed = viewModel::setSpeed,
        onVolume = viewModel::setVolume,
        onToggleMute = viewModel::toggleMute,
        onQuality = viewModel::setQuality,
        onToggleShuffle = viewModel::toggleShuffle,
        onRepeatMode = viewModel::setRepeatMode,
        onAutoplayNext = viewModel::setAutoplayNext,
        onSubtitles = viewModel::setSubtitles,
        onAudioTrack = viewModel::setAudioTrack,
        onSeekStepMillis = viewModel::seekStepMillis,
        onNavigateUp = { navigator.navigateUp() },
        queueCanRestore = savedPlaylists.isNotEmpty(),
        onQueuePlayNow = { index ->
            controller.queueState.value.items.getOrNull(index)?.let { controller.playItem(it) }
        },
        onQueueMoveUp = { index -> controller.moveItem(index, index - 1) },
        onQueueMoveDown = { index -> controller.moveItem(index, index + 1) },
        onQueueRemove = controller::removeItem,
        onQueueSavePlaylist = { controller.saveQueueAsPlaylist() },
        onQueueRestorePlaylist = {
            val saved = controller.savedPlaylists.value.lastOrNull()
            if (saved == null) false else { controller.restorePlaylist(saved); true }
        },
        onQueueSkipUnavailable = {
            val before = controller.queueState.value.items.size
            controller.skipUnavailableInQueue()
            controller.queueState.value.items.size < before
        }
    )
}

@Composable
fun PlayerScreenContent(
    state: PlaybackState = PlaybackState(),
    queue: PlayerQueueState = PlayerQueueState(),
    settings: VideoSettings = VideoSettings(),
    metadataState: StreamMetadataState = StreamMetadataState.Idle,
    captionsState: CaptionsState = CaptionsState.Idle,
    engine: PlayerEngine? = null,
    isEngineAvailable: Boolean = true,
    popOut: PopOutCoordinator? = null,
    shortcuts: ShortcutResolver = remember { ShortcutResolver() },
    onPlayPause: () -> Unit = {},
    onStop: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onSpeed: (Float) -> Unit = {},
    onVolume: (Float) -> Unit = {},
    onToggleMute: () -> Unit = {},
    onQuality: (Quality) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onRepeatMode: (RepeatMode) -> Unit = {},
    onAutoplayNext: (Boolean) -> Unit = {},
    onSubtitles: (Boolean) -> Unit = {},
    onAudioTrack: (String) -> Unit = {},
    onSeekStepMillis: (Boolean) -> Long = { PlayerShortcuts.seekStepMillis(it) },
    onNavigateUp: () -> Unit = {},
    queueCanRestore: Boolean = false,
    onQueuePlayNow: suspend (Int) -> Unit = {},
    onQueueMoveUp: (Int) -> Unit = {},
    onQueueMoveDown: (Int) -> Unit = {},
    onQueueRemove: (Int) -> Unit = {},
    onQueueSavePlaylist: suspend () -> Boolean = { false },
    onQueueRestorePlaylist: suspend () -> Boolean = { false },
    onQueueSkipUnavailable: suspend () -> Boolean = { false }
) {
    var settingsExpanded by remember { mutableStateOf(false) }
    PlusScaffold(
        toolbar = {
            val miniPlayerOpen = popOut?.isOpen?.collectAsStateWithLifecycle()?.value ?: false
            TopToolbar(
                title = queue.current?.title ?: "Player",
                subtitle = playbackStatusLabel(state.status),
                onBack = onNavigateUp,
                actions = {
                    if (popOut != null && popOut.supported) {
                        TextButton(onClick = { popOut.toggle() }) {
                            Text(if (miniPlayerOpen) "Close mini player" else "Mini player")
                        }
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .onPlayerKeyEvent { info ->
                    if (info.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        val action = shortcuts.actionFor(info.key, info.isCtrlPressed)
                        when (action) {
                            PlayerAction.PLAY_PAUSE -> onPlayPause()
                            PlayerAction.STOP -> onStop()
                            PlayerAction.NEXT -> onNext()
                            PlayerAction.PREVIOUS -> onPrevious()
                            PlayerAction.SEEK_FORWARD -> onSeek(
                                state.positionMs + onSeekStepMillis(info.isShiftPressed)
                            )
                            PlayerAction.SEEK_BACKWARD -> onSeek(
                                (state.positionMs - onSeekStepMillis(info.isShiftPressed))
                                    .coerceAtLeast(0L)
                            )
                            PlayerAction.VOLUME_UP -> onVolume((state.volume + 0.1f).coerceIn(0f, 1f))
                            PlayerAction.VOLUME_DOWN -> onVolume((state.volume - 0.1f).coerceIn(0f, 1f))
                            PlayerAction.MUTE -> onToggleMute()
                            null -> return@onPlayerKeyEvent false
                        }
                        true
                    }
                }
        ) {
            PlayerFrame(
                title = queue.current?.title,
                subtitle = "NewPipe+ playback engine",
                video = {
                    val activeEngine = engine
                    if (activeEngine != null && isEngineAvailable && state.mediaId != null) {
                        PlayerVideoSurface(activeEngine, Modifier.fillMaxSize())
                    } else {
                        PlayerSurface(state = state, isEngineAvailable = isEngineAvailable)
                    }
                },
                controls = {
                    TransportControls(
                        state = state,
                        enabled = isEngineAvailable,
                        onPlayPause = onPlayPause,
                        onStop = onStop,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onSeek = onSeek
                    )
                }
            )

            if (metadataState is StreamMetadataState.Success) {
                StreamInfoSection(metadataState.metadata)
            }

            if (captionsState is CaptionsState.Success) {
                CaptionsSection(captionsState.tracks)
            }

            if (!queue.isEmpty) {
                PlayerQuickSettings(
                    state = state,
                    repeatMode = queue.repeatMode,
                    settings = settings,
                    isEngineAvailable = isEngineAvailable,
                    expanded = settingsExpanded,
                    onToggleExpanded = { settingsExpanded = !settingsExpanded },
                    onRepeatMode = onRepeatMode,
                    onAutoplayNext = onAutoplayNext,
                    onSubtitles = onSubtitles,
                    onAudioTrack = onAudioTrack
                )
            }

            when {
                !isEngineAvailable -> ErrorState(
                    message = state.error?.message
                        ?: "Playback is not available on this platform.",
                    modifier = Modifier.padding(spaceLarge)
                )

                state.error != null -> ErrorState(
                    message = state.error!!.message,
                    modifier = Modifier.padding(spaceLarge)
                )

                queue.isEmpty -> EmptyState(
                    title = "Nothing loaded",
                    message = "Pick something to play and its controls, quality and queue appear here.",
                    modifier = Modifier.padding(spaceLarge)
                )

                else -> {
                    SectionHeader(title = "Playback")
                    PlaybackSettings(
                        state = state,
                        onSpeed = onSpeed,
                        onVolume = onVolume,
                        onToggleMute = onToggleMute,
                        onQuality = onQuality
                    )
                    PlayerQueuePanel(
                        queue = queue,
                        canRestore = queueCanRestore,
                        onPlayNow = onQueuePlayNow,
                        onMoveUp = onQueueMoveUp,
                        onMoveDown = onQueueMoveDown,
                        onRemove = onQueueRemove,
                        onSavePlaylist = onQueueSavePlaylist,
                        onRestorePlaylist = onQueueRestorePlaylist,
                        onSkipUnavailable = onQueueSkipUnavailable
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(spaceLarge),
                        horizontalArrangement = Arrangement.spacedBy(spaceSmall)
                    ) {
                        TextButton(onClick = onToggleShuffle) {
                            Text(if (queue.isShuffled) "Shuffle: on" else "Shuffle: off")
                        }
                    }
                }
            }
            Spacer(Modifier.height(spaceLarge))
        }
    }
}

@Composable
private fun StreamInfoSection(metadata: StreamMetadata, modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spaceLarge, vertical = spaceSmall)
            .then(modifier)
    ) {
        SectionHeader(title = "About")
        Text(
            text = metadata.title,
            style = MaterialTheme.typography.titleMedium
        )
        val uploaderParts = buildList {
            metadata.uploaderName?.let { add(if (metadata.uploaderVerified) "$it ✓" else it) }
            metadata.subscriberCount?.let { add("${formatCount(it)} subscribers") }
            metadata.uploadDate?.let { add(it) }
        }
        if (uploaderParts.isNotEmpty()) {
            Spacer(Modifier.height(spaceSmall))
            Text(
                text = uploaderParts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = plusColors().textSecondary
            )
        }
        val engagementParts = buildList {
            metadata.viewCount?.let { add("${formatCount(it)} views") }
            metadata.likeCount?.let { add("${formatCount(it)} likes") }
        }
        if (engagementParts.isNotEmpty()) {
            Spacer(Modifier.height(spaceSmall))
            Text(
                text = engagementParts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = plusColors().textSecondary
            )
        }
        metadata.category?.let { category ->
            Spacer(Modifier.height(spaceSmall))
            Text(
                text = category,
                style = MaterialTheme.typography.bodyMedium,
                color = plusColors().textSecondary
            )
        }
        if (metadata.tags.isNotEmpty()) {
            Spacer(Modifier.height(spaceSmall))
            Text(
                text = metadata.tags.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = plusColors().textSecondary
            )
        }
        metadata.description?.let { description ->
            Spacer(Modifier.height(spaceSmall))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = plusColors().textSecondary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CaptionsSection(tracks: List<SubtitleTrack>, modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spaceLarge, vertical = spaceSmall)
            .then(modifier)
    ) {
        SectionHeader(title = "Captions")
        if (tracks.isEmpty()) {
            Text(
                text = "No captions available for this stream.",
                style = MaterialTheme.typography.bodyMedium,
                color = plusColors().textSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spaceSmall)) {
                tracks.forEach { track ->
                    Text(
                        text = buildString {
                            append(track.displayName)
                            if (track.isAutoGenerated) append(" (auto-generated)")
                            append(" · ${track.languageTag}")
                            track.format?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = plusColors().textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerSurface(state: PlaybackState, isEngineAvailable: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(spaceLarge)
    ) {
        StatusChip(
            text = playbackStatusLabel(state.status).uppercase(),
            kind = statusKind(state.status)
        )
        Spacer(Modifier.height(spaceSmall))
        Text(
            text = when {
                !isEngineAvailable -> state.error?.message ?: "No playback engine"
                state.mediaId == null -> "No media loaded"
                state.status == PlaybackStatus.LOADING -> "Resolving stream…"
                else -> "Video surface"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB8B8C0),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TransportControls(
    state: PlaybackState,
    enabled: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val duration = state.durationMs.coerceAtLeast(0L)
    val canSeek = enabled && duration > 0L

    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableStateOf(0f) }
    val shownPosition = if (scrubbing) scrubPosition else state.positionMs.toFloat()
    val rangeEnd = if (duration > 0L) duration.toFloat() else 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDuration(state.positionMs / 1000L),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Slider(
                value = shownPosition.coerceIn(0f, rangeEnd),
                onValueChange = {
                    scrubbing = true
                    scrubPosition = it
                },
                onValueChangeFinished = {
                    if (canSeek) onSeek(scrubPosition.toLong())
                    scrubbing = false
                },
                valueRange = 0f..rangeEnd,
                enabled = canSeek,
                modifier = Modifier.weight(1f).padding(horizontal = spaceNormal)
            )
            Text(
                text = formatDuration(if (duration > 0L) duration / 1000L else 0L),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrevious, enabled = enabled) { Text("Prev") }
            Button(onClick = onPlayPause, enabled = enabled) {
                Text(if (state.isPlaying) "Pause" else "Play")
            }
            Spacer(Modifier.width(spaceSmall))
            TextButton(onClick = onStop, enabled = enabled) { Text("Stop") }
            TextButton(onClick = onNext, enabled = enabled) { Text("Next") }
        }
        if (scrubbing) {
            Text(
                text = formatDuration(scrubPosition.toLong() / 1000L),
                style = MaterialTheme.typography.labelSmall,
                color = plusColors().accent,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun PlaybackSettings(
    state: PlaybackState,
    onSpeed: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onQuality: (Quality) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spaceLarge)) {
        Text(
            text = "Speed",
            style = MaterialTheme.typography.labelMedium,
            color = plusColors().textSecondary
        )
        Spacer(Modifier.height(spaceSmall))
        FilterChipRow(
            options = SpeedOptions.map { "${formatSpeed(it)}×" },
            selectedIndex = SpeedOptions.indexOfFirst { it == state.playbackSpeed }.coerceAtLeast(0),
            onSelect = { onSpeed(SpeedOptions[it]) },
            contentPadding = PaddingValues(0.dp)
        )
        Spacer(Modifier.height(spaceNormal))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Volume",
                style = MaterialTheme.typography.labelMedium,
                color = plusColors().textSecondary
            )
            Slider(
                value = state.volume.coerceIn(0f, 1f),
                onValueChange = onVolume,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f).padding(horizontal = spaceNormal)
            )
            TextButton(onClick = onToggleMute) {
                Text(if (state.isMuted) "Unmute" else "Mute")
            }
        }
        Spacer(Modifier.height(spaceNormal))
        Text(
            text = "Quality",
            style = MaterialTheme.typography.labelMedium,
            color = plusColors().textSecondary
        )
        Spacer(Modifier.height(spaceSmall))
        val qualities = Quality.entries
        FilterChipRow(
            options = qualities.map { it.label },
            selectedIndex = qualities.indexOf(state.quality).coerceAtLeast(0),
            onSelect = { onQuality(qualities[it]) },
            contentPadding = PaddingValues(0.dp)
        )
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

private fun playbackStatusLabel(status: PlaybackStatus): String = when (status) {
    PlaybackStatus.IDLE -> "Idle"
    PlaybackStatus.LOADING -> "Loading"
    PlaybackStatus.READY -> "Ready"
    PlaybackStatus.PLAYING -> "Playing"
    PlaybackStatus.PAUSED -> "Paused"
    PlaybackStatus.ENDED -> "Ended"
    PlaybackStatus.ERROR -> "Error"
}

private fun statusKind(status: PlaybackStatus): StatusKind = when (status) {
    PlaybackStatus.ERROR -> StatusKind.DANGER
    PlaybackStatus.PLAYING -> StatusKind.LIVE
    PlaybackStatus.LOADING -> StatusKind.WARNING
    PlaybackStatus.PAUSED, PlaybackStatus.READY -> StatusKind.INFO
    PlaybackStatus.ENDED -> StatusKind.SUCCESS
    PlaybackStatus.IDLE -> StatusKind.INFO
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlayerScreenPreview() {
    PlayerScreenContent(
        state = PlaybackState(status = PlaybackStatus.READY, durationMs = 213_000L, positionMs = 42_000L),
        queue = PlayerQueueState(
            items = listOf(
                net.newpipe.app.player.MediaItem(id = "1", title = "A private, polished media experience", durationMs = 213_000L),
                net.newpipe.app.player.MediaItem(id = "2", title = "Next up in the queue", durationMs = 180_000L)
            ),
            currentIndex = 0
        )
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlayerScreenMetadataPreview() {
    PlayerScreenContent(
        state = PlaybackState(status = PlaybackStatus.READY, durationMs = 213_000L, positionMs = 42_000L),
        queue = PlayerQueueState(
            items = listOf(
                net.newpipe.app.player.MediaItem(id = "1", title = "A private, polished media experience", durationMs = 213_000L)
            ),
            currentIndex = 0
        ),
        metadataState = StreamMetadataState.Success(
            StreamMetadata(
                title = "A private, polished media experience",
                uploaderName = "NewPipe",
                uploaderVerified = true,
                subscriberCount = 1_200_000L,
                viewCount = 340_000L,
                likeCount = 12_000L,
                uploadDate = "2026-01-02",
                category = "Music",
                tags = listOf("demo", "newpipe"),
                description = "A desktop-first NewPipe+ experience with an honest, provider-agnostic metadata surface."
            )
        )
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun PlayerScreenCaptionsPreview() {
    PlayerScreenContent(
        state = PlaybackState(status = PlaybackStatus.READY, durationMs = 213_000L, positionMs = 42_000L),
        queue = PlayerQueueState(
            items = listOf(
                net.newpipe.app.player.MediaItem(id = "1", title = "A private, polished media experience", durationMs = 213_000L)
            ),
            currentIndex = 0
        ),
        captionsState = CaptionsState.Success(
            listOf(
                SubtitleTrack(id = "en|manual|vtt", languageTag = "en", displayName = "English", format = "vtt"),
                SubtitleTrack(id = "de|auto|ttml", languageTag = "de", displayName = "German", isAutoGenerated = true, format = "ttml")
            )
        )
    )
}
