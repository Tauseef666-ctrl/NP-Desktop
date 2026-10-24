/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import net.newpipe.app.composable.ContentListItem
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.service.WatchHistoryRepository
import net.newpipe.app.ui.Spacing
import org.koin.compose.koinInject

/**
 * The watch history screen.
 */
@Composable
fun HistoryScreen(
    navigator: Navigator = koinInject(),
    history: WatchHistoryRepository = koinInject()
) {
    val items by history.items.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg)
        ) {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm)
            )

            if (items.isEmpty()) {
                Text(
                    text = "No watch history yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.lg)
                )
            } else {
                TextButton(onClick = { history.clear() }) {
                    Text("Clear history")
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(items, key = { it.url }) { item ->
                        ContentListItem(
                            item = item,
                            onClick = {
                                navigator.navigateTo(
                                    Destination.Stream(item.serviceId, item.url)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
