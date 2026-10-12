/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import net.newpipe.app.extensions.withKoin
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.search_history_clear
import newpipe.shared.generated.resources.search_history_empty
import newpipe.shared.generated.resources.search_history_title
import newpipe.shared.generated.resources.search_screen_placeholder
import newpipe.shared.generated.resources.search_screen_title
import newpipe.shared.generated.resources.search_suggestions_title
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class SearchScreenTest {

    @Test
    fun rendersTitleAndSearchBox() = runComposeUiTest {
        withKoin(
            modules = emptyList(),
            content = { SearchScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.search_screen_title))
                    .assertIsDisplayed()
                onNodeWithText(getString(Res.string.search_screen_placeholder))
                    .assertIsDisplayed()
            }
        )
    }

    @Test
    fun typingUpdatesQuery() = runComposeUiTest {
        withKoin(
            modules = emptyList(),
            content = {
                var query by remember { mutableStateOf("") }
                SearchScreenContent(query = query, onQueryChange = { query = it })
            },
            onContent = {
                onNode(hasSetTextAction()).performClick().performTextInput("abc")

                onNodeWithText("abc").assertExists()
            }
        )
    }

    @Test
    fun showsHonestEmptyState() = runComposeUiTest {
        withKoin(
            modules = emptyList(),
            content = { SearchScreenContent() },
            onContent = {
                onNodeWithText(getString(Res.string.search_history_empty))
                    .assertIsDisplayed()
            }
        )
    }

    @Test
    fun showsRecentSearchesWhenQueryBlank() = runComposeUiTest {
        withKoin(
            modules = emptyList(),
            content = { SearchScreenContent(history = listOf("alpha", "beta")) },
            onContent = {
                onNodeWithText(getString(Res.string.search_history_title))
                    .assertIsDisplayed()
                onNodeWithText("alpha").assertIsDisplayed()
                onNodeWithText("beta").assertIsDisplayed()
            }
        )
    }

    @Test
    fun showsSuggestionsWhenQueryNonBlank() = runComposeUiTest {
        withKoin(
            modules = emptyList(),
            content = {
                SearchScreenContent(
                    query = "alp",
                    history = listOf("alpha"),
                    suggestions = listOf("alpha")
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.search_suggestions_title))
                    .assertIsDisplayed()
                onNodeWithText("alpha").assertExists()
            }
        )
    }

    @Test
    fun clearActionInvokesCallback() = runComposeUiTest {
        var cleared = false
        withKoin(
            modules = emptyList(),
            content = {
                SearchScreenContent(
                    history = listOf("alpha"),
                    onClearHistory = { cleared = true }
                )
            },
            onContent = {
                onNodeWithText(getString(Res.string.search_history_clear)).performClick()
                assertTrue(cleared)
            }
        )
    }
}
