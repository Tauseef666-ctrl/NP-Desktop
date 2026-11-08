/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.ic_open_in_new
import org.jetbrains.compose.resources.painterResource
import net.newpipe.app.model.Link
import net.newpipe.app.preview.LinkPreviewProvider
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.theme.spaceMedium

/**
 * Composable to display information about links
 * @param link A link item with information
 * @param onAction Callback when the action button is clicked
 */
@Composable
fun LinkListItem(modifier: Modifier = Modifier, link: Link, onAction: () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(spaceMedium)
    ) {
        Text(
            text = link.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = link.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.End),
            contentPadding = PaddingValues(horizontal = spaceMedium, vertical = 0.dp),
            // Left-aligned under the description instead of a floating right-aligned label so
            // the action is clearly attached to the row it belongs to.
            onClick = onAction
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_open_in_new),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(text = link.action)
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun LinkListItemPreview(
    @PreviewParameter(LinkPreviewProvider::class) link: Link
) {
    LinkListItem(link = link)
}
