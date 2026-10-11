/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.viewmodel.settings.ContentSettingsViewModel
import net.newpipe.app.viewmodel.settings.ProviderView
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.content_settings_no_providers_message
import newpipe.shared.generated.resources.content_settings_no_providers_title
import newpipe.shared.generated.resources.content_settings_provider_available
import newpipe.shared.generated.resources.content_settings_provider_unavailable
import newpipe.shared.generated.resources.content_settings_providers_title
import newpipe.shared.generated.resources.content_settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ContentSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: ContentSettingsViewModel = koinViewModel()
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    ContentSettingsScreenContent(
        providers = providers,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun ContentSettingsScreenContent(
    providers: List<ProviderView> = emptyList(),
    onNavigateUp: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.content_settings_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            SectionHeader(title = stringResource(Res.string.content_settings_providers_title))
            if (providers.isEmpty()) {
                EmptyState(
                    title = stringResource(Res.string.content_settings_no_providers_title),
                    message = stringResource(Res.string.content_settings_no_providers_message)
                )
            } else {
                providers.forEach { provider ->
                    ProviderRow(provider = provider)
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: ProviderView) {
    ListItem(
        headlineContent = { Text(text = provider.name) },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spaceSmall)
            ) {
                Text(
                    text = if (provider.isAvailable) {
                        stringResource(Res.string.content_settings_provider_available)
                    } else {
                        stringResource(Res.string.content_settings_provider_unavailable)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun ContentSettingsScreenPreview() {
    ContentSettingsScreenContent(
        providers = listOf(
            ProviderView(
                id = "youtube",
                name = "YouTube",
                isAvailable = false,
                capabilities = ProviderCapabilities()
            )
        )
    )
}