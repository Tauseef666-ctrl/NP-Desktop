/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import co.touchlab.kermit.Severity
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.newpipe.app.platform.BuildInfo
import net.newpipe.app.preferences.AdvancedPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeBuildInfo(
    override val isDebug: Boolean,
    override val isReleaseApk: Boolean
) : BuildInfo

class AdvancedSettingsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- pure parsing / normalization ----

    @Test
    fun parse_acceptsValidSeverityNames() {
        assertEquals(Severity.Verbose, AdvancedSettingsViewModel.pureParseSeverity("Verbose"))
        assertEquals(Severity.Debug, AdvancedSettingsViewModel.pureParseSeverity("Debug"))
        assertEquals(Severity.Info, AdvancedSettingsViewModel.pureParseSeverity("Info"))
        assertEquals(Severity.Warn, AdvancedSettingsViewModel.pureParseSeverity("Warn"))
        assertEquals(Severity.Error, AdvancedSettingsViewModel.pureParseSeverity("Error"))
        assertEquals(Severity.Assert, AdvancedSettingsViewModel.pureParseSeverity("Assert"))
    }

    @Test
    fun parse_isCaseInsensitiveAndTrimmed() {
        assertEquals(Severity.Info, AdvancedSettingsViewModel.pureParseSeverity("INFO"))
        assertEquals(Severity.Debug, AdvancedSettingsViewModel.pureParseSeverity("  debug  "))
        assertEquals(Severity.Warn, AdvancedSettingsViewModel.pureParseSeverity("wArN"))
    }

    @Test
    fun parse_unknownFallsBackToDefault() {
        assertEquals(Severity.Info, AdvancedSettingsViewModel.pureParseSeverity("BOGUS"))
        assertEquals(
            AdvancedSettingsViewModel.DEFAULT_SEVERITY,
            AdvancedSettingsViewModel.pureParseSeverity("BOGUS")
        )
    }

    @Test
    fun parse_unknownFallsBackToExplicitFallback() {
        assertEquals(
            Severity.Error,
            AdvancedSettingsViewModel.pureParseSeverity("BOGUS", Severity.Error)
        )
    }

    @Test
    fun parse_blankAndNullFallBackToDefault() {
        assertEquals(Severity.Info, AdvancedSettingsViewModel.pureParseSeverity(""))
        assertEquals(Severity.Info, AdvancedSettingsViewModel.pureParseSeverity("   "))
        assertEquals(Severity.Info, AdvancedSettingsViewModel.pureParseSeverity(null))
    }

    @Test
    fun canonicalNameRoundTripsThroughParse() {
        AdvancedSettingsViewModel.SUPPORTED_SEVERITIES.forEach { severity ->
            val canonical = AdvancedSettingsViewModel.pureCanonicalName(severity)
            assertEquals(severity, AdvancedSettingsViewModel.pureParseSeverity(canonical))
        }
    }

    // ---- pure build-variant derivation ----

    @Test
    fun variant_debugWinsOverRelease() {
        assertEquals(
            AdvancedSettingsViewModel.VARIANT_DEBUG,
            AdvancedSettingsViewModel.pureVariant(isDebug = true, isReleaseApk = false)
        )
        assertEquals(
            AdvancedSettingsViewModel.VARIANT_DEBUG,
            AdvancedSettingsViewModel.pureVariant(isDebug = true, isReleaseApk = true)
        )
    }

    @Test
    fun variant_releaseWhenOnlyRelease() {
        assertEquals(
            AdvancedSettingsViewModel.VARIANT_RELEASE,
            AdvancedSettingsViewModel.pureVariant(isDebug = false, isReleaseApk = true)
        )
    }

    @Test
    fun variant_developmentWhenNeither() {
        assertEquals(
            AdvancedSettingsViewModel.VARIANT_DEVELOPMENT,
            AdvancedSettingsViewModel.pureVariant(isDebug = false, isReleaseApk = false)
        )
    }

    // ---- ViewModel behavior ----

    @Test
    fun defaultsWhenBlank() {
        val settings = MapSettings()
        val vm = AdvancedSettingsViewModel(settings, FakeBuildInfo(isDebug = false, isReleaseApk = false))
        assertEquals("Info", vm.logLevel.value)
        assertEquals(
            "Info",
            settings.getString(AdvancedPreferences.KEY_LOG_LEVEL, "")
        )
    }

    @Test
    fun corruptPersistedValueFallsBackAndHeals() {
        val settings = MapSettings().apply {
            putString(AdvancedPreferences.KEY_LOG_LEVEL, "BOGUS")
        }
        val vm = AdvancedSettingsViewModel(settings, FakeBuildInfo(isDebug = false, isReleaseApk = false))
        assertEquals("Info", vm.logLevel.value)
        assertEquals(
            "Info",
            settings.getString(AdvancedPreferences.KEY_LOG_LEVEL, "")
        )
    }

    @Test
    fun setLogLevelPersistsCanonicalValueAndUpdatesFlow() {
        val settings = MapSettings()
        val vm = AdvancedSettingsViewModel(settings, FakeBuildInfo(isDebug = false, isReleaseApk = false))

        vm.setLogLevel("Error")

        assertEquals("Error", vm.logLevel.value)
        assertEquals("Error", settings.getString(AdvancedPreferences.KEY_LOG_LEVEL, ""))
    }

    @Test
    fun setLogLevelNormalizesCaseAndWhitespace() {
        val settings = MapSettings()
        val vm = AdvancedSettingsViewModel(settings, FakeBuildInfo(isDebug = false, isReleaseApk = false))

        vm.setLogLevel("  wArN ")

        assertEquals("Warn", vm.logLevel.value)
        assertEquals("Warn", settings.getString(AdvancedPreferences.KEY_LOG_LEVEL, ""))
    }

    @Test
    fun setLogLevelWithInvalidValueFallsBackToDefault() {
        val settings = MapSettings()
        val vm = AdvancedSettingsViewModel(settings, FakeBuildInfo(isDebug = false, isReleaseApk = false))

        vm.setLogLevel("BOGUS")

        assertEquals("Info", vm.logLevel.value)
        assertEquals("Info", settings.getString(AdvancedPreferences.KEY_LOG_LEVEL, ""))
    }

    @Test
    fun buildInfoView_debugVariant() {
        val vm = AdvancedSettingsViewModel(
            MapSettings(),
            FakeBuildInfo(isDebug = true, isReleaseApk = false)
        )
        val view = vm.buildInfoView()
        assertTrue(view.isDebug)
        assertFalse(view.isRelease)
        assertEquals(AdvancedSettingsViewModel.VARIANT_DEBUG, view.variantLabel)
    }

    @Test
    fun buildInfoView_releaseVariant() {
        val vm = AdvancedSettingsViewModel(
            MapSettings(),
            FakeBuildInfo(isDebug = false, isReleaseApk = true)
        )
        val view = vm.buildInfoView()
        assertFalse(view.isDebug)
        assertTrue(view.isRelease)
        assertEquals(AdvancedSettingsViewModel.VARIANT_RELEASE, view.variantLabel)
    }

    @Test
    fun buildInfoView_developmentVariant() {
        val vm = AdvancedSettingsViewModel(
            MapSettings(),
            FakeBuildInfo(isDebug = false, isReleaseApk = false)
        )
        val view = vm.buildInfoView()
        assertFalse(view.isDebug)
        assertFalse(view.isRelease)
        assertEquals(AdvancedSettingsViewModel.VARIANT_DEVELOPMENT, view.variantLabel)
    }
}
