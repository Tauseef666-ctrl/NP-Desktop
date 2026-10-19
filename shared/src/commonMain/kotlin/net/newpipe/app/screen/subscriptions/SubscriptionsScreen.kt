/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.newpipe.app.component.EmptyState
import net.newpipe.app.component.PlusDialog
import net.newpipe.app.component.PlusScaffold
import net.newpipe.app.component.TopToolbar
import net.newpipe.app.component.VideoCard
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.provider.ProviderContentItem
import net.newpipe.app.provider.ProviderContentKind
import net.newpipe.app.provider.SubscriptionFeed
import net.newpipe.app.subscription.Subscription
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.theme.spaceXXSmall
import net.newpipe.app.util.formatItemDetails
import net.newpipe.app.viewmodel.subscriptions.SubscriptionFeedViewModel
import net.newpipe.app.viewmodel.subscriptions.SubscriptionFeedViewModel.FeedState
import net.newpipe.app.viewmodel.subscriptions.SubscriptionsViewModel
import net.newpipe.app.viewmodel.subscriptions.SubscriptionsViewModel.AddResult
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.subscriptions_add
import newpipe.shared.generated.resources.subscriptions_add_title
import newpipe.shared.generated.resources.subscriptions_count
import newpipe.shared.generated.resources.subscriptions_duplicate
import newpipe.shared.generated.resources.subscriptions_empty_message
import newpipe.shared.generated.resources.subscriptions_empty_title
import newpipe.shared.generated.resources.subscriptions_invalid_url
import newpipe.shared.generated.resources.subscriptions_name_label
import newpipe.shared.generated.resources.subscriptions_provider_pending
import newpipe.shared.generated.resources.subscriptions_remove
import newpipe.shared.generated.resources.subscriptions_remove_confirm_message
import newpipe.shared.generated.resources.subscriptions_remove_confirm_title
import newpipe.shared.generated.resources.subscriptions_screen_title
import newpipe.shared.generated.resources.subscriptions_url_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Local subscriptions manager (Phase 4). It manages the user's channel list and, once a provider is
 * available, shows the newest videos of each subscribed channel. It never shows fabricated videos.
 */
@Composable
fun SubscriptionsScreen(
    navigator: Navigator = koinInject(),
    viewModel: SubscriptionsViewModel = koinViewModel(),
    feedViewModel: SubscriptionFeedViewModel = koinViewModel()
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val feedState by feedViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(subscriptions) {
        feedViewModel.load()
    }

    SubscriptionsScreenContent(
        subscriptions = subscriptions,
        feedState = feedState,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onPlayItem = feedViewModel::play,
        onPlayGroup = feedViewModel::playAll,
        onRetryFeed = { feedViewModel.load() },
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun SubscriptionsScreenContent(
    subscriptions: List<Subscription> = emptyList(),
    feedState: FeedState = FeedState.Idle,
    onAdd: (String, String) -> AddResult = { _, _ -> AddResult.Added },
    onRemove: (String) -> Unit = {},
    onPlayItem: (ProviderContentItem) -> Unit = {},
    onPlayGroup: (List<ProviderContentItem>) -> Unit = {},
    onRetryFeed: () -> Unit = {},
    onNavigateUp: () -> Unit = {}
) {
    val colors = plusColors()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<Subscription?>(null) }

    PlusScaffold(
        toolbar = {
            TopToolbar(
                title = stringResource(Res.string.subscriptions_screen_title),
                subtitle = if (subscriptions.isEmpty()) {
                    null
                } else {
                    stringResource(Res.string.subscriptions_count, subscriptions.size)
                },
                onBack = onNavigateUp,
                actions = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text(stringResource(Res.string.subscriptions_add))
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = spaceNormal)
        ) {
            FeedSection(
                feedState = feedState,
                onPlayItem = onPlayItem,
                onPlayGroup = onPlayGroup,
                onRetry = onRetryFeed
            )
            val feedHasContent = (feedState as? FeedState.Content)?.feed?.isEmpty == false
            if (!feedHasContent) {
                Text(
                    text = stringResource(Res.string.subscriptions_provider_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = spaceLarge, vertical = spaceSmall)
                )
            }
            if (subscriptions.isEmpty()) {
                EmptyState(
                    title = stringResource(Res.string.subscriptions_empty_title),
                    message = stringResource(Res.string.subscriptions_empty_message)
                )
            } else {
                subscriptions.forEach { subscription ->
                    SubscriptionEntry(
                        subscription = subscription,
                        onRequestRemove = { pendingRemove = subscription }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            onAdd = onAdd,
            onDismiss = { showAddDialog = false }
        )
    }

    pendingRemove?.let { subscription ->
        PlusDialog(
            title = stringResource(Res.string.subscriptions_remove_confirm_title),
            text = stringResource(Res.string.subscriptions_remove_confirm_message),
            confirmLabel = stringResource(Res.string.subscriptions_remove),
            onConfirm = {
                onRemove(subscription.id)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null },
            destructive = true
        )
    }
}

/**
 * Renders the provider-backed feed. It only shows content the provider actually returned: a loading
 * hint, an explicit unavailable state with a retry, or the per-channel groups. Channels that failed
 * to load are named so the user is not misled by their absence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedSection(
    feedState: FeedState,
    onPlayItem: (ProviderContentItem) -> Unit,
    onPlayGroup: (List<ProviderContentItem>) -> Unit,
    onRetry: () -> Unit
) {
    val colors = plusColors()
    when (feedState) {
        FeedState.Idle -> Unit
        FeedState.Loading -> Text(
            text = "Loading subscriptions feed…",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = spaceLarge, vertical = spaceSmall)
        )
        is FeedState.Unavailable -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spaceLarge, vertical = spaceSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Couldn't load the subscriptions feed",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.danger,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        is FeedState.Content -> FeedContent(
            feed = feedState.feed,
            onPlayItem = onPlayItem,
            onPlayGroup = onPlayGroup
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedContent(
    feed: SubscriptionFeed,
    onPlayItem: (ProviderContentItem) -> Unit,
    onPlayGroup: (List<ProviderContentItem>) -> Unit
) {
    val colors = plusColors()
    feed.groups.forEach { group ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spaceLarge, vertical = spaceSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.channelName,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onPlayGroup(group.items) }) { Text("Play all") }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spaceLarge),
            horizontalArrangement = Arrangement.spacedBy(spaceLarge),
            verticalArrangement = Arrangement.spacedBy(spaceLarge)
        ) {
            group.items.forEach { item ->
                VideoCard(
                    title = item.title,
                    subtitle = item.uploader,
                    thumbnailUrl = item.thumbnailUrl,
                    details = formatItemDetails(item.viewCount, item.textualUploadDate),
                    isLive = item.kind == ProviderContentKind.LIVE,
                    onClick = { onPlayItem(item) }
                )
            }
        }
    }
    if (feed.failedChannels.isNotEmpty()) {
        Text(
            text = "Couldn't load: ${feed.failedChannels.joinToString()}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.danger,
            modifier = Modifier.padding(horizontal = spaceLarge, vertical = spaceSmall)
        )
    }
}

@Composable
private fun SubscriptionEntry(
    subscription: Subscription,
    onRequestRemove: () -> Unit
) {
    val colors = plusColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spaceLarge, vertical = spaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subscription.name.ifBlank { subscription.url },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subscription.url,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onRequestRemove) {
            Text(stringResource(Res.string.subscriptions_remove))
        }
    }
}

@Composable
private fun AddSubscriptionDialog(
    onAdd: (String, String) -> AddResult,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<AddResult?>(null) }

    PlusDialog(
        title = stringResource(Res.string.subscriptions_add_title),
        confirmLabel = stringResource(Res.string.subscriptions_add),
        onConfirm = {
            when (onAdd(name, url)) {
                AddResult.Added -> onDismiss()
                AddResult.InvalidUrl -> error = AddResult.InvalidUrl
                AddResult.Duplicate -> error = AddResult.Duplicate
            }
        },
        onDismiss = onDismiss,
        content = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.subscriptions_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(Res.string.subscriptions_url_label)) },
                singleLine = true,
                isError = error == AddResult.InvalidUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spaceSmall)
            )
            when (error) {
                AddResult.InvalidUrl -> InlineError(stringResource(Res.string.subscriptions_invalid_url))
                AddResult.Duplicate -> InlineError(stringResource(Res.string.subscriptions_duplicate))
                AddResult.Added, null -> Unit
            }
        }
    )
}

@Composable
private fun InlineError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = plusColors().danger,
        modifier = Modifier.padding(top = spaceXXSmall)
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun SubscriptionsScreenPreview() {
    SubscriptionsScreenContent(
        subscriptions = listOf(
            Subscription(
                id = "https://youtube.com/@newpipe",
                name = "NewPipe",
                url = "https://youtube.com/@newpipe"
            )
        )
    )
}
