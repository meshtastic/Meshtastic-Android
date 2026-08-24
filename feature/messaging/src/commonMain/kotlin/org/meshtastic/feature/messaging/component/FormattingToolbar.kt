/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.messaging.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.format_bold
import org.meshtastic.core.resources.format_code
import org.meshtastic.core.resources.format_italic
import org.meshtastic.core.resources.format_link
import org.meshtastic.core.resources.format_strikethrough
import org.meshtastic.core.resources.insert
import org.meshtastic.core.resources.insert_link_message
import org.meshtastic.core.resources.insert_link_title
import org.meshtastic.core.resources.insert_link_url_hint
import org.meshtastic.core.ui.component.InlineStyle
import org.meshtastic.core.ui.icon.Bold
import org.meshtastic.core.ui.icon.Code
import org.meshtastic.core.ui.icon.Italic
import org.meshtastic.core.ui.icon.Link
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Strikethrough
import org.meshtastic.feature.messaging.FormattingResult
import org.meshtastic.feature.messaging.insertDelimiters
import org.meshtastic.feature.messaging.isMarkdownLink
import org.meshtastic.feature.messaging.unwrapLink
import org.meshtastic.feature.messaging.wrapSelection
import org.meshtastic.feature.messaging.wrapSelectionWithLink

/**
 * A row of markdown formatting actions (bold, italic, strikethrough, code, link) that mutate [state] in place. A
 * collapsed cursor inserts an empty delimiter pair; a non-empty selection is wrapped or, if already wrapped, toggled
 * off. The link action opens a URL-entry dialog, or unwraps a selected `[label](url)` back to its label.
 */
@Composable
internal fun FormattingToolbar(state: TextFieldState, modifier: Modifier = Modifier) {
    var showLinkDialog by remember { mutableStateOf(false) }
    var pendingLinkRange by remember { mutableStateOf<TextRange?>(null) }

    fun applyEdit(mutate: (String, TextRange) -> FormattingResult?) {
        val text = state.text.toString()
        val result = mutate(text, state.selection) ?: return
        state.edit {
            replace(0, length, result.text)
            selection = result.selection
        }
    }

    fun onStyle(style: InlineStyle) = applyEdit { text, selection ->
        if (selection.collapsed) insertDelimiters(text, selection.min, style) else wrapSelection(text, selection, style)
    }

    fun onLink() {
        val text = state.text.toString()
        val selection = state.selection
        val selected = text.substring(selection.min, selection.max)
        if (!selection.collapsed && isMarkdownLink(selected)) {
            applyEdit { _, _ -> unwrapLink(text, selection) }
        } else {
            pendingLinkRange = selection
            showLinkDialog = true
        }
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        FormatButton(MeshtasticIcons.Bold, stringResource(Res.string.format_bold)) { onStyle(InlineStyle.Bold) }
        FormatButton(MeshtasticIcons.Italic, stringResource(Res.string.format_italic)) { onStyle(InlineStyle.Italic) }
        FormatButton(MeshtasticIcons.Strikethrough, stringResource(Res.string.format_strikethrough)) {
            onStyle(InlineStyle.Strikethrough)
        }
        FormatButton(MeshtasticIcons.Code, stringResource(Res.string.format_code)) { onStyle(InlineStyle.Code) }
        FormatButton(MeshtasticIcons.Link, stringResource(Res.string.format_link)) { onLink() }
    }

    if (showLinkDialog) {
        val range = pendingLinkRange
        LinkDialog(
            onDismiss = {
                showLinkDialog = false
                pendingLinkRange = null
            },
            onConfirm = { url ->
                if (range != null) applyEdit { text, _ -> wrapSelectionWithLink(text, range, url) }
                showLinkDialog = false
                pendingLinkRange = null
            },
        )
    }
}

@Composable
private fun FormatButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(imageVector = icon, contentDescription = label) }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.insert_link_title)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.insert_link_message)) },
                placeholder = { Text(stringResource(Res.string.insert_link_url_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url) }, enabled = url.isNotBlank()) {
                Text(stringResource(Res.string.insert))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
