package net.newpipe.app.ui

import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

object Elevation {
    val none = 0.dp
    val sm = 2.dp
    val md = 4.dp
    val lg = 8.dp
}

object Layout {
    val navRailCollapsed = 72.dp
    val navRailExpanded = 240.dp
    val toolbarHeight = 56.dp
    val playerMinHeight = 200.dp
    val navigationItemHeight = 44.dp
    val contentMaxWidth = 1600.dp
}

/** Width thresholds at which the shell changes its layout. */
object Breakpoints {
    val compact = 900.dp
    val medium = 1200.dp
    val expanded = 1600.dp
}

/** Animation durations in milliseconds, matching the specification. */
object Motion {
    const val fast = 120
    const val medium = 220
    const val slow = 300
}

