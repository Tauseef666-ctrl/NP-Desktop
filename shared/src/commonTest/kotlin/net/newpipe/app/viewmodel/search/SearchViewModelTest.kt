/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.search

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.newpipe.app.search.SearchHistoryStore

class SearchViewModelTest {

    private fun viewModel(settings: MapSettings = MapSettings()) =
        SearchViewModel(SearchHistoryStore(settings, Json { ignoreUnknownKeys = true }))

    @Test
    fun recordUpdatesHistoryEntries() {
        val vm = viewModel()
        vm.record("alpha")
        vm.record("beta")
        assertEquals(listOf("beta", "alpha"), vm.historyEntries.value)
    }

    @Test
    fun suggestionsReflectRecordedHistory() {
        val vm = viewModel()
        vm.record("Kotlin Multiplatform")
        vm.record("Jetpack")
        assertEquals(listOf("Kotlin Multiplatform"), vm.suggestions("kotlin"))
    }

    @Test
    fun clearHistoryEmptiesEntries() {
        val vm = viewModel()
        vm.record("alpha")
        vm.clearHistory()
        assertTrue(vm.historyEntries.value.isEmpty())
    }

    @Test
    fun suggestionsExcludeExactMatch() {
        val vm = viewModel()
        vm.record("alpha")
        assertTrue(vm.suggestions("alpha").isEmpty())
    }
}
