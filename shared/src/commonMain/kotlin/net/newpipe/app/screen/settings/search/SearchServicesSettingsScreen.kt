/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.newpipe.app.component.PlusDialog
import net.newpipe.app.component.PlusSnackbarHost
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.ProviderOverview
import net.newpipe.app.viewmodel.settings.SearchServicesSettingsViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.content_settings_provider_available
import newpipe.shared.generated.resources.content_settings_provider_unavailable
import newpipe.shared.generated.resources.search_services_clear_history_title
import newpipe.shared.generated.resources.search_services_cleared
import newpipe.shared.generated.resources.search_services_history_count
import newpipe.shared.generated.resources.search_services_history_title
import newpipe.shared.generated.resources.search_services_providers_count
import newpipe.shared.generated.resources.search_services_providers_empty
import newpipe.shared.generated.resources.search_services_providers_title
import newpipe.shared.generated.resources.settings_category_search_services_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchServicesSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: SearchServicesSettingsViewModel = koinViewModel()
) {
    val searchHistoryCount by viewModel.searchHistoryCount.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val hasProviders by viewModel.hasProviders.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    SearchServicesSettingsScreenContent(
        searchHistoryCount = searchHistoryCount,
        providers = providers,
        hasProviders = hasProviders,
        onClearSearchHistory = viewModel::clearSearchHistory,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun SearchServicesSettingsScreenContent(
    searchHistoryCount: Int = 0,
    providers: List<ProviderOverview> = emptyList(),
    hasProviders: Boolean = false,
    onClearSearchHistory: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clearedMessage = stringResource(Res.string.search_services_cleared)
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_search_services_title),
                onNavigateUp = onNavigateUp
            )
        },
        snackbarHost = { PlusSnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            PreferenceCategoryTitle(stringResource(Res.string.search_services_history_title))
            PreferenceRow(
                title = stringResource(Res.string.search_services_clear_history_title),
                summary = stringResource(Res.string.search_services_history_count, searchHistoryCount),
                enabled = searchHistoryCount > 0,
                onClick = { showClearDialog = true }
            )

            PreferenceCategoryTitle(stringResource(Res.string.search_services_providers_title))
            if (hasProviders) {
                providers.forEach { provider ->
                    PreferenceRow(
                        title = provider.name,
                        summary = if (provider.available) {
                            stringResource(Res.string.content_settings_provider_available)
                        } else {
                            stringResource(Res.string.content_settings_provider_unavailable)
                        }
                    )
                }
                PreferenceRow(
                    title = stringResource(
                        Res.string.search_services_providers_count,
                        providers.size
                    )
                )
            } else {
                PreferenceRow(
                    title = stringResource(Res.string.search_services_providers_empty)
                )
            }
        }
    }

    if (showClearDialog) {
        PlusDialog(
            title = stringResource(Res.string.search_services_clear_history_title),
            text = stringResource(Res.string.search_services_history_count, searchHistoryCount),
            confirmLabel = stringResource(Res.string.search_services_clear_history_title),
            onConfirm = {
                onClearSearchHistory()
                showClearDialog = false
                scope.launch { snackbarHostState.showSnackbar(clearedMessage) }
            },
            onDismiss = { showClearDialog = false },
            destructive = true
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SearchServicesSettingsScreenPreview() {
    SearchServicesSettingsScreenContent(
        searchHistoryCount = 4,
        providers = listOf(
            ProviderOverview(id = "youtube", name = "YouTube", available = false),
            ProviderOverview(id = "peertube", name = "PeerTube", available = true)
        ),
        hasProviders = true
    )
}
