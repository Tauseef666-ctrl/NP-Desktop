/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import net.newpipe.app.download.DownloadRequest
import net.newpipe.app.download.DownloadState
import net.newpipe.app.download.DownloadTask
import net.newpipe.app.extensions.withKoin
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.downloads_open_file
import newpipe.shared.generated.resources.downloads_open_folder
import newpipe.shared.generated.resources.downloads_subtitle_language_label
import newpipe.shared.generated.resources.downloads_subtitle_url_label
import newpipe.shared.generated.resources.downloads_tab_completed
import newpipe.shared.generated.resources.downloads_tab_empty_title
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class DownloadsTabsTest {

    private fun task(id: String, title: String, status: DownloadState) = DownloadTask(
        request = DownloadRequest(
            id = id,
            url = "https://example.org/$id",
            destinationPath = "/tmp/$id",
            title = title
        ),
        status = status
    )

    @Test
    fun tabsShowAllStatusesFiltered() = runComposeUiTest {
        val downloading = task("a", "Alpha", DownloadState.DOWNLOADING)
        val completed = task("b", "Beta", DownloadState.COMPLETED)
        withKoin(
            modules = emptyList(),
            content = {
                var selected by remember { mutableStateOf(DownloadCategory.ALL) }
                DownloadsScreenContent(
                    tasks = listOf(downloading, completed),
                    selectedCategory = selected,
                    onCategorySelect = { selected = it }
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.downloads_tab_completed), substring = true)
                    .performClick()
                onNodeWithText("Beta").assertIsDisplayed()
                onNodeWithText("Alpha").assertDoesNotExist()
            }
        )
    }

    @Test
    fun completedRowShowsOpenActions() = runComposeUiTest {
        val completed = task("b", "Beta", DownloadState.COMPLETED)
        withKoin(
            modules = emptyList(),
            content = {
                DownloadsScreenContent(tasks = listOf(completed))
            },
            onContent = {
                onNodeWithText(getString(Res.string.downloads_open_file)).assertIsDisplayed()
                onNodeWithText(getString(Res.string.downloads_open_folder)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun emptyTabShowsEmptyMessage() = runComposeUiTest {
        val downloading = task("a", "Alpha", DownloadState.DOWNLOADING)
        withKoin(
            modules = emptyList(),
            content = {
                DownloadsScreenContent(
                    tasks = listOf(downloading),
                    selectedCategory = DownloadCategory.COMPLETED
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.downloads_tab_empty_title)).assertIsDisplayed()
            }
        )
    }

    @Test
    fun newDownloadSectionShowsSubtitleFields() = runComposeUiTest {
        withKoin(
            modules = emptyList(),
            content = {
                DownloadsScreenContent(defaultDirectory = "/downloads")
            },
            onContent = {
                onNodeWithText(getString(Res.string.downloads_subtitle_url_label)).assertIsDisplayed()
                onNodeWithText(getString(Res.string.downloads_subtitle_language_label)).assertIsDisplayed()
            }
        )
    }
}