/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.newpipe.app.provider.ProviderPlaybackState
import net.newpipe.app.theme.spaceNormal
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.playback_error_title
import newpipe.shared.generated.resources.playback_preparing
import org.jetbrains.compose.resources.stringResource

/**
 * Inline status line for a user-triggered "play this provider item" request. Shows progress while an
 * item is being resolved and an honest error when it could not start; renders nothing for idle or
 * already-launched requests. Shared by the search, trending and channel screens.
 */
@Composable
fun PlaybackStatusMessage(state: ProviderPlaybackState, modifier: Modifier = Modifier) {
    when (state) {
        ProviderPlaybackState.Loading -> Text(
            text = stringResource(Res.string.playback_preparing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = spaceNormal)
        )

        is ProviderPlaybackState.Failure -> Text(
            text = "${stringResource(Res.string.playback_error_title)}: ${providerErrorMessage(state.reason)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(horizontal = spaceNormal)
        )

        ProviderPlaybackState.Idle, ProviderPlaybackState.Launched -> Unit
    }
}
