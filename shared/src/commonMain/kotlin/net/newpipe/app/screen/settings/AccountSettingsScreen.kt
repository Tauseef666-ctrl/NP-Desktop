/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.navigation.Navigator
import net.newpipe.app.preview.ThemePreviewProvider
import net.newpipe.app.service.YtSessionStore
import net.newpipe.app.service.ytSessionStore
import org.koin.compose.koinInject
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.account_not_found
import newpipe.shared.generated.resources.account_paste_hint
import newpipe.shared.generated.resources.account_save_session
import newpipe.shared.generated.resources.account_session_description
import newpipe.shared.generated.resources.account_session_title
import newpipe.shared.generated.resources.account_sign_out
import newpipe.shared.generated.resources.account_status_active
import newpipe.shared.generated.resources.account_status_none
import newpipe.shared.generated.resources.account_stored
import newpipe.shared.generated.resources.account_storage_note
import newpipe.shared.generated.resources.settings_category_account_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountSettingsScreen(
    navigator: Navigator = koinInject(),
    store: YtSessionStore = remember { ytSessionStore() }
) {
    AccountSettingsScreenContent(
        store = store,
        onNavigateUp = { navigator.navigateUp() }
    )
}

@Composable
fun AccountSettingsScreenContent(
    store: YtSessionStore = remember { ytSessionStore() },
    onNavigateUp: () -> Unit = {}
) {
    var paste by rememberSaveable { mutableStateOf("") }
    var sessionActive by rememberSaveable { mutableStateOf(store.sessionExists()) }
    var resultText by rememberSaveable { mutableStateOf("") }

    val notFoundText = stringResource(Res.string.account_not_found)
    val storedTemplate = stringResource(Res.string.account_stored)

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_category_account_title),
                onNavigateUp = onNavigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 16.dp)
                .fillMaxHeight()
        ) {
            Text(
                text = stringResource(Res.string.account_session_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.account_session_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    if (sessionActive) {
                        Res.string.account_status_active
                    } else {
                        Res.string.account_status_none
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (sessionActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 8.dp)
            )
            if (resultText.isNotBlank()) {
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = paste,
                onValueChange = { paste = it },
                label = { Text(stringResource(Res.string.account_paste_hint)) },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Button(
                    onClick = {
                        val saved = store.saveSession(paste)
                        resultText = if (saved == 0) {
                            notFoundText
                        } else {
                            String.format(storedTemplate, saved)
                        }
                        sessionActive = store.sessionExists()
                        paste = ""
                    },
                    enabled = paste.isNotBlank()
                ) {
                    Text(stringResource(Res.string.account_save_session))
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    onClick = {
                        store.signOut()
                        sessionActive = false
                        resultText = ""
                    },
                    enabled = sessionActive
                ) {
                    Text(stringResource(Res.string.account_sign_out))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.account_storage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@PreviewLightDark
@Composable
private fun AccountSettingsScreenPreview() {
    AccountSettingsScreenContent()
}
