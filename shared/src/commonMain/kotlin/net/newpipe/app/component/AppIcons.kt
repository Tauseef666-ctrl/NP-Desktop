/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_pause
import newpipe.shared.generated.resources.ic_play_arrow
import newpipe.shared.generated.resources.ic_skip_next
import newpipe.shared.generated.resources.ic_skip_previous
import newpipe.shared.generated.resources.ic_stop
import newpipe.shared.generated.resources.ic_volume_off
import newpipe.shared.generated.resources.ic_volume_up

/**
 * Public painters for the player transport icons. The generated [Res] accessors are module-
 * [internal] and the compose-resources library is not on every consumer's classpath, so consumers
 * (e.g. the desktop mini-player window) go through these composables instead.
 */
object AppIcons {
    @Composable
    fun playPause(isPlaying: Boolean): Painter =
        painterResource(if (isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play_arrow)

    @Composable
    fun skipPrevious(): Painter = painterResource(Res.drawable.ic_skip_previous)

    @Composable
    fun skipNext(): Painter = painterResource(Res.drawable.ic_skip_next)

    @Composable
    fun stop(): Painter = painterResource(Res.drawable.ic_stop)

    @Composable
    fun volume(muted: Boolean): Painter =
        painterResource(if (muted) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up)
}
