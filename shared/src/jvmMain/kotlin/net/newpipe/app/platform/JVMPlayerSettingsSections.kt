/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import androidx.compose.runtime.Composable
import net.newpipe.app.screen.settings.PlayerDefaultsSettingsSection
import net.newpipe.app.screen.settings.PlayerResumeSettingsSection
import net.newpipe.app.screen.settings.ShortcutReferenceSection
import org.koin.core.annotation.Singleton

@Singleton(binds = [PlayerSettingsSections::class])
class JVMPlayerSettingsSections : PlayerSettingsSections {
    override val isAvailable = true

    /**
     * JVM-rendered player settings extras. [PlayerDefaultsSettingsSection] is shared commonMain UI
     * surfaced here because the desktop platform owns this section list.
     */
    @Composable
    override fun Render() {
        PlayerResumeSettingsSection()
        PlayerDefaultsSettingsSection()
        ShortcutReferenceSection()
    }
}
