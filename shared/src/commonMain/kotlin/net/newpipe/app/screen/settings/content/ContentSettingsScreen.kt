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
import net.newpipe.app.composable.ListPreference
import net.newpipe.app.composable.ListPreferenceEntry
import net.newpipe.app.composable.SwitchPreference
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.preferences.ContentPreferences
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
    val contentLanguage by viewModel.contentLanguage.collectAsStateWithLifecycle()
    val contentCountry by viewModel.contentCountry.collectAsStateWithLifecycle()
    val ageRestrictedContent by viewModel.ageRestrictedContent.collectAsStateWithLifecycle()
    val youtubeRestrictedMode by viewModel.youtubeRestrictedMode.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val imageQuality by viewModel.imageQuality.collectAsStateWithLifecycle()
    val showComments by viewModel.showComments.collectAsStateWithLifecycle()
    val showNextVideo by viewModel.showNextVideo.collectAsStateWithLifecycle()
    val showDescription by viewModel.showDescription.collectAsStateWithLifecycle()
    val showMetaInfo by viewModel.showMetaInfo.collectAsStateWithLifecycle()
    val feedUpdateThreshold by viewModel.feedUpdateThreshold.collectAsStateWithLifecycle()

    ContentSettingsScreenContent(
        providers = providers,
        contentLanguage = contentLanguage,
        contentCountry = contentCountry,
        ageRestrictedContent = ageRestrictedContent,
        youtubeRestrictedMode = youtubeRestrictedMode,
        searchSuggestions = searchSuggestions,
        imageQuality = imageQuality,
        showComments = showComments,
        showNextVideo = showNextVideo,
        showDescription = showDescription,
        showMetaInfo = showMetaInfo,
        feedUpdateThreshold = feedUpdateThreshold,
        onContentLanguageChange = viewModel::setContentLanguage,
        onContentCountryChange = viewModel::setContentCountry,
        onAgeRestrictedContentChange = viewModel::setAgeRestrictedContent,
        onYoutubeRestrictedModeChange = viewModel::setYoutubeRestrictedMode,
        onSearchSuggestionsChange = viewModel::setSearchSuggestions,
        onImageQualityChange = viewModel::setImageQuality,
        onShowCommentsChange = viewModel::setShowComments,
        onShowNextVideoChange = viewModel::setShowNextVideo,
        onShowDescriptionChange = viewModel::setShowDescription,
        onShowMetaInfoChange = viewModel::setShowMetaInfo,
        onFeedUpdateThresholdChange = viewModel::setFeedUpdateThreshold,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun ContentSettingsScreenContent(
    providers: List<ProviderView> = emptyList(),
    contentLanguage: String = ContentPreferences.DEFAULT_CONTENT_LANGUAGE,
    contentCountry: String = ContentPreferences.DEFAULT_CONTENT_COUNTRY,
    ageRestrictedContent: Boolean = ContentPreferences.DEFAULT_AGE_RESTRICTED_CONTENT,
    youtubeRestrictedMode: Boolean = ContentPreferences.DEFAULT_YOUTUBE_RESTRICTED_MODE,
    searchSuggestions: String = ContentPreferences.DEFAULT_SEARCH_SUGGESTIONS,
    imageQuality: String = ContentPreferences.DEFAULT_IMAGE_QUALITY,
    showComments: Boolean = ContentPreferences.DEFAULT_SHOW_COMMENTS,
    showNextVideo: Boolean = ContentPreferences.DEFAULT_SHOW_NEXT_VIDEO,
    showDescription: Boolean = ContentPreferences.DEFAULT_SHOW_DESCRIPTION,
    showMetaInfo: Boolean = ContentPreferences.DEFAULT_SHOW_META_INFO,
    feedUpdateThreshold: String = ContentPreferences.DEFAULT_FEED_UPDATE_THRESHOLD,
    onContentLanguageChange: (String) -> Unit = {},
    onContentCountryChange: (String) -> Unit = {},
    onAgeRestrictedContentChange: (Boolean) -> Unit = {},
    onYoutubeRestrictedModeChange: (Boolean) -> Unit = {},
    onSearchSuggestionsChange: (String) -> Unit = {},
    onImageQualityChange: (String) -> Unit = {},
    onShowCommentsChange: (Boolean) -> Unit = {},
    onShowNextVideoChange: (Boolean) -> Unit = {},
    onShowDescriptionChange: (Boolean) -> Unit = {},
    onShowMetaInfoChange: (Boolean) -> Unit = {},
    onFeedUpdateThresholdChange: (String) -> Unit = {},
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
            SectionHeader(title = "Language and region")
            ListPreference(
                title = "Default content language",
                entries = ContentLanguages,
                selectedValue = contentLanguage,
                onValueSelected = onContentLanguageChange
            )
            ListPreference(
                title = "Default content country",
                entries = ContentCountries,
                selectedValue = contentCountry,
                onValueSelected = onContentCountryChange
            )

            SectionHeader(title = "Content of this app")
            SwitchPreference(
                title = "Show age-restricted content",
                checked = ageRestrictedContent,
                onCheckedChange = onAgeRestrictedContentChange
            )
            SwitchPreference(
                title = "YouTube restricted mode",
                summary = "Filter potentially inappropriate video search results",
                checked = youtubeRestrictedMode,
                onCheckedChange = onYoutubeRestrictedModeChange
            )
            ListPreference(
                title = "Show search suggestions",
                entries = SearchSuggestions,
                selectedValue = searchSuggestions,
                onValueSelected = onSearchSuggestionsChange
            )
            ListPreference(
                title = "Image quality",
                entries = ImageQualities,
                selectedValue = imageQuality,
                onValueSelected = onImageQualityChange
            )
            SwitchPreference(
                title = "Show comments",
                checked = showComments,
                onCheckedChange = onShowCommentsChange
            )
            SwitchPreference(
                title = "Show \"Next\" and \"Similar\" videos",
                checked = showNextVideo,
                onCheckedChange = onShowNextVideoChange
            )
            SwitchPreference(
                title = "Show description",
                checked = showDescription,
                onCheckedChange = onShowDescriptionChange
            )
            SwitchPreference(
                title = "Show meta info",
                summary = "Additional information when long-pressing an upload date",
                checked = showMetaInfo,
                onCheckedChange = onShowMetaInfoChange
            )

            SectionHeader(title = "Feed")
            ListPreference(
                title = "Feed update threshold",
                summary = "How old a feed item may be before it is considered new",
                entries = FeedThresholds,
                selectedValue = feedUpdateThreshold,
                onValueSelected = onFeedUpdateThresholdChange
            )

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

private val ContentLanguages = listOf(
    ListPreferenceEntry(ContentPreferences.SYSTEM, "System default"),
    ListPreferenceEntry("en", "English"),
    ListPreferenceEntry("de", "Deutsch"),
    ListPreferenceEntry("fr", "Français"),
    ListPreferenceEntry("es", "Español"),
    ListPreferenceEntry("hi", "हिन्दी"),
    ListPreferenceEntry("ar", "العربية"),
    ListPreferenceEntry("pt", "Português"),
    ListPreferenceEntry("ru", "Русский"),
    ListPreferenceEntry("ja", "日本語"),
    ListPreferenceEntry("zh", "中文")
)

private val ContentCountries = listOf(
    ListPreferenceEntry(ContentPreferences.SYSTEM, "System default"),
    ListPreferenceEntry("US", "United States"),
    ListPreferenceEntry("DE", "Germany"),
    ListPreferenceEntry("FR", "France"),
    ListPreferenceEntry("ES", "Spain"),
    ListPreferenceEntry("IN", "India"),
    ListPreferenceEntry("BR", "Brazil"),
    ListPreferenceEntry("JP", "Japan")
)

private val SearchSuggestions = listOf(
    ListPreferenceEntry(ContentPreferences.SUGGESTIONS_BOTH, "Local and remote"),
    ListPreferenceEntry(ContentPreferences.SUGGESTIONS_LOCAL, "Local only"),
    ListPreferenceEntry(ContentPreferences.SUGGESTIONS_NONE, "None")
)

private val ImageQualities = listOf(
    ListPreferenceEntry(ContentPreferences.IMAGE_QUALITY_DO_NOT_LOAD, "Do not load images"),
    ListPreferenceEntry(ContentPreferences.IMAGE_QUALITY_LOW, "Low quality"),
    ListPreferenceEntry(ContentPreferences.IMAGE_QUALITY_MEDIUM, "Medium quality"),
    ListPreferenceEntry(ContentPreferences.IMAGE_QUALITY_HIGH, "High quality")
)

private val FeedThresholds = listOf(
    ListPreferenceEntry("0", "Always update"),
    ListPreferenceEntry("300", "5 minutes"),
    ListPreferenceEntry("900", "15 minutes"),
    ListPreferenceEntry("3600", "1 hour"),
    ListPreferenceEntry("21600", "6 hours"),
    ListPreferenceEntry("43200", "12 hours"),
    ListPreferenceEntry("86400", "1 day")
)

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
