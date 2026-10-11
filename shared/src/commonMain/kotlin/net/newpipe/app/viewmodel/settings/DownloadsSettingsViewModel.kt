/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.download.DownloadEngine
import net.newpipe.app.platform.defaultDownloadDirectory
import net.newpipe.app.preferences.DownloadsPreferences
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the Downloads & Storage settings screen. The directory preference is a real override of
 * the platform default: it is persisted in [Settings] and consumed by the downloads engine setup.
 */
@KoinViewModel
class DownloadsSettingsViewModel(
    private val settings: Settings,
    private val engine: DownloadEngine
) : ViewModel() {

    val isEngineAvailable: Boolean = engine.isAvailable

    /** The platform's default folder, shown as the fallback and used when the override is blank. */
    val platformDefault: String = defaultDownloadDirectory()

    private val _directory = MutableStateFlow(resolveDirectory())

    /** Effective download directory: the persisted override when set, else [platformDefault]. */
    val directory: StateFlow<String> = _directory.asStateFlow()

    /** Persist [path] as the download directory; a blank value resets to [platformDefault]. */
    fun setDirectory(path: String) {
        val trimmed = path.trim()
        settings.putString(DownloadsPreferences.KEY_DOWNLOAD_DIRECTORY, trimmed)
        _directory.value = trimmed.ifBlank { platformDefault }
    }

    /** Remove the override so downloads go back to the platform default. */
    fun resetDirectory() = setDirectory(DownloadsPreferences.DEFAULT_DOWNLOAD_DIRECTORY)

    fun clearCompleted() = engine.clearCompleted()

    private fun resolveDirectory(): String =
        settings
            .getString(
                DownloadsPreferences.KEY_DOWNLOAD_DIRECTORY,
                DownloadsPreferences.DEFAULT_DOWNLOAD_DIRECTORY
            )
            .ifBlank { platformDefault }
}
