/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import com.russhwolf.settings.Settings
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.preferences.UpdatePreferences
import net.newpipe.app.service.UpdateService
import org.koin.core.annotation.Singleton
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The desktop updater: the version comparison runs against the packaged version the desktop
 * module passes into [UpdateService] at startup, skipped versions are remembered in the shared
 * settings store and the installer runs elevated as soon as the user confirms.
 */
@Singleton(binds = [UpdateActions::class])
class JVMUpdateActions(private val settings: Settings) : UpdateActions {

    override val status: StateFlow<UpdateStatus> = UpdateService.status

    override val progress = UpdateService.progress

    override val currentVersion: String?
        get() = UpdateService.appVersion.takeIf { it.isNotBlank() }

    /** The startup check never runs twice, even when the shell recomposes. */
    private val startupCheckStarted = AtomicBoolean(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun checkOnStartup() {
        if (!startupCheckStarted.compareAndSet(false, true)) {
            return
        }
        // Unpackaged development runs have no comparable version: stay silent
        if (!UpdateService.isVersionKnown) {
            return
        }
        scope.launch {
            runCatching {
                val result = UpdateService.checkForUpdate()
                if (result is UpdateStatus.Available && skippedVersion() != result.update.version) {
                    UpdateService.announce(result)
                }
            }.onFailure { failure ->
                // Offline at startup is the normal silent no-op: log and never bother the user
                Logger.w(
                    messageString = "The startup update check stayed silent",
                    throwable = failure
                )
            }
        }
    }

    override fun checkManually() {
        scope.launch {
            UpdateService.checkForUpdate()
        }
    }

    override fun install() {
        val info = (status.value as? UpdateStatus.Available)?.update ?: return
        UpdateService.downloadInstall(info)
    }

    override fun dismiss() {
        UpdateService.reset()
    }

    override fun skip() {
        val info = (status.value as? UpdateStatus.Available)?.update ?: return
        runCatching {
            settings.putString(UpdatePreferences.KEY_SKIPPED_VERSION, info.version)
        }.onFailure { failure ->
            Logger.w(
                messageString = "The skipped version could not be stored",
                throwable = failure
            )
        }
        UpdateService.reset()
    }

    private fun skippedVersion(): String = runCatching {
        settings.getString(
            UpdatePreferences.KEY_SKIPPED_VERSION,
            UpdatePreferences.DEFAULT_SKIPPED_VERSION
        )
    }.getOrDefault(UpdatePreferences.DEFAULT_SKIPPED_VERSION)
}
