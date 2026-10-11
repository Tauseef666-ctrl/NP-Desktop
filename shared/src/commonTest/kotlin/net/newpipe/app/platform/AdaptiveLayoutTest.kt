/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveLayoutTest {

    private val widths = listOf(0, 480, 700, 900, 1100, 1280, 1700, 1920, 2560, 3840)

    @Test
    fun compactModeCollapsesSidebar() {
        assertTrue(WorkspaceMode.COMPACT.isCompactSidebar)
        assertFalse(WorkspaceMode.BALANCED.isCompactSidebar)
        assertFalse(WorkspaceMode.EXPANDED.isCompactSidebar)
    }

    @Test
    fun homeColumnsOrderingHoldsAcrossWidths() {
        widths.forEach { width ->
            val compact = WorkspaceMode.COMPACT.homeColumns(width)
            val balanced = WorkspaceMode.BALANCED.homeColumns(width)
            val expanded = WorkspaceMode.EXPANDED.homeColumns(width)
            assertTrue(
                compact <= balanced && balanced <= expanded,
                "ordering broken at width=$width: $compact/$balanced/$expanded"
            )
        }
    }

    @Test
    fun homeColumnsStayWithinSaneRange() {
        WorkspaceMode.values().forEach { mode ->
            widths.forEach { width ->
                val columns = mode.homeColumns(width)
                assertTrue(
                    columns in MinHomeColumns..MaxHomeColumns,
                    "$mode at width=$width yielded $columns"
                )
            }
        }
    }

    @Test
    fun homeColumnsClampNarrowFloorsAndWideCeilings() {
        assertEquals(MinHomeColumns, WorkspaceMode.COMPACT.homeColumns(0))
        assertEquals(MaxHomeColumns, WorkspaceMode.EXPANDED.homeColumns(3000))
        assertEquals(MaxHomeColumns, WorkspaceMode.EXPANDED.homeColumns(100000))
    }

    @Test
    fun homeColumnsNeverShrinkAsWidthGrows() {
        WorkspaceMode.values().forEach { mode ->
            widths.zipWithNext().forEach { (narrow, wide) ->
                assertTrue(
                    mode.homeColumns(narrow) <= mode.homeColumns(wide),
                    "$mode shrank from width=$narrow to $wide"
                )
            }
        }
    }

    @Test
    fun homeColumnsNoArgMatchesNarrowestWidth() {
        WorkspaceMode.values().forEach { mode ->
            assertEquals(mode.homeColumns(0), mode.homeColumns())
        }
    }

    @Test
    fun gridDensityIsMonotonicIncreasing() {
        val compact = WorkspaceMode.COMPACT.gridDensity()
        val balanced = WorkspaceMode.BALANCED.gridDensity()
        val expanded = WorkspaceMode.EXPANDED.gridDensity()
        assertTrue(compact < balanced && balanced < expanded, "density not monotonic")
        assertEquals(1.0f, balanced)
    }
}
