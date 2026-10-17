/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import net.newpipe.app.platform.Notifier
import net.newpipe.app.platform.NotificationsPreferences
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the Notifications settings screen.
 *
 * The enable/disable control is real: it persists under
 * [NotificationsPreferences.KEY] and gates the completion notifications the desktop download engine
 * posts. [isSupported] surfaces whether the host can deliver notifications at all, so the UI can
 * show an honest unavailable state instead of a dead toggle. [sendTestNotification] posts a real
 * notification and reports whether the platform accepted it.
 */
@KoinViewModel
class NotificationsSettingsViewModel(
    private val preferences: NotificationsPreferences,
    private val notifier: Notifier
) : ViewModel() {

    val enabled: StateFlow<Boolean> = preferences.enabled

    val isSupported: Boolean get() = notifier.isSupported

    fun setEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
    }

    fun sendTestNotification(): Boolean = notifier.notify(
        title = TEST_TITLE,
        body = TEST_BODY
    )

    companion object {
        const val TEST_TITLE = "NewPipe+"
        const val TEST_BODY = "Notifications are working."
    }
}
