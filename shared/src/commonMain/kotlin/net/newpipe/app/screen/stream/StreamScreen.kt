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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.AnnotatedString
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
import net.newpipe.app.service.DownloadRepository
import net.newpipe.app.service.MediaDownloader
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PlaybackRequest
import net.newpipe.app.service.PopupPlayer
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
    val immersive by playerUiState.immersive.collectAsState()
    val audioOnly by playerUiState.audioOnly.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(serviceId, url) {
        playerUiState.setImmersive(false)
        viewModel.load(serviceId, url)
    }

    DisposableEffect(Unit) {
        onDispose {
            playerUiState.setImmersive(false)
            playerUiState.setFullscreen(false)
            PopupPlayer.close()
            // The playback keeps running: the shell shows it in the mini player while the user
            // browses other sections, so the video is never terminated by navigation.
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

    DisposableEffect(serviceId, url, relatedItems, navigator.backstack.size) {
        PlayerShortcutHooks.next = relatedItems
            .firstOrNull { it.type == ContentType.STREAM }
            ?.let { next ->
                { navigator.navigateTo(Destination.Stream(next.serviceId, next.url)) }
            }
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
                    onRetry = { viewModel.load(serviceId, url) },
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
                        StreamIntents.Action.DOWNLOAD -> startDownload(
                            details = streamDetails,
                            selectedVideo = selectedVideo,
                            downloader = downloader,
                            downloads = downloads,
                            scope = scope,
                            snackbarHostState = snackbarHostState
                        )
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
                        onNext = relatedItems
                            .firstOrNull { it.type == ContentType.STREAM }
                            ?.let { next ->
                                {
                                    navigator.navigateTo(
                                        Destination.Stream(next.serviceId, next.url)
                                    )
                                }
                            },
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
                        onNext = relatedItems
                            .firstOrNull { it.type == ContentType.STREAM }
                            ?.let { next ->
                                {
                                    navigator.navigateTo(
                                        Destination.Stream(next.serviceId, next.url)
                                    )
                                }
                            },
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
                        onDownload = {
                            startDownload(
                                details = streamDetails,
                                selectedVideo = selectedVideo,
                                downloader = downloader,
                                downloads = downloads,
                                scope = scope,
                                snackbarHostState = snackbarHostState
                            )
                        },
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
}
}

/**
 * Converts the loaded details into a library entry.
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

    controller.play(
        PlaybackRequest(
            title = details.title,
            videoUrl = selectedVideoUrl,
            audioUrl = playbackAudioUrl,
            thumbnailUrl = details.thumbnailUrl,
            startPositionMs = resumePositionMs,
            serviceId = details.serviceId,
            webUrl = details.url,
            qualityLabel = details.videoStreams
                .firstOrNull { it.url == selectedVideoUrl }?.quality
        )
    )
}

/**
 * Downloads the best muxed stream, or the selected stream with a separate audio download when
 * the service only offers video-only streams.
 */
private fun startDownload(
    details: StreamDetails,
    selectedVideo: MediaStream?,
    downloader: MediaDownloader,
    downloads: DownloadRepository,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val muxed = details.videoStreams
        .filterNot { it.isVideoOnly }
        .maxByOrNull { it.height ?: 0 }
    val target = muxed ?: selectedVideo

    if (target == null) {
        scope.launch {
            snackbarHostState.showSnackbar("No video stream is available to download")
        }
        return
    }

    val extension = if (target.formatId.contains("webm")) "webm" else "mp4"

    scope.launch {
        snackbarHostState.showSnackbar("Downloading ${target.quality}...")
    }

    scope.launch {
        try {
            val path = downloader.download(
                url = target.url,
                fileName = "${details.title}.$extension"
            )
            val audioPath = if (target.isVideoOnly) {
                details.audioStreams
                    .maxByOrNull { it.bitrate }
                    ?.let { audio ->
                        runCatching {
                            downloader.download(
                                url = audio.url,
                                fileName = "${details.title}.m4a"
                            )
                        }.getOrNull()
                    }
            } else {
                null
            }
            downloads.add(
                item = details.toContentItem(),
                filePath = path,
                quality = target.quality,
                formatId = target.formatId,
                audioFilePath = audioPath
            )
            snackbarHostState.showSnackbar("Saved to $path")
        } catch (exception: Exception) {
            snackbarHostState.showSnackbar("Download failed: ${exception.message}")
        }
    }
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
