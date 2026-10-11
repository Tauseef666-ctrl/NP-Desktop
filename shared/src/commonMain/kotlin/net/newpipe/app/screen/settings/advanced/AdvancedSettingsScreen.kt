/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings.advanced

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Severity
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.viewmodel.settings.AdvancedSettingsViewModel
import net.newpipe.app.viewmodel.settings.BuildInfoView
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.advanced_settings_build_info_title
import newpipe.shared.generated.resources.advanced_settings_log_level_debug
import newpipe.shared.generated.resources.advanced_settings_log_level_error
import newpipe.shared.generated.resources.advanced_settings_log_level_info
import newpipe.shared.generated.resources.advanced_settings_log_level_title
import newpipe.shared.generated.resources.advanced_settings_log_level_verbose
import newpipe.shared.generated.resources.advanced_settings_log_level_warning
import newpipe.shared.generated.resources.advanced_settings_variant_debug
import newpipe.shared.generated.resources.advanced_settings_variant_development
import newpipe.shared.generated.resources.advanced_settings_variant_release
import newpipe.shared.generated.resources.advanced_settings_variant_title
import newpipe.shared.generated.resources.settings_category_advanced_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AdvancedSettingsScreen(
    navigator: Navigator = koinInject(),
    viewModel: AdvancedSettingsViewModel = koinViewModel()
) {
    val logLevel by viewModel.logLevel.collectAsStateWithLifecycle()

    AdvancedSettingsScreenContent(
        buildInfo = viewModel.buildInfoView(),
        logLevel = logLevel,
        onLogLevelSelected = viewModel::setLogLevel,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun AdvancedSettingsScreenContent(
    buildInfo: BuildInfoView = BuildInfoView(
        isDebug = false,
        isRelease = false,
        variantLabel = AdvancedSettingsViewModel.VARIANT_DEVELOPMENT
    ),
    logLevel: String = AdvancedSettingsViewModel.DEFAULT_SEVERITY.name,
    onLogLevelSelected: (String) -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val selectedSeverity = AdvancedSettingsViewModel.pureParseSeverity(logLevel)
    val entries = AdvancedSettingsViewModel.SUPPORTED_SEVERITIES.map { severity ->
        ListPreferenceEntry(
            value = severity.name,
            title = stringResource(severityLabel(severity))
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_advanced_title),
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
            PreferenceCategoryTitle(stringResource(Res.string.advanced_settings_build_info_title))
            PreferenceRow(
                title = stringResource(Res.string.advanced_settings_variant_title),
                summary = stringResource(variantLabel(buildInfo.variantLabel))
            )
            PreferenceCategoryTitle(stringResource(Res.string.advanced_settings_log_level_title))
            ListPreference(
                title = stringResource(Res.string.advanced_settings_log_level_title),
                entries = entries,
                selectedValue = selectedSeverity.name,
                onValueSelected = onLogLevelSelected,
                summary = stringResource(severityLabel(selectedSeverity))
            )
        }
    }
}

private fun severityLabel(severity: Severity): StringResource = when (severity) {
    Severity.Verbose -> Res.string.advanced_settings_log_level_verbose
    Severity.Debug -> Res.string.advanced_settings_log_level_debug
    Severity.Info -> Res.string.advanced_settings_log_level_info
    Severity.Warn -> Res.string.advanced_settings_log_level_warning
    Severity.Error -> Res.string.advanced_settings_log_level_error
    Severity.Assert -> Res.string.advanced_settings_log_level_error
}

private fun variantLabel(variantLabel: String): StringResource = when (variantLabel) {
    AdvancedSettingsViewModel.VARIANT_DEBUG -> Res.string.advanced_settings_variant_debug
    AdvancedSettingsViewModel.VARIANT_RELEASE -> Res.string.advanced_settings_variant_release
    else -> Res.string.advanced_settings_variant_development
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun AdvancedSettingsScreenPreview() {
    AdvancedSettingsScreenContent(
        buildInfo = BuildInfoView(
            isDebug = false,
            isRelease = false,
            variantLabel = AdvancedSettingsViewModel.VARIANT_DEVELOPMENT
        ),
        logLevel = AdvancedSettingsViewModel.DEFAULT_SEVERITY.name
    )
}
