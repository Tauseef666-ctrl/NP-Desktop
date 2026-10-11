/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.newpipe.app.component.SearchBarField
import net.newpipe.app.component.SectionHeader
import net.newpipe.app.composable.PreferenceCategoryTitle
import net.newpipe.app.composable.PreferenceRow
import net.newpipe.app.player.ShortcutReference
import net.newpipe.app.player.ShortcutRow
import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.settings_category_shortcuts_title
import newpipe.shared.generated.resources.settings_shortcuts_none_found
import newpipe.shared.generated.resources.settings_shortcuts_reserved_note
import newpipe.shared.generated.resources.settings_shortcuts_search_placeholder
import newpipe.shared.generated.resources.shortcuts_global_hint
import newpipe.shared.generated.resources.shortcuts_media_keys_hint
import newpipe.shared.generated.resources.shortcuts_scope_app
import newpipe.shared.generated.resources.shortcuts_scope_player
import org.jetbrains.compose.resources.stringResource

/**
 * F7 keyboard-shortcut reference: a searchable list of every default binding, rendered inside the
 * player-settings section. Holding the search query is the only state; the filtering logic lives
 * in the pure [ShortcutReference] object. Stateless content is in [ShortcutReferenceSectionContent].
 */
@Composable
fun ShortcutReferenceSection() {
    var query by remember { mutableStateOf("") }

    ShortcutReferenceSectionContent(
        query = query,
        onQueryChange = { query = it },
        rows = ShortcutReference.rows(query)
    )
}

@Composable
fun ShortcutReferenceSectionContent(
    query: String,
    onQueryChange: (String) -> Unit,
    rows: List<ShortcutRow>
) {
    Column {
        PreferenceCategoryTitle(stringResource(Res.string.settings_category_shortcuts_title))
        SearchBarField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(Res.string.settings_shortcuts_search_placeholder)
        )
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            SectionHeader(stringResource(Res.string.settings_shortcuts_none_found))
        } else {
            rows.forEach { row ->
                PreferenceRow(
                    title = "${row.keyLabel} — ${row.actionLabel}",
                    summary = "${stringResource(if (row.isAppWide) Res.string.shortcuts_scope_app else Res.string.shortcuts_scope_player)} · ${row.description}",
                    onClick = {}
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionHeader(stringResource(Res.string.shortcuts_global_hint), modifier = Modifier.padding(horizontal = 4.dp))
        SectionHeader(stringResource(Res.string.shortcuts_media_keys_hint), modifier = Modifier.padding(horizontal = 4.dp))
        SectionHeader(stringResource(Res.string.settings_shortcuts_reserved_note), modifier = Modifier.padding(horizontal = 4.dp))
    }
}