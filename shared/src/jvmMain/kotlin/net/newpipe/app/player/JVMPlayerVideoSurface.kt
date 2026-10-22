/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.newpipe.app.platform.JVMPlayerEngine

/**
 * Draws the libVLC video output inside the Compose player frame.
 *
 * The engine decodes into a callback video surface and exposes the latest frame as a
 * `BufferedImage`; this composable copies it into an [ImageBitmap] a few times per second's worth
 * of display frames. Rendering the picture as normal Compose content (instead of embedding an AWT
 * component) keeps the controls overlay clickable and always visible above the video.
 */
@Composable
actual fun PlayerVideoSurface(
    engine: PlayerEngine,
    modifier: Modifier,
    surfacePriority: Int
) {
    val jvmEngine = engine as? JVMPlayerEngine
        ?: return
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    DisposableEffect(jvmEngine) {
        jvmEngine.acquireFrameSink()
        onDispose {
            jvmEngine.releaseFrameSurface()
        }
    }
    LaunchedEffect(jvmEngine) {
        System.err.println("[NewPipe+] compose surface: polling started engine=" + jvmEngine.hashCode())
        var lastSequence = -1L
        var drawn = 0L
        while (isActive) {
            val sequence = jvmEngine.videoFrameSequence()
            if (sequence != lastSequence) {
                try {
                    jvmEngine.latestVideoFrame()?.let { image ->
                        frame = image.toComposeImageBitmap()
                        drawn++
                        if (drawn == 1L || drawn % 120L == 0L) {
                            System.err.println(
                                "[NewPipe+] compose surface: drawn=$drawn ${image.width}x${image.height}"
                            )
                        }
                    }
                } catch (t: Throwable) {
                    System.err.println("[NewPipe+] compose surface: conversion failed: ${t.message}")
                }
                lastSequence = sequence
            }
            delay(FRAME_POLL_MS)
        }
    }
    Box(modifier.background(Color.Black)) {
        frame?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/** Poll interval between frame copies; 16 ms keeps playback smooth without spinning the CPU. */
private const val FRAME_POLL_MS = 16L
