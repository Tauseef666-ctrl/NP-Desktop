/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import com.russhwolf.settings.MapSettings
import net.newpipe.app.platform.NotificationsPreferences
import net.newpipe.app.platform.Notifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingNotifier(
    override val isSupported: Boolean = true
) : Notifier {

    val calls = mutableListOf<Pair<String, String>>()
    var result: Boolean = true

    override fun notify(title: String, body: String): Boolean {
        calls += title to body
        return result
    }
}

class NotificationsSettingsViewModelTest {

    private fun viewModel(
        notifier: Notifier = RecordingNotifier(),
        settings: MapSettings = MapSettings()
    ) = NotificationsSettingsViewModel(NotificationsPreferences(settings), notifier)

    @Test
    fun defaultsToEnabled() {
        val viewModel = viewModel()

        assertTrue(viewModel.enabled.value)
        assertTrue(viewModel.isSupported)
    }

    @Test
    fun setEnabledPersistsToStorage() {
        val settings = MapSettings()
        val viewModel = viewModel(settings = settings)

        viewModel.setEnabled(false)

        assertFalse(viewModel.enabled.value)
        assertFalse(NotificationsPreferences(settings).isEnabled())
    }

    @Test
    fun isSupportedReflectsNotifier() {
        val viewModel = viewModel(notifier = RecordingNotifier(isSupported = false))

        assertFalse(viewModel.isSupported)
    }

    @Test
    fun sendTestNotificationDelegatesAndReportsSuccess() {
        val notifier = RecordingNotifier().apply { result = true }
        val viewModel = viewModel(notifier = notifier)

        val sent = viewModel.sendTestNotification()

        assertTrue(sent)
        assertEquals(1, notifier.calls.size)
        assertEquals(NotificationsSettingsViewModel.TEST_TITLE, notifier.calls.single().first)
        assertEquals(NotificationsSettingsViewModel.TEST_BODY, notifier.calls.single().second)
    }

    @Test
    fun sendTestNotificationReportsFailure() {
        val notifier = RecordingNotifier().apply { result = false }
        val viewModel = viewModel(notifier = notifier)

        assertFalse(viewModel.sendTestNotification())
    }
}
