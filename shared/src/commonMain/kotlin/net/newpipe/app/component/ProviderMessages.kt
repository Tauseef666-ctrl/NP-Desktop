/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.component

import androidx.compose.runtime.Composable
import net.newpipe.app.provider.ProviderFailureReason
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.provider_error_network
import newpipe.shared.generated.resources.provider_error_no_provider
import newpipe.shared.generated.resources.provider_error_parse
import newpipe.shared.generated.resources.provider_error_unknown
import newpipe.shared.generated.resources.provider_error_unsupported
import org.jetbrains.compose.resources.stringResource

/**
 * Maps a [ProviderFailureReason] to honest, localized text for the UI. Shared by the search,
 * trending and channel screens so every failure reads the same way.
 */
@Composable
fun providerErrorMessage(reason: ProviderFailureReason): String = when (reason) {
    ProviderFailureReason.NO_PROVIDER -> stringResource(Res.string.provider_error_no_provider)
    ProviderFailureReason.UNSUPPORTED -> stringResource(Res.string.provider_error_unsupported)
    ProviderFailureReason.NETWORK -> stringResource(Res.string.provider_error_network)
    ProviderFailureReason.PARSE -> stringResource(Res.string.provider_error_parse)
    ProviderFailureReason.UNKNOWN -> stringResource(Res.string.provider_error_unknown)
}
