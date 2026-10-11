/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.SearchBarField
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.viewmodel.search.SearchViewModel
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.search_empty_message
import newpipe.shared.generated.resources.search_empty_title
import newpipe.shared.generated.resources.search_history_clear
import newpipe.shared.generated.resources.search_history_empty
import newpipe.shared.generated.resources.search_history_title
import newpipe.shared.generated.resources.search_screen_placeholder
import newpipe.shared.generated.resources.search_screen_title
import newpipe.shared.generated.resources.search_suggestions_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Search destination. The input is live; recent searches and suggestions come from the persisted
 * history. Results stay empty until provider wiring lands.
 */
@Composable
fun SearchScreen(
    navigator: Navigator = koinInject(),
    viewModel: SearchViewModel = koinViewModel()
) {
    var query by remember { mutableStateOf("") }
    val history by viewModel.historyEntries.collectAsStateWithLifecycle()
    SearchScreenContent(
        query = query,
        onQueryChange = { query = it },
        onNavigateUp = { navigator.navigateUp() },
        history = history,
        suggestions = viewModel.suggestions(query),
        onClearHistory = { viewModel.clearHistory() },
        onSubmitQuery = { viewModel.record(it) }
    )
}

@Composable
fun SearchScreenContent(
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    history: List<String> = emptyList(),
    suggestions: List<String> = emptyList(),
    onClearHistory: () -> Unit = {},
    onSubmitQuery: (String) -> Unit = {}
) {
    PlusScaffold(
        toolbar = {
            TopToolbar(
                title = stringResource(Res.string.search_screen_title),
                onBack = onNavigateUp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = spaceNormal)
        ) {
            SearchBarField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.padding(horizontal = spaceLarge),
                placeholder = stringResource(Res.string.search_screen_placeholder),
                onSubmit = { onSubmitQuery(query) }
            )
            if (query.isBlank()) {
                SectionHeader(title = stringResource(Res.string.search_history_title))
                if (history.isEmpty()) {
                    EmptyState(title = stringResource(Res.string.search_history_empty))
                } else {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.padding(horizontal = spaceNormal)
                    ) {
                        Text(text = stringResource(Res.string.search_history_clear))
                    }
                    history.forEach { entry ->
                        PreferenceRow(
                            title = entry,
                            onClick = { onQueryChange(entry) }
                        )
                    }
                }
            } else {
                SectionHeader(title = stringResource(Res.string.search_suggestions_title))
                suggestions.forEach { suggestion ->
                    PreferenceRow(
                        title = suggestion,
                        onClick = { onQueryChange(suggestion) }
                    )
                }
            }
            SectionHeader(title = "Results")
            EmptyState(
                title = stringResource(Res.string.search_empty_title),
                message = stringResource(Res.string.search_empty_message)
            )
            Spacer(Modifier.height(spaceNormal))
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SearchScreenPreview() {
    SearchScreenContent(history = listOf("NewPipe updates", "Kotlin Multiplatform"))
}
