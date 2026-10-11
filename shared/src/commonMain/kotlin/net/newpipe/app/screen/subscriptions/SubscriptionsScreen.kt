/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.subscriptions

import androidx.compose.foundation.layout.Column
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
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.subscription.Subscription
import net.newpipe.app.theme.plusColors
import net.newpipe.app.theme.spaceLarge
import net.newpipe.app.theme.spaceNormal
import net.newpipe.app.theme.spaceSmall
import net.newpipe.app.theme.spaceXXSmall
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
 * Local subscriptions manager (Phase 4). No streaming provider is bundled yet, so this only manages
 * the user's hand-entered channel list; it never shows fabricated videos.
 */
@Composable
fun SubscriptionsScreen(
    navigator: Navigator = koinInject(),
    viewModel: SubscriptionsViewModel = koinViewModel()
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()

    SubscriptionsScreenContent(
        subscriptions = subscriptions,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun SubscriptionsScreenContent(
    subscriptions: List<Subscription> = emptyList(),
    onAdd: (String, String) -> AddResult = { _, _ -> AddResult.Added },
    onRemove: (String) -> Unit = {},
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
            Text(
                text = stringResource(Res.string.subscriptions_provider_pending),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = spaceLarge, vertical = spaceSmall)
            )
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
