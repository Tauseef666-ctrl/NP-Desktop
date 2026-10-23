/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.model.ContentItem
import net.newpipe.app.model.ContentType
import net.newpipe.app.navigation.Destination
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.screen.search.Message
import net.newpipe.app.ui.Radius
import net.newpipe.app.ui.Spacing
import net.newpipe.app.util.formatDuration
import net.newpipe.app.util.formatViewCount
import net.newpipe.app.viewmodel.home.HomeViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The home screen: the predefined content lists ("kiosks") of a service in a responsive grid.
 *
 * The grid adapts its column count to the available width, so the same screen works on a small
 * window and on an ultrawide monitor.
 */
@Composable
fun HomeScreen(
    navigator: Navigator = koinInject(),
    viewModel: HomeViewModel = koinViewModel()
) {
    val services by viewModel.services.collectAsState()
    val serviceId by viewModel.serviceId.collectAsState()
    val kiosks by viewModel.kiosks.collectAsState()
    val selectedKiosk by viewModel.selectedKiosk.collectAsState()
    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = "Home")
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                ServiceSelector(
                    serviceName = services.firstOrNull { it.id == serviceId }?.name ?: "Service",
                    serviceNames = services.map { it.name },
                    onSelect = { name ->
                        services.firstOrNull { it.name == name }?.let { viewModel.selectService(it.id) }
                    }
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    kiosks.forEach { kiosk ->
                        FilterChip(
                            selected = kiosk.id == selectedKiosk,
                            onClick = { viewModel.selectKiosk(kiosk.id) },
                            label = { Text(kiosk.name) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = Spacing.sm))

            when {
                error != null -> Message(
                    text = error!!,
                    modifier = Modifier.fillMaxSize()
                )

                items.isEmpty() && loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                items.isEmpty() -> Message(
                    text = "Nothing to show here right now",
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    items(items, key = { it.url }) { item ->
                        ContentCard(
                            item = item,
                            onClick = { navigateToItem(item, navigator) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single entry of the grid: a thumbnail with the title and the metadata below it.
 */
@Composable
private fun ContentCard(
    item: ContentItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column {
            val thumbnailUrl = item.thumbnailUrl
            if (thumbnailUrl != null) {
                Box {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        contentScale = ContentScale.Crop
                    )

                    val duration = item.durationSeconds
                    if (duration != null && duration > 0 && !item.liveNow) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Spacing.sm),
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(Radius.sm)
                        ) {
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val metadata = buildMetadata(item)
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }
        }
    }
}

private fun buildMetadata(item: ContentItem): String {
    val parts = mutableListOf<String>()
    item.uploader?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    item.viewCount?.let { parts.add("${formatViewCount(it)} views") }
    if (item.liveNow) parts.add("LIVE")
    return parts.joinToString(" · ")
}

/**
 * Navigates to the screen that matches the type of the item.
 */
private fun navigateToItem(item: ContentItem, navigator: Navigator) {
    when (item.type) {
        ContentType.STREAM -> navigator.navigateTo(Destination.Stream(item.serviceId, item.url))
        ContentType.CHANNEL -> navigator.navigateTo(Destination.Channel(item.serviceId, item.url))
        ContentType.PLAYLIST -> navigator.navigateTo(Destination.Playlist(item.serviceId, item.url))
    }
}

/**
 * A dropdown to select the service whose content is shown.
 */
@Composable
private fun ServiceSelector(
    serviceName: String,
    serviceNames: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(text = serviceName)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            serviceNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(name)
                    }
                )
            }
        }
    }
}
