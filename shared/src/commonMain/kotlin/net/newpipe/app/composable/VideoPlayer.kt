/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import net.newpipe.app.model.MediaStream
import net.newpipe.app.service.PlaybackController
import net.newpipe.app.service.PopupPlayer
import net.newpipe.app.ui.Motion
import net.newpipe.app.ui.Spacing
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_headset
import newpipe.shared.generated.resources.ic_link
import newpipe.shared.generated.resources.ic_picture_in_picture
import newpipe.shared.generated.resources.ic_play_arrow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

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
    val state by controller.state.collectAsState()
    val popupState by PopupPlayer.state.collectAsState()
    val displacedByPopup = !isPopupWindow && popupState != null
    val hasMedia = state.title.isNotEmpty()

    var controlsVisible by remember(hasMedia) { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var aspect by remember { mutableStateOf(PlayerAspect.Fit) }

    LaunchedEffect(controlsVisible, interactions, state.playing, hasMedia) {
        if (hasMedia && controlsVisible && state.playing) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (hasMedia) {
            if (displacedByPopup) {
                PopupPlaceholder(
                    title = state.title,
                    onReturn = { PopupPlayer.close() }
                )
            } else {
                if (audioOnly) {
                    AudioOnlyPanel(
                        title = state.title,
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

            // Any pointer activity over the video brings the controls back and restarts the
            // auto hide timer. The overlay is composed before the controls, so the controls
            // always receive their clicks first.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Move,
                                    PointerEventType.Press,
                                    PointerEventType.Enter -> {
                                        controlsVisible = true
                                        interactions++
                                    }
                                }
                            }
                        }
                    }
            )

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(Motion.fast)),
                exit = fadeOut(animationSpec = tween(Motion.medium)),
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
                    }
                )
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(Motion.fast)),
                exit = fadeOut(animationSpec = tween(Motion.medium)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Move ||
                                    event.type == PointerEventType.Press
                                ) {
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

            if (state.loading) {
                CircularProgressIndicator(color = Color.White)
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
    onInteraction: () -> Unit
) {
    val state by controller.state.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                )
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Move ||
                            event.type == PointerEventType.Press
                        ) {
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
                onSelect = onSelectVideo
            )
        }

        if (audioStreams.isNotEmpty() && onSelectAudio != null) {
            AudioMenu(
                streams = audioStreams,
                selected = selectedAudio,
                onSelect = onSelectAudio
            )
        }

        SpeedMenu(
            speed = state.speed,
            onSpeedChange = controller::setSpeed
        )

        PlayerChip(
            text = aspect.label,
            onClick = onCycleAspect
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
    onSpeedChange: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var sliderSpeed by remember { mutableFloatStateOf(speed) }

    LaunchedEffect(speed) { sliderSpeed = speed }

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
 * The video quality, which opens the list of the extracted video streams.
 */
@Composable
private fun QualityMenu(
    streams: List<MediaStream>,
    selected: MediaStream?,
    onSelect: (MediaStream) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        PlayerChip(
            text = selected?.quality ?: "Quality",
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val distinct = streams.distinctBy { it.quality }
            distinct.forEach { stream ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stream.quality,
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
 * The audio track, which opens the list of the extracted audio streams.
 */
@Composable
private fun AudioMenu(
    streams: List<MediaStream>,
    selected: MediaStream?,
    onSelect: (MediaStream) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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

private val PlayerForeground = Color(0xFFF5F5F5)

/**
 * How long the controls stay visible without interaction while the playback is running.
 */
private const val CONTROLS_HIDE_DELAY_MS = 3_500L
