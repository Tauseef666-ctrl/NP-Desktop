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
import kotlinx.coroutines.launch
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
        playbackController.clear()
        viewModel.load(serviceId, url)
    }

    DisposableEffect(Unit) {
        onDispose {
            playerUiState.setImmersive(false)
            PopupPlayer.close()
            playbackController.clear()
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
            error != null -> Message(
                text = error!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

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
                val selectVideo: (MediaStream) -> Unit = { stream ->
                    viewModel.selectVideo(stream)
                    if (playbackState.title.isNotEmpty()) {
                        playStream(
                            details = streamDetails,
                            selectedVideoUrl = stream.url,
                            selectedAudioUrl = selectedAudio?.url,
                            controller = playbackController,
                            watchHistory = watchHistory
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
                            watchHistory = watchHistory
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
                        onToggleImmersive = playerUiState::toggleImmersive,
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
                        onToggleImmersive = playerUiState::toggleImmersive,
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
                            val muxed = streamDetails.videoStreams
                                .filterNot { it.isVideoOnly }
                                .maxByOrNull { it.height ?: 0 }
                            val target = muxed ?: selectedVideo

                            if (target == null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("No video stream is available to download")
                                }
                            } else {
                                val extension = if (target.formatId.contains("webm")) {
                                    "webm"
                                } else {
                                    "mp4"
                                }

                                scope.launch {
                                    snackbarHostState.showSnackbar("Downloading ${target.quality}...")
                                }

                                scope.launch {
                                    try {
                                        val path = downloader.download(
                                            url = target.url,
                                            fileName = "${streamDetails.title}.$extension"
                                        )
                                        val audioPath = if (target.isVideoOnly) {
                                            streamDetails.audioStreams
                                                .maxByOrNull { it.bitrate }
                                                ?.let { audio ->
                                                    runCatching {
                                                        downloader.download(
                                                            url = audio.url,
                                                            fileName = "${streamDetails.title}.m4a"
                                                        )
                                                    }.getOrNull()
                                                }
                                        } else {
                                            null
                                        }
                                        downloads.add(
                                            item = streamDetails.toContentItem(),
                                            filePath = path,
                                            quality = target.quality,
                                            formatId = target.formatId,
                                            audioFilePath = audioPath
                                        )
                                        snackbarHostState.showSnackbar("Saved to $path")
                                    } catch (exception: Exception) {
                                        snackbarHostState.showSnackbar(
                                            "Download failed: ${exception.message}"
                                        )
                                    }
                                }
                            }
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
    watchHistory: WatchHistoryRepository
) {
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
            liveNow = details.liveNow
        )
    )

    if (selectedVideoUrl == null) return

    controller.play(
        PlaybackRequest(
            title = details.title,
            videoUrl = selectedVideoUrl,
            audioUrl = selectedAudioUrl,
            thumbnailUrl = details.thumbnailUrl
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
