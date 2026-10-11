/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchSuggestionsTest {

    private val history = listOf("Kotlin Multiplatform", "Jetpack Compose", "kotlin basics", "NewPipe")

    @Test
    fun blankQueryReturnsEmpty() {
        assertTrue(matchSuggestions("", history).isEmpty())
        assertTrue(matchSuggestions("   ", history).isEmpty())
    }

    @Test
    fun matchesCaseInsensitiveContains() {
        val result = matchSuggestions("KOTLIN", history)
        assertEquals(listOf("Kotlin Multiplatform", "kotlin basics"), result)
    }

    @Test
    fun excludesExactMatchCaseInsensitively() {
        val result = matchSuggestions("kotlin basics", listOf("kotlin basics", "kotlin basics 101"))
        assertEquals(listOf("kotlin basics 101"), result)
    }

    @Test
    fun respectsLimit() {
        val many = listOf("a1", "a2", "a3", "a4")
        assertEquals(listOf("a1", "a2"), matchSuggestions("a", many, limit = 2))
    }

    @Test
    fun preservesHistoryOrder() {
        val ordered = listOf("zeta", "alpha", "beta")
        assertEquals(ordered, matchSuggestions("a", ordered))
    }
}
