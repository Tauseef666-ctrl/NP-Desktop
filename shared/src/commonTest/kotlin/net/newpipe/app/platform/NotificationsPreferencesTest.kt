/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsPreferencesTest {

    @Test
    fun defaultsToEnabledWhenUnset() {
        val preferences = NotificationsPreferences(MapSettings())

        assertTrue(preferences.isEnabled())
        assertTrue(preferences.enabled.value)
    }

    @Test
    fun disablingRoundTripsAcrossInstances() {
        val settings = MapSettings()

        NotificationsPreferences(settings).setEnabled(false)

        assertFalse(NotificationsPreferences(settings).isEnabled())
    }

    @Test
    fun reEnablingRoundTripsAcrossInstances() {
        val settings = MapSettings()
        val preferences = NotificationsPreferences(settings)

        preferences.setEnabled(false)
        preferences.setEnabled(true)

        assertTrue(NotificationsPreferences(settings).isEnabled())
    }

    @Test
    fun persistsUnderDocumentedKey() {
        val settings = MapSettings()

        NotificationsPreferences(settings).setEnabled(false)

        assertEquals(false, settings.getBooleanOrNull(NotificationsPreferences.KEY))
    }

    @Test
    fun stateFlowReflectsUpdates() {
        val preferences = NotificationsPreferences(MapSettings())

        assertTrue(preferences.enabled.value)

        preferences.setEnabled(false)
        assertFalse(preferences.enabled.value)

        preferences.setEnabled(true)
        assertTrue(preferences.enabled.value)
    }
}
