/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.platform.BuildInfo
import net.newpipe.app.preferences.AdvancedPreferences
import org.koin.core.annotation.KoinViewModel

/**
 * Immutable snapshot of the running build, derived purely from [BuildInfo] so it can be rendered by
 * the Advanced settings screen and asserted in tests.
 */
data class BuildInfoView(
    val isDebug: Boolean,
    val isRelease: Boolean,
    val variantLabel: String
)

/**
 * Drives the Advanced & diagnostics settings screen.
 *
 * The log-level control is real: selecting a level persists it under
 * [AdvancedPreferences.KEY_LOG_LEVEL] and applies it immediately to the global kermit
 * [Logger] via [Logger.setMinSeverity]. On construction the stored level (or the default
 * [DEFAULT_SEVERITY], which is [Severity.Info]) is applied and re-persisted in canonical form, so a
 * blank or corrupt value is healed rather than silently ignored.
 */
@KoinViewModel
class AdvancedSettingsViewModel(
    private val settings: Settings,
    private val buildInfo: BuildInfo
) : ViewModel() {

    private val initialSeverity: Severity =
        pureParseSeverity(settings.getStringOrNull(AdvancedPreferences.KEY_LOG_LEVEL))

    private val _logLevel = MutableStateFlow(pureCanonicalName(initialSeverity))

    /** Current kermit log level, always a canonical [Severity.name] such as "Info". */
    val logLevel: StateFlow<String> = _logLevel.asStateFlow()

    init {
        settings.putString(AdvancedPreferences.KEY_LOG_LEVEL, pureCanonicalName(initialSeverity))
        Logger.setMinSeverity(initialSeverity)
    }

    /**
     * Normalize [value] to a valid [Severity] (unknown/blank falls back to [DEFAULT_SEVERITY]),
     * persist the canonical name and apply it to the global logger immediately.
     */
    fun setLogLevel(value: String) {
        val severity = pureParseSeverity(value)
        settings.putString(AdvancedPreferences.KEY_LOG_LEVEL, pureCanonicalName(severity))
        Logger.setMinSeverity(severity)
        _logLevel.value = pureCanonicalName(severity)
    }

    /** Pure snapshot of the current build variant, derived from the injected [BuildInfo]. */
    fun buildInfoView(): BuildInfoView = BuildInfoView(
        isDebug = buildInfo.isDebug,
        isRelease = buildInfo.isReleaseApk,
        variantLabel = pureVariant(buildInfo.isDebug, buildInfo.isReleaseApk)
    )

    companion object {
        /** Sensible default used when nothing is stored or the stored value is unreadable. */
        val DEFAULT_SEVERITY: Severity = Severity.Info

        /** Levels offered in the UI. [Severity.Assert] is intentionally not user-selectable. */
        val SUPPORTED_SEVERITIES: List<Severity> = listOf(
            Severity.Verbose,
            Severity.Debug,
            Severity.Info,
            Severity.Warn,
            Severity.Error
        )

        const val VARIANT_DEBUG = "debug"
        const val VARIANT_RELEASE = "release"
        const val VARIANT_DEVELOPMENT = "development"

        /**
         * Parse a stored/user-supplied level into a valid [Severity]. Matching is case-insensitive
         * and whitespace-trimmed; blank or unknown input yields [fallback].
         */
        fun pureParseSeverity(raw: String?, fallback: Severity = DEFAULT_SEVERITY): Severity {
            val candidate = raw?.trim().orEmpty()
            if (candidate.isEmpty()) return fallback
            return Severity.entries.firstOrNull { it.name.equals(candidate, ignoreCase = true) }
                ?: fallback
        }

        /** Canonical persisted representation of a [severity], i.e. its enum [Severity.name]. */
        fun pureCanonicalName(severity: Severity): String = severity.name

        /** Derive the build-variant label. Debug wins over release, matching the Android build. */
        fun pureVariant(isDebug: Boolean, isReleaseApk: Boolean): String = when {
            isDebug -> VARIANT_DEBUG
            isReleaseApk -> VARIANT_RELEASE
            else -> VARIANT_DEVELOPMENT
        }
    }
}
