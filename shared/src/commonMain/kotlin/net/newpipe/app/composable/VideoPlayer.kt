/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.newpipe.app.model.MediaStream
import net.newpipe.app.service.FrameCapture
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PopupPlayer
import net.newpipe.app.service.SleepTimer
import net.newpipe.app.ui.Motion
import net.newpipe.app.ui.PlayerUiState
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_bedtime
import newpipe.shared.generated.resources.ic_camera
import newpipe.shared.generated.resources.ic_headset
import newpipe.shared.generated.resources.ic_link
import newpipe.shared.generated.resources.ic_picture_in_picture
import newpipe.shared.generated.resources.ic_play_arrow
import newpipe.shared.generated.resources.ic_volume_off
import newpipe.shared.generated.resources.ic_volume_up
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * The video area of the player.
 *
 * The video surface fills the whole area and the transport controls are drawn over it. While the
 * playback is running the controls hide after a few seconds without interaction and appear again
 * as soon as the pointer moves over the video.
 *
 * The top right corner of the player holds the controls that belong to the video itself: the
 * playback speed, the video quality, the way the picture is fitted into the player and a button
 * that shares (copies) the link of the stream.
 *
 * @param controller the controller that renders the video and receives the commands
 * @param thumbnailUrl the image that is shown as long as nothing has been played
 * @param canPlay whether a playable stream is selected
 * @param onPlay invoked when the user starts the playback
 * @param videoStreams the video streams that can be selected in the player
 * @param selectedVideo the currently selected video stream
 * @param onSelectVideo invoked when the user picks another video stream
 * @param onShare invoked when the user shares the stream, or `null` to hide the button
 * @param immersive whether the player currently uses the whole window
 * @param onToggleImmersive invoked when the user switches the immersive mode
 * @param onClose invoked when the user closes the player
 * @param onNext invoked when the user skips to the next stream
 * @param onPrevious invoked when the user goes back to the previous stream
 * @param audioOnly whether the video output is hidden and only the audio is played
 * @param isPopupWindow whether this player is rendered inside the popup window; the main window then
 * hides its video output while the popup is open
 */
@Composable
fun VideoPlayer(
    controller: PlaybackController,
    thumbnailUrl: String?,
    canPlay: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    videoStreams: List<MediaStream> = emptyList(),
    selectedVideo: MediaStream? = null,
    onSelectVideo: ((MediaStream) -> Unit)? = null,
    audioStreams: List<MediaStream> = emptyList(),
    selectedAudio: MediaStream? = null,
    onSelectAudio: ((MediaStream) -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    immersive: Boolean = false,
    onToggleImmersive: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    audioOnly: Boolean = false,
    isPopupWindow: Boolean = false
) {
    val state = controller.state.collectAsState()
    val popupState by PopupPlayer.state.collectAsState()
    val displacedByPopup = !isPopupWindow && popupState != null
    // The player state ticks four times a second with the playback position; reading the
    // whole state here would recompose this entire subtree with every tick. The screen only
    // needs a few rarely-changing facts, so they are derived: the composition stays flat
    // while the position moves, and the seek bar keeps its own collection.
    val hasMedia by remember(state) { derivedStateOf { state.value.title.isNotEmpty() } }
    val playing by remember(state) { derivedStateOf { state.value.playing } }
    val loading by remember(state) { derivedStateOf { state.value.loading } }
    val popupTitle by remember(state) { derivedStateOf { state.value.title } }
    val audioOnlyMedia by remember(state) { derivedStateOf { state.value.audioOnlyMedia } }
    val nearEnd by remember(state) {
        derivedStateOf {
            val snapshot = state.value
            snapshot.durationMs > 0 &&
                snapshot.positionMs >= snapshot.durationMs - END_OF_VIDEO_GRACE_MS &&
                !snapshot.playing && !snapshot.loading && snapshot.error == null
        }
    }

    var controlsVisible by remember(hasMedia) { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var aspect by remember { mutableStateOf(PlayerAspect.Fit) }
    // Whether one of the option menus of the top bar (quality, audio, speed) is open. While a
    // menu is open the controls must not hide, because the menus would disappear with them.
    var menuOpen by remember { mutableStateOf(false) }
    // The last position the pointer had when it last really moved. The video surface under a
    // stationary cursor redraws with every frame, and the platform then replays Move events for
    // the same position; comparing positions makes those fake moves identifiable.
    var lastPointerPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    // The mouse-wheel volume indicator: every wheel action bumps the tick, which restarts the
    // visibility timer, so the pill stays up while the user keeps scrolling and fades shortly
    // after the last notch.
    var volumePillTick by remember { mutableIntStateOf(0) }
    var volumePillVolume by remember { mutableIntStateOf(0) }
    var volumePillVisible by remember { mutableStateOf(false) }

    LaunchedEffect(volumePillTick) {
        if (volumePillTick == 0) return@LaunchedEffect
        volumePillVisible = true
        delay(VOLUME_PILL_VISIBLE_MS)
        volumePillVisible = false
    }

    // The feedback pill for a frame capture: the capture itself runs off the composition
    // thread, because encoding a PNG picture takes a moment, and the pill reports the
    // outcome for a while after.
    var flashPillTick by remember { mutableIntStateOf(0) }
    var flashPillText by remember { mutableStateOf("") }
    var flashPillVisible by remember { mutableStateOf(false) }
    LaunchedEffect(flashPillTick) {
        if (flashPillTick == 0) return@LaunchedEffect
        flashPillVisible = true
        delay(FLASH_PILL_VISIBLE_MS)
        flashPillVisible = false
    }
    val captureScope = rememberCoroutineScope()
    val captureFrame: () -> Unit = {
        captureScope.launch {
            flashPillText = when (val result = controller.saveFrame()) {
                is FrameCapture.Saved -> "Frame saved to ${result.path}"
                FrameCapture.NoFrame -> "Nothing to capture yet"
                is FrameCapture.Failed -> "Could not save the frame: ${result.reason}"
            }
            flashPillTick++
        }
    }

    LaunchedEffect(controlsVisible, interactions, playing, hasMedia, menuOpen) {
        if (hasMedia && controlsVisible && playing && !menuOpen) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    // The fullscreen player hides the mouse pointer together with the automatically hidden
    // controls; the first real pointer movement brings both back.
    val playerUiState: PlayerUiState = koinInject()
    val showStats by playerUiState.showStats.collectAsState()
    val hideCursor = immersive && !isPopupWindow && !displacedByPopup &&
        hasMedia && playing && !controlsVisible
    LaunchedEffect(hideCursor) {
        playerUiState.setHideCursor(hideCursor)
    }
    DisposableEffect(Unit) {
        onDispose {
            // Never leave the pointer hidden when this player goes away.
            playerUiState.setHideCursor(false)
        }
    }

    Box(
        modifier = modifier.playerCursor(hideCursor).background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (hasMedia) {
            if (displacedByPopup) {
                PopupPlaceholder(
                    title = popupTitle,
                    onReturn = { PopupPlayer.close() }
                )
            } else {
                if (audioOnly || audioOnlyMedia) {
                    AudioOnlyPanel(
                        title = popupTitle,
                        heading = "Playing audio only"
                    )
                } else {
                    VideoSurface(
                        controller = controller,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = aspect.contentScale,
                        zoom = aspect.zoom
                    )
                }

            // Any real pointer movement over the video brings the controls back and restarts
            // the auto hide timer. The overlay is composed before the controls, so the controls
            // always receive their clicks first and this handler still sees everything that
            // happens over them. Enter and Move are handled the same way: the platform replays
            // Move events for a stationary cursor over a redrawing surface and synthesizes
            // Enter events whenever the hit test chain changes while the controls hide, so the
            // events only count when the pointer really arrived somewhere new. Otherwise the
            // controls would flicker forever over a parked, still mouse.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        lastPointerPosition =
                                            event.changes.firstOrNull()?.let { Pair(it.position.x, it.position.y) }
                                        controlsVisible = true
                                        interactions++
                                    }
                                    PointerEventType.Move,
                                    PointerEventType.Enter -> {
                                        val position = event.changes.firstOrNull()
                                            ?.let { Pair(it.position.x, it.position.y) }
                                        val last = lastPointerPosition
                                        val moved = position != null && (
                                            last == null ||
                                                (position.first - last.first).let { it * it } +
                                                (position.second - last.second).let { it * it } >
                                                POINTER_MOVE_THRESHOLD_PX * POINTER_MOVE_THRESHOLD_PX
                                            )
                                        if (moved) {
                                            lastPointerPosition = position
                                            controlsVisible = true
                                            interactions++
                                        }
                                    }
                                    PointerEventType.Scroll -> {
                                        // The wheel over the video means volume, not page
                                        // scroll: scrolling up makes it louder (and unmutes),
                                        // scrolling down quieter, and the event is consumed so
                                        // it does not also scroll the page under the player.
                                        // On the desktop a wheel notch up arrives as a
                                        // positive vertical delta, so the delta is added.
                                        val delta = event.changes.firstOrNull()
                                            ?.scrollDelta?.y ?: 0f
                                        if (delta != 0f) {
                                            val current = controller.state.value
                                            val target = (current.volume + delta * VOLUME_WHEEL_STEP)
                                                .toInt()
                                                .coerceIn(0, 100)
                                            if (target != current.volume) {
                                                controller.setVolume(target)
                                                if (target > current.volume && current.muted) {
                                                    controller.setMuted(false)
                                                }
                                            }
                                            volumePillVolume = target
                                            volumePillTick++
                                            event.changes.forEach { change -> change.consume() }
                                        }
                                    }
                                }
                            }
                        }
                    }
            )

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(Motion.fast)) +
                    slideInVertically(animationSpec = tween(Motion.medium)) { -it / 3 },
                exit = fadeOut(animationSpec = tween(Motion.medium)) +
                    slideOutVertically(animationSpec = tween(Motion.medium)) { -it / 3 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                PlayerTopBar(
                    controller = controller,
                    videoStreams = videoStreams,
                    selectedVideo = selectedVideo,
                    onSelectVideo = onSelectVideo,
                    audioStreams = audioStreams,
                    selectedAudio = selectedAudio,
                    onSelectAudio = onSelectAudio,
                    onShare = onShare,
                    aspect = aspect,
                    onCycleAspect = { aspect = aspect.next() },
                    onInteraction = {
                        controlsVisible = true
                        interactions++
                    },
                    onMenuOpenChange = { menuOpen = it },
                    onScreenshot = captureFrame
                )
            }

            // The stats-for-nerds overlay: a small panel in the top left corner with the facts
            // of the running playback. The panel has no pointer handling of its own, so clicks
            // pass through it to the video underneath.
            AnimatedVisibility(
                visible = showStats && hasMedia,
                enter = fadeIn(animationSpec = tween(Motion.medium)),
                exit = fadeOut(animationSpec = tween(Motion.medium)),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                StatsOverlay(controller)
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(Motion.fast)) +
                    slideInVertically(animationSpec = tween(Motion.medium)) { it / 3 },
                exit = fadeOut(animationSpec = tween(Motion.medium)) +
                    slideOutVertically(animationSpec = tween(Motion.medium)) { it / 3 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        // Presses inside the controls restart the idle timer directly, so a
                        // stationary click still keeps the controls shown. Pointer movement is
                        // not tracked here on purpose: the surface handler above already sees
                        // every move over the controls with the replayed-event filtering, so a
                        // duplicate tracker here would keep resetting the timer forever while
                        // the mouse rests motionless on a button.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press) {
                                    controlsVisible = true
                                    interactions++
                                }
                            }
                        }
                    }
                ) {
                    PlayerControls(
                        controller = controller,
                        immersive = immersive,
                        onToggleImmersive = onToggleImmersive,
                        onClose = onClose,
                        onNext = onNext,
                        onPrevious = onPrevious
                    )
                }
            }

            if (loading) {
                CircularProgressIndicator(color = Color.White)
            }

            // The video has run to its end: a centered replay button lets the user watch it
            // again without going through the play/pause toggle in the controls.
            if (nearEnd) {
                FilledIconButton(
                    onClick = {
                        controller.seekTo(0)
                        controller.resume()
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_play_arrow),
                        contentDescription = "Replay",
                        tint = PlayerForeground,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            // The mouse-wheel volume indicator: a small centered pill that shows the level
            // while the user scrolls the wheel over the video and fades after the last notch.
            AnimatedVisibility(
                visible = volumePillVisible,
                enter = fadeIn(animationSpec = tween(Motion.fast)),
                exit = fadeOut(animationSpec = tween(Motion.medium)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = Spacing.md,
                            vertical = Spacing.sm
                        )
                    ) {
                        Icon(
                            painter = painterResource(
                                if (volumePillVolume <= 0) {
                                    Res.drawable.ic_volume_off
                                } else {
                                    Res.drawable.ic_volume_up
                                }
                            ),
                            contentDescription = "Volume",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "$volumePillVolume%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = Spacing.xs)
                        )
                    }
                }
            }

            // The one-shot feedback pill, for example the outcome of a frame capture. It sits
            // a little above the center, so it does not collide with the volume pill.
            AnimatedVisibility(
                visible = flashPillVisible,
                enter = fadeIn(animationSpec = tween(Motion.fast)),
                exit = fadeOut(animationSpec = tween(Motion.medium)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 120.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = flashPillText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    )
                }
            }
            }
        } else {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = canPlay, onClick = onPlay),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FilledIconButton(
                    onClick = onPlay,
                    enabled = canPlay,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_play_arrow),
                        contentDescription = "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }

                if (!canPlay) {
                    Text(
                        text = "No video stream is available",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * The stand-in for the video surface while the video plays in the popup window.
 */
@Composable
private fun PopupPlaceholder(title: String, onReturn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_picture_in_picture),
            contentDescription = null,
            tint = PlayerForeground,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = "Playing in the popup player",
            color = PlayerForeground,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.sm)
        )
        Text(
            text = title,
            color = PlayerForeground.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs)
        )
        Surface(
            onClick = onReturn,
            shape = RoundedCornerShape(percent = 50),
            color = Color.White.copy(alpha = 0.16f),
            contentColor = PlayerForeground,
            modifier = Modifier.padding(top = Spacing.md)
        ) {
            Text(
                text = "Return to this player",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            )
        }
    }
}

/**
 * The stand-in for the video surface while the player runs in audio-only mode.
 */
@Composable
private fun AudioOnlyPanel(title: String, heading: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_headset),
            contentDescription = null,
            tint = PlayerForeground,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = heading,
            color = PlayerForeground,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.sm)
        )
        Text(
            text = title,
            color = PlayerForeground.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs)
        )
    }
}

/**
 * The player controls that are shown in the top right corner of the video.
 */
@Composable
private fun PlayerTopBar(
    controller: PlaybackController,
    videoStreams: List<MediaStream>,
    selectedVideo: MediaStream?,
    onSelectVideo: ((MediaStream) -> Unit)?,
    audioStreams: List<MediaStream>,
    selectedAudio: MediaStream?,
    onSelectAudio: ((MediaStream) -> Unit)?,
    onShare: (() -> Unit)?,
    aspect: PlayerAspect,
    onCycleAspect: () -> Unit,
    onInteraction: () -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
    onScreenshot: () -> Unit
) {
    val state = controller.state.collectAsState()
    // Only rare facts of the player state are shown here; deriving them keeps this bar out
    // of the four-times-a-second position recomposition.
    val playingLabel by remember(state) { derivedStateOf { state.value.qualityLabel } }
    val speed by remember(state) { derivedStateOf { state.value.speed } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                )
            )
            .pointerInput(Unit) {
                // Presses on the top bar restart the idle timer directly. Pointer movement is
                // handled by the surface handler of the video area, which filters the replayed
                // Move events the platform sends for a stationary cursor.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            onInteraction()
                        }
                    }
                }
            }
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onShare != null) {
            PlayerIconButton(
                icon = Res.drawable.ic_link,
                contentDescription = "Copy link",
                onClick = onShare
            )
        }

        if (videoStreams.isNotEmpty() && onSelectVideo != null) {
            QualityMenu(
                streams = videoStreams,
                selected = selectedVideo,
                playingLabel = playingLabel,
                isUnavailable = controller::isQualityTemporarilyUnavailable,
                onSelect = onSelectVideo,
                onOpenChange = onMenuOpenChange
            )
        }

        if (audioStreams.isNotEmpty() && onSelectAudio != null) {
            AudioMenu(
                streams = audioStreams,
                selected = selectedAudio,
                onSelect = onSelectAudio,
                onOpenChange = onMenuOpenChange
            )
        }

        SpeedMenu(
            speed = speed,
            onSpeedChange = controller::setSpeed,
            onOpenChange = onMenuOpenChange
        )

        PlayerChip(
            text = aspect.label,
            onClick = onCycleAspect
        )

        PlayerIconButton(
            icon = Res.drawable.ic_camera,
            contentDescription = "Save the current frame as a picture",
            onClick = onScreenshot
        )

        SleepTimerMenu(
            controller = controller,
            onOpenChange = onMenuOpenChange
        )
    }
}

/**
 * A small translucent button that is used for the player icons.
 */
@Composable
private fun PlayerIconButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = PlayerForeground
        )
    }
}

/**
 * A small translucent text button that is used for the player options.
 */
@Composable
private fun PlayerChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = PlayerForeground
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        )
    }
}

/**
 * The playback speed, which opens a slider above the player controls.
 */
@Composable
private fun SpeedMenu(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onOpenChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var sliderSpeed by remember { mutableFloatStateOf(speed) }

    LaunchedEffect(speed) { sliderSpeed = speed }
    LaunchedEffect(expanded) { onOpenChange(expanded) }

    Box {
        PlayerChip(
            text = formatSpeed(speed),
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Playback speed",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = formatSpeed(sliderSpeed),
                    style = MaterialTheme.typography.bodyMedium
                )

                Slider(
                    value = sliderSpeed.coerceIn(MIN_SPEED, MAX_SPEED),
                    onValueChange = { sliderSpeed = it },
                    onValueChangeFinished = { onSpeedChange(sliderSpeed) },
                    valueRange = MIN_SPEED..MAX_SPEED,
                    steps = SPEED_STEPS,
                    colors = SliderDefaults.colors()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { preset ->
                        TextButton(onClick = { onSpeedChange(preset) }) {
                            Text(formatSpeed(preset))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The sleep timer, which opens the list of the timer presets.
 *
 * While a timer runs, the button becomes a countdown chip; picking a preset replaces the
 * running timer and the chip switches to the new countdown.
 */
@Composable
private fun SleepTimerMenu(
    controller: PlaybackController,
    onOpenChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { onOpenChange(expanded) }

    val remainingSeconds by SleepTimer.remainingSeconds.collectAsState()

    Box {
        if (remainingSeconds != null) {
            PlayerChip(
                text = formatCountdown(remainingSeconds!!),
                onClick = { expanded = true }
            )
        } else {
            PlayerIconButton(
                icon = Res.drawable.ic_bedtime,
                contentDescription = "Sleep timer",
                onClick = { expanded = true }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Sleep timer",
                    style = MaterialTheme.typography.titleSmall
                )

                val remaining = remainingSeconds
                Text(
                    text = if (remaining != null) {
                        "Pauses the playback in ${formatCountdown(remaining)}"
                    } else {
                        "Pauses the playback after the chosen time"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SLEEP_TIMER_PRESETS.forEach { minutes ->
                        TextButton(onClick = {
                            SleepTimer.start(minutes, controller)
                            expanded = false
                        }) {
                            Text("$minutes")
                        }
                    }
                }

                if (remaining != null) {
                    TextButton(onClick = {
                        SleepTimer.cancel()
                        expanded = false
                    }) {
                        Text("Turn off")
                    }
                }
            }
        }
    }
}

/**
 * Formats a count of seconds as minutes and seconds, for example 5400 becomes `90:00`.
 */
private fun formatCountdown(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val padded = if (seconds < 10) "0$seconds" else "$seconds"
    return "$minutes:$padded"
}

/**
 * The stats-for-nerds overlay: a small translucent panel in the top left corner of the video
 * with the facts of the running playback. The panel collects its data on its own, so the rest
 * of the composition stays out of the four-times-a-second refresh.
 */
@Composable
private fun StatsOverlay(controller: PlaybackController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    val stats by controller.statistics.collectAsState()

    Surface(
        color = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(Radius.md),
        modifier = modifier
            .padding(Spacing.md)
            .widthIn(max = 380.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            StatsRow("Stream", state.title.take(48))
            state.qualityLabel?.let { StatsRow("Quality", it) }
            if (stats.videoWidth > 0) {
                StatsRow(
                    "Video",
                    "${stats.videoWidth}×${stats.videoHeight} · ${"%.2f".format(stats.frameRateFps)}fps"
                )
            }
            StatsRow(
                "Frames",
                "decoded ${stats.decodedVideoFrames} · shown ${stats.displayedFrames} · " +
                    "dropped ${stats.lostPictures}"
            )
            StatsRow(
                "Bitrate",
                "input ${"%.0f".format(stats.inputBitrateKbps)}kbps · " +
                    "demux ${"%.0f".format(stats.demuxBitrateKbps)}kbps"
            )
            StatsRow(
                "Relay",
                if (stats.relayDeliveredBytes > 0) {
                    formatBytes(stats.relayDeliveredBytes)
                } else {
                    "—"
                }
            )
            StatsRow("Player", "${formatSpeed(state.speed)} · volume ${state.volume}%")
        }
    }
}

/**
 * One labeled row of the stats overlay.
 */
@Composable
private fun StatsRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Formats a byte count for the stats overlay, for example 1_536_000 becomes `1.5 MB`.
 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

/**
 * The video quality, which opens the list of the extracted video streams.
 */
@Composable
private fun QualityMenu(
    streams: List<MediaStream>,
    selected: MediaStream?,
    playingLabel: String?,
    isUnavailable: (String) -> Boolean,
    onSelect: (MediaStream) -> Unit,
    onOpenChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) { onOpenChange(expanded) }

    Box {
        PlayerChip(
            // The button always names the quality that is actually playing: after an
            // automatic fallback to a lower muxed stream, the selection in the list stays
            // the picked quality while the player runs the fallback one.
            text = playingLabel ?: selected?.quality ?: "Quality",
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val distinct = streams.distinctBy { it.quality }
            if (distinct.size <= 1) {
                // Some videos (for example made-for-kids content served through restricted
                // clients) only carry a single muxed quality; say so instead of offering a
                // dropdown with one seemingly arbitrary entry.
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Only ${distinct.firstOrNull()?.quality ?: "one quality"} is" +
                                " available for this video",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        expanded = false
                        distinct.firstOrNull()?.let(onSelect)
                    }
                )
            }
            distinct.forEach { stream ->
                // A quality whose SABR route YouTube walled moments ago is marked instead of
                // letting the user pick it and bounce off it: the entry is disabled until a
                // fresh attempt is allowed again.
                // The highlight follows the quality that is actually playing: after an
                // automatic fallback the picked quality stays selected in the view model
                // while the player runs the fallback one.
                val playing = stream.quality == (playingLabel ?: selected?.quality)
                // A quality whose SABR route YouTube walled moments ago is marked instead of
                // letting the user pick it and bounce off it: the entry is disabled until a
                // fresh attempt is allowed again. The quality that is playing right now is
                // never marked: it demonstrably works.
                val unavailable = !playing && isUnavailable(stream.url)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (unavailable) "${stream.quality} (unavailable)" else stream.quality,
                            color = when {
                                playing -> MaterialTheme.colorScheme.primary
                                unavailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    enabled = !unavailable,
                    onClick = {
                        expanded = false
                        onSelect(stream)
                    }
                )
            }
        }
    }
}

/**
 * The audio track, which opens the list of the extracted audio streams.
 */
@Composable
private fun AudioMenu(
    streams: List<MediaStream>,
    selected: MediaStream?,
    onSelect: (MediaStream) -> Unit,
    onOpenChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) { onOpenChange(expanded) }

    Box {
        PlayerChip(
            text = selected?.audioTrackName ?: selected?.quality ?: "Audio",
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val distinct = streams.distinctBy { stream ->
                stream.audioTrackName ?: stream.quality
            }
            distinct.forEach { stream ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stream.audioTrackName ?: stream.quality,
                            color = if (stream == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(stream)
                    }
                )
            }
        }
    }
}

/**
 * How the video picture is fitted into the player area.
 */
private enum class PlayerAspect(
    val label: String,
    val contentScale: ContentScale,
    val zoom: Float
) {
    Fit("Fit", ContentScale.Fit, 1f),
    Fill("Fill", ContentScale.Crop, 1f),
    Zoom("Zoom", ContentScale.Crop, 1.25f);

    fun next(): PlayerAspect = entries[(ordinal + 1) % entries.size]
}

private fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100f).toInt() / 100f
    return if (rounded % 1f == 0f) "${rounded.toInt()}x" else "${rounded}x"
}

private const val MIN_SPEED = 0.25f
private const val MAX_SPEED = 3.0f
private const val SPEED_INTERVALS = 11
private const val SPEED_STEPS = SPEED_INTERVALS - 1

/** The sleep timer presets, in minutes. */
private val SLEEP_TIMER_PRESETS = listOf(15, 30, 45, 60, 90)

private val PlayerForeground = Color(0xFFF5F5F5)

/**
 * How long the controls (and, in fullscreen, the mouse pointer) stay visible without
 * interaction while the playback is running.
 */
private const val CONTROLS_HIDE_DELAY_MS = 3_000L

/**
 * The position counts as "the video has ended" when it is this close to the duration; the
 * replay button appears in that window while the playback is stopped.
 */
private const val END_OF_VIDEO_GRACE_MS = 1_000

/**
 * The pointer distance in pixels that counts as a real movement; hover events replayed for a
 * stationary cursor over a changing surface stay below this.
 */
private const val POINTER_MOVE_THRESHOLD_PX = 6f

/** How many volume points one notch of the mouse wheel over the video changes. */
private const val VOLUME_WHEEL_STEP = 5f

/** How long the mouse-wheel volume pill stays visible after the last notch. */
private const val VOLUME_PILL_VISIBLE_MS = 1_200L

/** How long the one-shot feedback pill stays visible. */
private const val FLASH_PILL_VISIBLE_MS = 2_400L
