/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.newpipe.app.platform.LocalWorkspaceMode
import net.newpipe.app.platform.isCompactSidebar
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_arrow_back
import newpipe.shared.generated.resources.ic_history
import newpipe.shared.generated.resources.ic_search
import newpipe.shared.generated.resources.sidebar_collapse
import newpipe.shared.generated.resources.sidebar_expand
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val NavRailShape = RoundedCornerShape(8.dp)
private val NavItemHeight = 44.dp
private val NavIconSize = 22.dp
private val NavRailExpandedWidth = 220.dp
private val NavRailCollapsedWidth = 72.dp

/** A single sidebar destination. */
data class NavEntry(
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
    val icon: Painter? = null,
    val badge: String? = null
)

/**
 * Desktop navigation sidebar. Collapses to an icon-only rail; entries without an icon fall back
 * to their initial when collapsed.
 *
 * The default [expanded] value is derived from [LocalWorkspaceMode]: [WorkspaceMode.COMPACT]
 * renders icon-only while every other mode shows labels.
 *
 * @param expanded When true shows labels; when false only icons/initials.
 * @param onToggleExpanded Optional collapse/expand affordance rendered above the footer. The label
 *   follows the current state ("Collapse sidebar" when expanded, "Expand sidebar" when collapsed).
 * @param header Optional slot pinned above the entries (logo, account, ...).
 * @param entries Destinations, in order.
 * @param footer Optional slot pinned to the bottom (settings, version, ...).
 */
@Composable
fun NavRail(
    entries: List<NavEntry>,
    modifier: Modifier = Modifier,
    expanded: Boolean = !LocalWorkspaceMode.current.isCompactSidebar,
    onToggleExpanded: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    val colors = plusColors()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(if (expanded) NavRailExpandedWidth else NavRailCollapsedWidth)
            .background(colors.surface)
    ) {
        if (header != null) {
            Box(Modifier.fillMaxWidth()) { header() }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spaceSmall, vertical = spaceSmall),
            verticalArrangement = Arrangement.spacedBy(spaceSmall)
        ) {
            entries.forEach { entry -> NavRailItem(entry = entry, expanded = expanded) }
        }
        if (onToggleExpanded != null) {
            NavRailToggle(expanded = expanded, onToggle = onToggleExpanded)
        }
        if (footer != null) {
            Box(Modifier.fillMaxWidth().padding(spaceSmall)) { footer() }
        }
    }
}

@Composable
private fun NavRailToggle(expanded: Boolean, onToggle: () -> Unit) {
    val colors = plusColors()
    val label = stringResource(
        if (expanded) Res.string.sidebar_collapse else Res.string.sidebar_expand
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spaceSmall, vertical = spaceSmall)
            .height(NavItemHeight)
            .clip(NavRailShape)
            .plusClickable(NavRailShape, onClick = onToggle)
            .padding(horizontal = spaceNormal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(NavIconSize), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = label,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(NavIconSize)
                    .rotate(if (expanded) 0f else 180f)
            )
        }
        if (expanded) {
            Spacer(Modifier.width(spaceNormal))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavRailItem(entry: NavEntry, expanded: Boolean) {
    val colors = plusColors()
    val contentColor = if (entry.selected) colors.accent else colors.textSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NavItemHeight)
            .clip(NavRailShape)
            .background(if (entry.selected) colors.accentMuted else Color.Transparent)
            .plusClickable(NavRailShape, onClick = entry.onClick)
            .padding(horizontal = spaceNormal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(NavIconSize), contentAlignment = Alignment.Center) {
            if (entry.icon != null) {
                Icon(
                    painter = entry.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(NavIconSize)
                )
            } else if (!expanded) {
                Text(
                    text = entry.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.width(spaceNormal))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (entry.selected) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (entry.badge != null) {
                Text(
                    text = entry.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accent)
                        .padding(horizontal = spaceSmall, vertical = 1.dp)
                )
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun NavRailPreview() {
    NavRail(
        entries = listOf(
            NavEntry("Home", selected = true, icon = painterResource(Res.drawable.ic_search)),
            NavEntry("History", icon = painterResource(Res.drawable.ic_history)),
            NavEntry("Library", badge = "3")
        )
    )
}
