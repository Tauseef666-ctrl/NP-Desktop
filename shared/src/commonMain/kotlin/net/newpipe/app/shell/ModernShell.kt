/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.newpipe.app.navigation.Destination
import net.newpipe.app.ui.Breakpoints
import net.newpipe.app.ui.Layout
import net.newpipe.app.ui.Motion
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_home
import newpipe.shared.generated.resources.ic_language
import newpipe.shared.generated.resources.ic_search
import newpipe.shared.generated.resources.ic_settings_backup_restore
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * An entry of the shell navigation rail.
 *
 * Only destinations that are actually implemented are listed, so that the rail never contains a
 * button which does nothing.
 */
private data class ShellEntry(
    val destination: Destination,
    val label: String,
    val icon: DrawableResource
)

private val shellEntries = listOf(
    ShellEntry(Destination.Home, "Home", Res.drawable.ic_home),
    ShellEntry(Destination.Search, "Search", Res.drawable.ic_search),
    ShellEntry(Destination.Settings, "Settings", Res.drawable.ic_settings_backup_restore),
    ShellEntry(Destination.About, "About", Res.drawable.ic_language)
)

/**
 * The desktop application shell: a collapsible navigation rail on the left and the screen content
 * on the right.
 *
 * The rail starts expanded on wide windows and collapsed on narrow ones. The user can toggle it at
 * any time; the manual choice is kept while the application runs. Below [Breakpoints.compact] the
 * rail is always collapsed so that the content keeps as much room as possible.
 *
 * @param currentDestination the destination currently shown, used to highlight the rail entry
 * @param onNavigate invoked when a rail entry is activated
 * @param content the screen content
 */
@Composable
fun ModernShell(
    currentDestination: Destination,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxWidth < Breakpoints.compact
        val expandedByDefault = maxWidth >= Breakpoints.medium
        var userExpanded by rememberSaveable { mutableStateOf<Boolean?>(null) }
        val expanded = !compact && (userExpanded ?: expandedByDefault)

        val railWidth by animateDpAsState(
            targetValue = if (expanded) Layout.navRailExpanded else Layout.navRailCollapsed,
            animationSpec = tween(durationMillis = Motion.medium),
            label = "navigation-rail-width"
        )

        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                expanded = expanded,
                width = railWidth,
                currentDestination = currentDestination,
                onNavigate = onNavigate,
                onToggleExpanded = { userExpanded = !expanded }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                content()
            }
        }
    }
}

@Composable
private fun NavigationRail(
    expanded: Boolean,
    width: androidx.compose.ui.unit.Dp,
    currentDestination: Destination,
    onNavigate: (Destination) -> Unit,
    onToggleExpanded: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.sm, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Layout.toolbarHeight)
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.primary)
                )

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandHorizontally(animationSpec = tween(Motion.medium)),
                    exit = shrinkHorizontally(animationSpec = tween(Motion.medium))
                ) {
                    Text(
                        text = "NewPipe",
                        modifier = Modifier.padding(start = Spacing.md),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            shellEntries.forEach { entry ->
                NavigationRailItem(
                    entry = entry,
                    expanded = expanded,
                    selected = entry.destination == currentDestination,
                    onClick = { onNavigate(entry.destination) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onToggleExpanded,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = if (expanded) "\u00AB" else "\u00BB",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NavigationRailItem(
    entry: ShellEntry,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val background by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            hovered -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = Motion.fast),
        label = "navigation-item-background"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = Motion.fast),
        label = "navigation-item-content"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Layout.navigationItemHeight)
            .clip(RoundedCornerShape(Radius.md))
            .background(background)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(entry.icon),
            contentDescription = entry.label,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(animationSpec = tween(Motion.medium)),
            exit = shrinkHorizontally(animationSpec = tween(Motion.medium))
        ) {
            Text(
                text = entry.label,
                modifier = Modifier.padding(start = Spacing.md),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
