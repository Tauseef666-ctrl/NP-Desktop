/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

/**
 * Preference keys, values, and defaults for the Content settings screen. Keys and defaults match
 * the Android app (content_settings.xml) so imported settings behave the same.
 */
object ContentPreferences {
    const val KEY_CONTENT_LANGUAGE = "content_language"
    const val KEY_CONTENT_COUNTRY = "content_country"
    const val KEY_AGE_RESTRICTED_CONTENT = "show_age_restricted_content"
    const val KEY_YOUTUBE_RESTRICTED_MODE = "youtube_restricted_mode_enabled"
    const val KEY_SEARCH_SUGGESTIONS = "show_search_suggestions"
    const val KEY_IMAGE_QUALITY = "image_quality_key"
    const val KEY_SHOW_COMMENTS = "show_comments"
    const val KEY_SHOW_NEXT_VIDEO = "show_next_video"
    const val KEY_SHOW_DESCRIPTION = "show_description"
    const val KEY_SHOW_META_INFO = "show_meta_info"
    const val KEY_FEED_UPDATE_THRESHOLD = "feed_update_threshold"

    const val SYSTEM = "system"

    const val SUGGESTIONS_BOTH = "both"
    const val SUGGESTIONS_LOCAL = "local"
    const val SUGGESTIONS_NONE = "none"

    const val IMAGE_QUALITY_DO_NOT_LOAD = "do_not_load"
    const val IMAGE_QUALITY_LOW = "low"
    const val IMAGE_QUALITY_MEDIUM = "medium"
    const val IMAGE_QUALITY_HIGH = "high"

    const val DEFAULT_CONTENT_LANGUAGE = SYSTEM
    const val DEFAULT_CONTENT_COUNTRY = SYSTEM
    const val DEFAULT_AGE_RESTRICTED_CONTENT = false
    const val DEFAULT_YOUTUBE_RESTRICTED_MODE = false
    const val DEFAULT_SEARCH_SUGGESTIONS = SUGGESTIONS_BOTH
    const val DEFAULT_IMAGE_QUALITY = IMAGE_QUALITY_MEDIUM
    const val DEFAULT_SHOW_COMMENTS = true
    const val DEFAULT_SHOW_NEXT_VIDEO = true
    const val DEFAULT_SHOW_DESCRIPTION = true
    const val DEFAULT_SHOW_META_INFO = true
    const val DEFAULT_FEED_UPDATE_THRESHOLD = "300"
}
