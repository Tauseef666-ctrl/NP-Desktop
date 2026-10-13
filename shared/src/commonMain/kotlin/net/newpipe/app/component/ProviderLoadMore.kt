/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.component

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.provider_load_more
import org.jetbrains.compose.resources.stringResource

/**
 * The "load more" affordance shared by every paged provider listing (search, trending, channels,
 * playlists). It is disabled while a follow-up page is already in flight so a listing cannot be
 * re-queried twice. It only makes sense to render this when the listing actually has a next page.
 */
@Composable
fun ProviderLoadMore(
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onLoadMore,
        enabled = !loadingMore,
        modifier = modifier
    ) {
        Text(text = stringResource(Res.string.provider_load_more))
    }
}
