/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Singleton

/**
 * Persists whether desktop notifications are enabled (default on). Kept as a hot [StateFlow] so the
 * settings screen and any future consumer observe changes immediately. Blank/missing storage falls
 * back to [DEFAULT_ENABLED].
 */
@Singleton
class NotificationsPreferences(
    private val settings: Settings
) {

    private val _enabled = MutableStateFlow(settings.getBoolean(KEY, DEFAULT_ENABLED))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(enabled: Boolean) {
        settings.putBoolean(KEY, enabled)
        _enabled.value = enabled
    }

    companion object {
        const val KEY = "npp_notifications_enabled"
        const val DEFAULT_ENABLED = true
    }
}
