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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.AppTheme

/**
 * A channel row: its index, [title] and the trailing [content], with optional [supportingContent] under them inside the
 * same card.
 *
 * The card is a button for [onClick] unless a [selection] is given. With a [selection] the card is one checkbox, so the
 * index badge is not a separate click target and [onClick] is unused.
 */
@Composable
fun ChannelItem(
    index: Int,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit = {},
    selection: ChannelItemSelection? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val fontColor = if (index == 0) MaterialTheme.colorScheme.primary else Color.Unspecified
    val rowAction =
        if (selection == null) {
            Modifier.clickable(enabled = enabled) { onClick() }
        } else {
            Modifier.toggleable(
                value = selection.selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = selection.onSelectedChange,
            )
        }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).then(rowAction)) {
        Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selection == null) {
                    AssistChip(
                        onClick = onClick,
                        enabled = enabled,
                        label = { Text(text = "$index", color = fontColor) },
                    )
                } else {
                    ChannelIndexBadge(index = index, color = fontColor)
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = fontColor,
                )
                content()
            }
            supportingContent?.let { Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) { it() } }
        }
    }
}

/**
 * Makes a [ChannelItem] a checkbox: the whole card toggles [selected] through [onSelectedChange], and the index badge
 * and everything in [ChannelItem]'s `supportingContent` are read as part of it.
 */
@Immutable class ChannelItemSelection(val selected: Boolean, val onSelectedChange: (Boolean) -> Unit)

/** The index chip without a click action, laid out like an [AssistChip] so a selectable row looks like the others. */
@Composable
private fun ChannelIndexBadge(index: Int, color: Color) {
    Surface(
        modifier = Modifier.minimumInteractiveComponentSize(),
        shape = AssistChipDefaults.shape,
        color = Color.Transparent,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.defaultMinSize(minHeight = AssistChipDefaults.Height).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$index",
                color = color.takeOrElse { MaterialTheme.colorScheme.onSurface },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview
@Composable
fun ChannelItemPreview() {
    AppTheme { ChannelItem(index = 0, title = "Medium Fast", enabled = true) {} }
}
