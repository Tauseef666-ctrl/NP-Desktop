/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.platform

import androidx.compose.runtime.compositionLocalOf

/** Smallest number of home-grid columns any workspace mode may render. */
const val MinHomeColumns: Int = 2

/** Largest number of home-grid columns any workspace mode may render. */
const val MaxHomeColumns: Int = 6

/** Available-width threshold (dp) at which the home grid gains one column. */
private const val HomeColumnsStep1WidthDp = 700

/** Available-width threshold (dp) at which the home grid gains two columns. */
private const val HomeColumnsStep2WidthDp = 1100

/** Available-width threshold (dp) at which the home grid gains three columns before clamping. */
private const val HomeColumnsStep3WidthDp = 1700

/**
 * Whether this mode renders the desktop navigation sidebar as an icon-only rail.
 *
 * Only [WorkspaceMode.COMPACT] collapses the sidebar; [WorkspaceMode.BALANCED] and
 * [WorkspaceMode.EXPANDED] keep icon + label entries.
 */
val WorkspaceMode.isCompactSidebar: Boolean
    get() = this == WorkspaceMode.COMPACT

/**
 * Column count the home grid uses at the narrowest width, before responsive widening.
 *
 * Dictated by the mode so the ordering EXPANDED >= BALANCED >= COMPACT holds at every width:
 * COMPACT 2, BALANCED 3, EXPANDED 4.
 */
private val WorkspaceMode.baseHomeColumns: Int
    get() = when (this) {
        WorkspaceMode.COMPACT -> 2
        WorkspaceMode.BALANCED -> 3
        WorkspaceMode.EXPANDED -> 4
    }

/**
 * Number of home-grid columns for the given available width in dp.
 *
 * The mode sets the base count (COMPACT 2 / BALANCED 3 / EXPANDED 4) and the width adds one column
 * at [HomeColumnsStep1WidthDp], two at [HomeColumnsStep2WidthDp] and three at
 * [HomeColumnsStep3WidthDp]. The sum is clamped to [MinHomeColumns]..[MaxHomeColumns], so wider
 * windows always yield at least as many columns (EXPANDED >= BALANCED >= COMPACT) and no layout
 * ever over- or under-fills.
 */
fun WorkspaceMode.homeColumns(availableWidthDp: Int): Int {
    val widthBonus = when {
        availableWidthDp >= HomeColumnsStep3WidthDp -> 3
        availableWidthDp >= HomeColumnsStep2WidthDp -> 2
        availableWidthDp >= HomeColumnsStep1WidthDp -> 1
        else -> 0
    }
    return (baseHomeColumns + widthBonus).coerceIn(MinHomeColumns, MaxHomeColumns)
}

/**
 * [homeColumns] assuming a narrow window (no width widening): COMPACT 2, BALANCED 3, EXPANDED 4.
 */
fun WorkspaceMode.homeColumns(): Int = homeColumns(availableWidthDp = 0)

/**
 * Relative density of the home rails. Larger values pack more items per row; BALANCED is the 1.0
 * baseline and the scale is monotonic across modes: COMPACT 0.75, BALANCED 1.0, EXPANDED 1.35.
 */
fun WorkspaceMode.gridDensity(): Float = when (this) {
    WorkspaceMode.COMPACT -> 0.75f
    WorkspaceMode.BALANCED -> 1.0f
    WorkspaceMode.EXPANDED -> 1.35f
}

/**
 * Whether the navigation rail should render its expanded (icon + label) form right now.
 *
 * The user's explicit choice ([pinnedExpanded]) always wins. When it is not pinned and the rail is
 * collapsed, an active pointer [hovering] over the rail temporarily "peeks" it open, provided the
 * host allows it ([hoverPeekEnabled]) — the "dense nav-rail hover" behaviour. This is pure so the
 * policy is unit-testable independently of pointer plumbing.
 */
fun navRailExpanded(
    pinnedExpanded: Boolean,
    hovering: Boolean,
    hoverPeekEnabled: Boolean
): Boolean = pinnedExpanded || (hoverPeekEnabled && hovering)

/**
 * Active workspace mode exposed to the shared UI. Defaults to [WorkspaceMode.BALANCED] so previews,
 * tests and non-desktop hosts render without an explicit provider.
 */
val LocalWorkspaceMode = compositionLocalOf { WorkspaceMode.BALANCED }

/**
 * Requests a workspace-mode change from the shared UI. Hosts provide an implementation that
 * persists the mode and updates the backing state; the default is a no-op so pure UI tests and
 * previews stay side-effect free.
 */
val LocalWorkspaceModeChange = compositionLocalOf<(WorkspaceMode) -> Unit> { {} }
