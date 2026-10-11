/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.player

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import net.newpipe.app.extensions.withKoin
import net.newpipe.app.player.RepeatMode
import net.newpipe.app.player.VideoSettings
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.player_autoplay_next
import newpipe.shared.generated.resources.player_subtitles
import newpipe.shared.generated.resources.player_unavailable
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class PlayerSettingsPanelTest {

    @Test
    fun unavailableEngineLabelsRenderingDependentControls() = runComposeUiTest {
        withKoin(
            modules = emptyList(),
            content = {
                PlayerAdvancedSettings(
                    settings = VideoSettings(subtitlesEnabled = true),
                    repeatMode = RepeatMode.OFF,
                    isEngineAvailable = false
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.player_subtitles)).assertExists()
                onAllNodesWithText(getString(Res.string.player_unavailable)).onFirst().assertExists()
            }
        )
    }

    @Test
    fun availableEngineHidesUnavailableLabel() = runComposeUiTest {
        withKoin(
            modules = emptyList(),
            content = {
                PlayerAdvancedSettings(
                    settings = VideoSettings(),
                    repeatMode = RepeatMode.OFF,
                    isEngineAvailable = true
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.player_autoplay_next)).assertExists()
                onNodeWithText(getString(Res.string.player_unavailable)).assertDoesNotExist()
            }
        )
    }
}
