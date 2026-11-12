/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.stream

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.newpipe.app.composable.ErrorPanel
import net.newpipe.app.composable.VideoPlayer
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.model.MediaStream
import net.newpipe.app.model.StreamDetails
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.platform.ShareHandler
import net.newpipe.app.screen.search.Message
import net.newpipe.app.service.ContentRepository
import net.newpipe.app.service.DownloadRepository
import net.newpipe.app.service.MediaDownloader
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PlaybackRequest
import net.newpipe.app.service.PopupPlayer
import net.newpipe.app.service.QueueRepository
import net.newpipe.app.service.WatchHistoryRepository
import net.newpipe.app.ui.PlayerShortcutHooks
import net.newpipe.app.ui.PlayerUiState
import net.newpipe.app.ui.Spacing
import net.newpipe.app.viewmodel.stream.StreamViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_arrow_back
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows the details of a single stream and plays it right inside the screen.
 *
 * @param serviceId the id of the service the stream belongs to
 * @param url the canonical url of the stream
 */
@Composable
fun StreamScreen(
    serviceId: Int,
    url: String,
    navigator: Navigator = koinInject(),
    viewModel: StreamViewModel = koinViewModel(),
    playbackController: PlaybackController = koinInject(),
    playerUiState: PlayerUiState = koinInject(),
    watchHistory: WatchHistoryRepository = koinInject(),
    downloads: DownloadRepository = koinInject(),
    downloader: MediaDownloader = koinInject(),
    queueRepository: QueueRepository = koinInject(),
    contentRepository: ContentRepository = koinInject(),
    shareHandler: ShareHandler = koinInject()
) {
    val details by viewModel.details.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val selectedAudio by viewModel.selectedAudio.collectAsState()
    val channel by viewModel.channel.collectAsState()
    val relatedItems by viewModel.relatedItems.collectAsState()
    val loadingMoreRelated by viewModel.loadingMoreRelated.collectAsState()
    val hasMoreRelated by viewModel.hasMoreRelated.collectAsState()
    val playbackState by playbackController.state.collectAsState()
    val queueItems by queueRepository.queue.collectAsState()
    val immersive by playerUiState.immersive.collectAsState()
    val audioOnly by playerUiState.audioOnly.collectAsState()

    // The next button skips to the head of the queue when the user has queued videos, and only
    // falls back to the first related stream when the queue is empty. A queue entry is removed
    // when it is played, exactly like the automatic advance at the end of a video does.
    val skipToNext: (() -> Unit)? = {
        val queued = queueRepository.pollNext()
        if (queued != null) {
            navigator.navigateTo(Destination.Stream(queued.serviceId, queued.url))
        } else {
            relatedItems
                .firstOrNull { it.type == ContentType.STREAM && it.url != url }
                ?.let { next ->
                    navigator.navigateTo(Destination.Stream(next.serviceId, next.url))
                }
        }
    }
    val hasNext = queueItems.any { it.type == ContentType.STREAM } ||
        relatedItems.any { it.type == ContentType.STREAM && it.url != url }

    // The auto-queue fallback in the queue repository takes its "next" video from the related
    // suggestions of the stream the user is watching.
    LaunchedEffect(relatedItems) {
        queueRepository.relatedSuggestions = relatedItems
    }

    // Prefetch the candidate "next" videos in the background: the queue head and the first
    // related suggestion. Their extraction lands in the repository cache, so skipping forward
    // or the automatic advance at the end of the video starts without an extraction wait.
    // Failures are silent; a prefetch must never disturb the user.
    LaunchedEffect(
        details?.url,
        queueItems.firstOrNull()?.url,
        relatedItems.firstOrNull()?.url
    ) {
        val candidates = listOfNotNull(
            queueItems.firstOrNull { it.type == ContentType.STREAM },
            relatedItems.firstOrNull { it.type == ContentType.STREAM && it.url != url }
        ).distinctBy { it.url }.take(2)
        for (candidate in candidates) {
            runCatching {
                contentRepository.streamDetails(candidate.serviceId, candidate.url)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var showDownloadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(serviceId, url) {
        playerUiState.setImmersive(false)
        viewModel.load(serviceId, url)
    }

    DisposableEffect(Unit) {
        onDispose {
            playerUiState.setImmersive(false)
            playerUiState.setFullscreen(false)
            // The playback and the popup player keep running: the shell shows the video in the
            // mini player (or keeps the popup window) while the user browses other sections, so
            // navigation never terminates either of them.
        }
    }

    // Persists the playback position periodically, so "continue where you left off" works even
    // when the app or the player is closed without an explicit stop.
    LaunchedEffect(playbackState.playing, url) {
        if (!playbackState.playing) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val now = playbackController.state.value
            if (now.positionMs > 0) {
                watchHistory.updateProgress(url, now.positionMs, now.durationMs)
            }
        }
    }

    DisposableEffect(serviceId, url, relatedItems, queueItems, navigator.backstack.size) {
        PlayerShortcutHooks.next = if (hasNext) skipToNext else null
        PlayerShortcutHooks.previous = if (navigator.backstack.size > 1) {
            ({ navigator.navigateUp() })
        } else {
            null
        }

        onDispose {
            PlayerShortcutHooks.next = null
            PlayerShortcutHooks.previous = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        val streamDetails = details
        when {
            error != null -> {
                val errorMessage = error!!
                val botWall = errorMessage.contains("Sign in to confirm", ignoreCase = true) ||
                    errorMessage.contains("limiting anonymous access", ignoreCase = true) ||
                    errorMessage.contains("limiting access", ignoreCase = true) ||
                    errorMessage.contains("refresh the session", ignoreCase = true)
                ErrorPanel(
                    title = if (botWall) {
                        "YouTube is restricting access"
                    } else {
                        "The stream could not be loaded"
                    },
                    detail = errorMessage,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onRetry = { viewModel.retry() },
                    onSignIn = if (botWall) {
                        { navigator.navigateTo(Destination.AccountSettings) }
                    } else {
                        null
                    },
                    onOpenInBrowser = { shareHandler.openUrlInBrowser(url) }
                )
            }

            streamDetails == null && loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            streamDetails == null -> Message(
                text = "The stream could not be loaded",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            else -> {
                // Clicking any content starts the playback right away: as soon as the details
                // resolve and the streams are chosen the video starts in the player UI.
                LaunchedEffect(streamDetails.url) {
                    val current = playbackController.state.value
                    if (current.webUrl != streamDetails.url || current.error != null) {
                        playStream(
                            details = streamDetails,
                            selectedVideoUrl = selectedVideo?.url,
                            selectedAudioUrl = selectedAudio?.url,
                            controller = playbackController,
                            watchHistory = watchHistory
                        )
                    }
                }

                val selectVideo: (MediaStream) -> Unit = { stream ->
                    viewModel.selectVideo(stream)
                    if (playbackState.title.isNotEmpty()) {
                        playStream(
                            details = streamDetails,
                            selectedVideoUrl = stream.url,
                            selectedAudioUrl = selectedAudio?.url,
                            controller = playbackController,
                            watchHistory = watchHistory,
                            forceRestart = true
                        )
                    }
                }
                val selectAudio: (MediaStream) -> Unit = { stream ->
                    viewModel.selectAudio(stream)
                    if (playbackState.title.isNotEmpty()) {
                        playStream(
                            details = streamDetails,
                            selectedVideoUrl = selectedVideo?.url,
                            selectedAudioUrl = stream.url,
                            controller = playbackController,
                            watchHistory = watchHistory,
                            forceRestart = true
                        )
                    }
                }
                val openPopup: () -> Unit = {
                    val popupCurrent = PopupPlayer.state.value
                    if (popupCurrent != null && popupCurrent.title == streamDetails.title) {
                        // The same video already plays in the popup; pressing the button again
                        // returns it to the main player: the window closes and the inline
                        // surface shows the running playback again.
                        PopupPlayer.close()
                    } else {
                        PopupPlayer.open(
                            PopupPlayer.State(
                                title = streamDetails.title,
                                thumbnailUrl = streamDetails.thumbnailUrl,
                                videoStreams = streamDetails.videoStreams,
                                selectedVideo = selectedVideo,
                                canPlay = selectedVideo != null,
                                onPlay = {
                                    playStream(
                                        details = streamDetails,
                                        selectedVideoUrl = selectedVideo?.url,
                                        selectedAudioUrl = selectedAudio?.url,
                                        controller = playbackController,
                                        watchHistory = watchHistory
                                    )
                                },
                               onSelectVideo = selectVideo
                            )
                        )
                    }
                }
                PlayerShortcutHooks.togglePopup = {
                    if (PopupPlayer.state.value != null) {
                        PopupPlayer.close()
                    } else {
                        openPopup()
                    }
                }
                DisposableEffect(Unit) {
                    onDispose { PlayerShortcutHooks.togglePopup = null }
                }

                // Applies the action requested from the right-click menu of a video card.
                LaunchedEffect(streamDetails, selectedVideo) {
                    val intent = StreamIntents.take(streamDetails.url)
                    when (intent) {
                        null -> Unit
                        StreamIntents.Action.DOWNLOAD -> showDownloadDialog = true
                        else -> {
                            playStream(
                                details = streamDetails,
                                selectedVideoUrl = selectedVideo?.url,
                                selectedAudioUrl = selectedAudio?.url,
                                controller = playbackController,
                                watchHistory = watchHistory
                            )
                            when (intent) {
                                StreamIntents.Action.POPUP -> openPopup()
                                StreamIntents.Action.BACKGROUND -> {
                                    if (!playerUiState.audioOnly.value) {
                                        playerUiState.toggleAudioOnly()
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }

                if (immersive && playbackState.title.isNotEmpty()) {
                    VideoPlayer(
                        controller = playbackController,
                        thumbnailUrl = streamDetails.thumbnailUrl,
                        canPlay = selectedVideo != null,
                        onPlay = {
                            playStream(
                                details = streamDetails,
                                selectedVideoUrl = selectedVideo?.url,
                                selectedAudioUrl = selectedAudio?.url,
                                controller = playbackController,
                                watchHistory = watchHistory
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                        videoStreams = streamDetails.videoStreams,
                        selectedVideo = selectedVideo,
                        onSelectVideo = selectVideo,
                        audioStreams = streamDetails.audioStreams,
                        selectedAudio = selectedAudio,
                        onSelectAudio = selectAudio,
                        onShare = {
                            clipboard.setText(AnnotatedString(streamDetails.url))
                            scope.launch {
                                snackbarHostState.showSnackbar("Link copied to the clipboard")
                            }
                        },
                        audioOnly = audioOnly,
                        immersive = true,
                        onToggleImmersive = {
                            // The player's fullscreen button toggles real video fullscreen;
                            // Main.kt syncs the immersive flag and the window placement.
                            playerUiState.toggleFullscreen()
                        },
                        onClose = playbackController::clear,
                        onNext = if (hasNext) skipToNext else null,
                        onPrevious = if (navigator.backstack.size > 1) {
                            ({ navigator.navigateUp() })
                        } else {
                            null
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        WatchPage(
                        details = streamDetails,
                        channel = channel,
                        controller = playbackController,
                        selectedVideo = selectedVideo,
                        selectedAudio = selectedAudio,
                        relatedItems = relatedItems,
                        loadingMoreRelated = loadingMoreRelated,
                        hasMoreRelated = hasMoreRelated,
                        onToggleImmersive = { playerUiState.toggleFullscreen() },
                        onClosePlayer = playbackController::clear,
                        onNext = if (hasNext) skipToNext else null,
                        onPrevious = if (navigator.backstack.size > 1) {
                            ({ navigator.navigateUp() })
                        } else {
                            null
                        },
                        onLoadMoreRelated = viewModel::loadMoreRelated,
                        onSelectVideo = selectVideo,
                        onSelectAudio = selectAudio,
                        onPlay = {
                            playStream(
                                details = streamDetails,
                                selectedVideoUrl = selectedVideo?.url,
                                selectedAudioUrl = selectedAudio?.url,
                                controller = playbackController,
                                watchHistory = watchHistory
                            )
                        },
                        onSeekTo = playbackController::seekTo,
                        onCopyLink = {
                            clipboard.setText(AnnotatedString(streamDetails.url))
                            scope.launch {
                                snackbarHostState.showSnackbar("Link copied to the clipboard")
                            }
                        },
                        onOpenInBrowser = {
                            shareHandler.openUrlInBrowser(streamDetails.url)
                        },
                        onOpenChannel = {
                            streamDetails.uploaderUrl?.let { channelUrl ->
                                navigator.navigateTo(
                                    Destination.Channel(streamDetails.serviceId, channelUrl)
                                )
                            }
                        },
                        onOpenItem = { item -> navigateToItem(item, navigator) },
                        audioOnly = audioOnly,
                        onToggleAudioOnly = playerUiState::toggleAudioOnly,
                        onOpenPopup = openPopup,
                        onDownload = { showDownloadDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )

                    FilledIconButton(
                        onClick = { navigator.navigateUp() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Spacing.sm)
                            .zIndex(2f)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                }
            }
        }
        }

        if (showDownloadDialog) {
            streamDetails?.let { openDetails ->
                DownloadDialog(
                    details = openDetails,
                    item = openDetails.toContentItem(),
                    downloader = downloader,
                    repository = downloads,
                    onResult = { result ->
                        result.fold(
                            onSuccess = { path ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("Downloaded to $path")
                                }
                            },
                            onFailure = { failure ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Download failed: ${failure.message}"
                                    )
                                }
                            }
                        )
                    },
                    onDismiss = { showDownloadDialog = false }
                )
            }
        }
    }
}

/**
 * Converts the loaded details into a library entry.
 */private fun StreamDetails.toContentItem() = ContentItem(
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
 * Starts the playback and records the stream in the watch history.
 *
 * The stream is recorded even when no video stream is available, so the feed reflects what the user
 * tried to watch, but playback only starts when there is something to play.
 */
private fun playStream(
    details: StreamDetails,
    selectedVideoUrl: String?,
    selectedAudioUrl: String?,
    controller: PlaybackController,
    watchHistory: WatchHistoryRepository,
    forceRestart: Boolean = false
) {
    // Opening the watch page from the mini player keeps the same video running: the media is
    // already loaded, so it is only made visible on the big surface again without restarting.
    // A quality or audio switch on the running video is a forced restart, which keeps the
    // current position so the user does not lose their place.
    val current = controller.state.value
    val sameMedia = current.webUrl == details.url && current.error == null
    if (!forceRestart && sameMedia) {
        return
    }

    val resumePositionMs = when {
        forceRestart && sameMedia -> current.positionMs
        details.liveNow -> 0L
        else -> watchHistory.resumePosition(details.url)
    }
    // A resume position at (or beyond) the end cannot be served by the media: it dead-loads
    // the SABR route, dead-loads the fallback's deferred seek and chains pointlessly. An
    // entry like that comes from a frameless session whose position ran away, or from a
    // video the user finished; such an open starts over instead.
    val durationMs = (details.durationSeconds ?: 0L) * 1000L
    val startPositionMs =
        if (durationMs > 0 && resumePositionMs >= durationMs - 10_000) 0L else resumePositionMs

    watchHistory.record(
        ContentItem(
            serviceId = details.serviceId,
            url = details.url,
            title = details.title,
            type = ContentType.STREAM,
            uploader = details.uploader,
            uploaderUrl = details.uploaderUrl,
            thumbnailUrl = details.thumbnailUrl,
            durationSeconds = details.durationSeconds,
            viewCount = details.viewCount,
            uploadDate = details.uploadDate,
            liveNow = details.liveNow,
            resumePositionMs = resumePositionMs.takeIf { it > 0 }
        )
    )

    if (selectedVideoUrl == null) return

    // A live HLS manifest carries its own audio track; attaching the separate audio stream
    // breaks the muxed playback.
    val playbackAudioUrl = if (details.liveNow) null else selectedAudioUrl

    // The player's emergency fallback: the richest muxed stream of the plain extraction, which
    // plays from its own direct url without the SABR relay. When the chosen route delivers no
    // media at all (YouTube walling every relay session, for example), the player switches to
    // it instead of leaving a dead screen.
    val fallbackStream = details.videoStreams
        .filter { !it.isVideoOnly && it.url != selectedVideoUrl }
        .maxByOrNull { it.height ?: 0 }

    controller.play(
        PlaybackRequest(
            title = details.title,
            videoUrl = selectedVideoUrl,
            audioUrl = playbackAudioUrl,
            thumbnailUrl = details.thumbnailUrl,
            startPositionMs = startPositionMs,
            serviceId = details.serviceId,
            webUrl = details.url,
            qualityLabel = details.videoStreams
                .firstOrNull { it.url == selectedVideoUrl }?.quality,
            live = details.liveNow,
            fallbackVideoUrl = fallbackStream?.url,
            fallbackQualityLabel = fallbackStream?.quality
        )
    )
}

/**
 * Navigates to the screen that matches the type of the item.
 */
private fun navigateToItem(item: ContentItem, navigator: Navigator) {
    when (item.type) {
        ContentType.STREAM ->
            navigator.navigateTo(Destination.Stream(item.serviceId, item.url))

        ContentType.CHANNEL ->
            navigator.navigateTo(Destination.Channel(item.serviceId, item.url))

        ContentType.PLAYLIST ->
            navigator.navigateTo(Destination.Playlist(item.serviceId, item.url))
    }
}
