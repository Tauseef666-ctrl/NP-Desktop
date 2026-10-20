/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceNormal
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_arrow_back
import newpipe.shared.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource

private val ToolbarHeight = 56.dp
private val IconButtonSize = 40.dp
private val IconButtonShape = RoundedCornerShape(8.dp)

/**
 * Top toolbar for a screen: optional back button, title/subtitle and a trailing actions slot.
 * Falls back to the bundled back arrow when [onBack] is provided without a [navigationIcon].
 */
@Composable
fun TopToolbar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: Painter? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = plusColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ToolbarHeight)
            .background(colors.accent)
            .padding(horizontal = spaceNormal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val backIcon = navigationIcon ?: if (onBack != null) painterResource(Res.drawable.ic_arrow_back) else null
        if (backIcon != null && onBack != null) {
            ToolbarIconButton(
                painter = backIcon,
                contentDescription = "Back",
                onClick = onBack,
                iconTint = colors.onAccent
            )
            Spacer(Modifier.width(spaceNormal))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onAccent.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Square icon button with a hover/press surface, for toolbar and list actions. */
@Composable
fun ToolbarIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color = plusColors().textSecondary
) {
    val colors = plusColors()
    Box(
        modifier = modifier
            .size(IconButtonSize)
            .plusClickable(IconButtonShape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val icon: @Composable () -> Unit = {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        if (contentDescription != null) {
            PlusTooltip(text = contentDescription) { icon() }
        } else {
            icon()
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun TopToolbarPreview() {
    TopToolbar(
        title = "Downloads",
        subtitle = "3 active",
        onBack = {},
        actions = {
            ToolbarIconButton(
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = "Search",
                onClick = {}
            )
        }
    )
}
