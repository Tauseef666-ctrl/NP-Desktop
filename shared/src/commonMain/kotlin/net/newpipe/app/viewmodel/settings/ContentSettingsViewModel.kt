/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.newpipe.app.preferences.ContentPreferences
import net.newpipe.app.provider.MediaProvider
import net.newpipe.app.provider.ProviderCapabilities
import net.newpipe.app.provider.ProviderRegistry
import org.koin.core.annotation.KoinViewModel

data class ProviderView(
    val id: String,
    val name: String,
    val isAvailable: Boolean,
    val capabilities: ProviderCapabilities
)

@KoinViewModel
class ContentSettingsViewModel(
    private val registry: ProviderRegistry,
    private val settings: Settings
) : ViewModel() {

    val providers: StateFlow<List<ProviderView>> = registry.providers
        .map { list -> list.map { it.toView() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = registry.providers.value.map { it.toView() }
        )

    val hasProviders: StateFlow<Boolean> = registry.providers
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = registry.providers.value.isNotEmpty()
        )

    val contentLanguage: MutableStateFlow<String> = MutableStateFlow(
        settings.getString(
            ContentPreferences.KEY_CONTENT_LANGUAGE,
            ContentPreferences.DEFAULT_CONTENT_LANGUAGE
        )
    )

    val contentCountry: MutableStateFlow<String> = MutableStateFlow(
        settings.getString(
            ContentPreferences.KEY_CONTENT_COUNTRY,
            ContentPreferences.DEFAULT_CONTENT_COUNTRY
        )
    )

    val ageRestrictedContent: MutableStateFlow<Boolean> = MutableStateFlow(
        settings.getBoolean(
            ContentPreferences.KEY_AGE_RESTRICTED_CONTENT,
            ContentPreferences.DEFAULT_AGE_RESTRICTED_CONTENT
        )
    )

    val youtubeRestrictedMode: MutableStateFlow<Boolean> = MutableStateFlow(
        settings.getBoolean(
            ContentPreferences.KEY_YOUTUBE_RESTRICTED_MODE,
            ContentPreferences.DEFAULT_YOUTUBE_RESTRICTED_MODE
        )
    )

    val searchSuggestions: MutableStateFlow<String> = MutableStateFlow(
        settings.getString(
            ContentPreferences.KEY_SEARCH_SUGGESTIONS,
            ContentPreferences.DEFAULT_SEARCH_SUGGESTIONS
        )
    )

    val imageQuality: MutableStateFlow<String> = MutableStateFlow(
        settings.getString(
            ContentPreferences.KEY_IMAGE_QUALITY,
            ContentPreferences.DEFAULT_IMAGE_QUALITY
        )
    )

    val showComments: MutableStateFlow<Boolean> = MutableStateFlow(
        settings.getBoolean(
            ContentPreferences.KEY_SHOW_COMMENTS,
            ContentPreferences.DEFAULT_SHOW_COMMENTS
        )
    )

    val showNextVideo: MutableStateFlow<Boolean> = MutableStateFlow(
        settings.getBoolean(
            ContentPreferences.KEY_SHOW_NEXT_VIDEO,
            ContentPreferences.DEFAULT_SHOW_NEXT_VIDEO
        )
    )

    val showDescription: MutableStateFlow<Boolean> = MutableStateFlow(
        settings.getBoolean(
            ContentPreferences.KEY_SHOW_DESCRIPTION,
            ContentPreferences.DEFAULT_SHOW_DESCRIPTION
        )
    )

    val showMetaInfo: MutableStateFlow<Boolean> = MutableStateFlow(
        settings.getBoolean(
            ContentPreferences.KEY_SHOW_META_INFO,
            ContentPreferences.DEFAULT_SHOW_META_INFO
        )
    )

    val feedUpdateThreshold: MutableStateFlow<String> = MutableStateFlow(
        settings.getString(
            ContentPreferences.KEY_FEED_UPDATE_THRESHOLD,
            ContentPreferences.DEFAULT_FEED_UPDATE_THRESHOLD
        )
    )

    fun setContentLanguage(value: String) = putString(ContentPreferences.KEY_CONTENT_LANGUAGE, contentLanguage, value)
    fun setContentCountry(value: String) = putString(ContentPreferences.KEY_CONTENT_COUNTRY, contentCountry, value)
    fun setAgeRestrictedContent(value: Boolean) = putBoolean(ContentPreferences.KEY_AGE_RESTRICTED_CONTENT, ageRestrictedContent, value)
    fun setYoutubeRestrictedMode(value: Boolean) = putBoolean(ContentPreferences.KEY_YOUTUBE_RESTRICTED_MODE, youtubeRestrictedMode, value)
    fun setSearchSuggestions(value: String) = putString(ContentPreferences.KEY_SEARCH_SUGGESTIONS, searchSuggestions, value)
    fun setImageQuality(value: String) = putString(ContentPreferences.KEY_IMAGE_QUALITY, imageQuality, value)
    fun setShowComments(value: Boolean) = putBoolean(ContentPreferences.KEY_SHOW_COMMENTS, showComments, value)
    fun setShowNextVideo(value: Boolean) = putBoolean(ContentPreferences.KEY_SHOW_NEXT_VIDEO, showNextVideo, value)
    fun setShowDescription(value: Boolean) = putBoolean(ContentPreferences.KEY_SHOW_DESCRIPTION, showDescription, value)
    fun setShowMetaInfo(value: Boolean) = putBoolean(ContentPreferences.KEY_SHOW_META_INFO, showMetaInfo, value)
    fun setFeedUpdateThreshold(value: String) = putString(ContentPreferences.KEY_FEED_UPDATE_THRESHOLD, feedUpdateThreshold, value)

    private fun putString(key: String, flow: MutableStateFlow<String>, value: String) {
        settings.putString(key, value)
        flow.value = value
    }

    private fun putBoolean(key: String, flow: MutableStateFlow<Boolean>, value: Boolean) {
        settings.putBoolean(key, value)
        flow.value = value
    }

    private fun MediaProvider.toView(): ProviderView = ProviderView(
        id = id,
        name = name,
        isAvailable = isAvailable,
        capabilities = capabilities
    )
}