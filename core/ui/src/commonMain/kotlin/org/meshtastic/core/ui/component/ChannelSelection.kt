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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Channel
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ChannelSettings

/** A [ChannelItem] that is one checkbox: tapping anywhere on the row toggles it. */
@Composable
fun ChannelSelection(
    index: Int,
    title: String,
    enabled: Boolean,
    isSelected: Boolean,
    onSelected: (Boolean) -> Unit,
    channel: Channel,
    supportingContent: (@Composable () -> Unit)? = null,
) = ChannelItem(
    index = index,
    title = title,
    enabled = enabled,
    selection = ChannelItemSelection(selected = isSelected, onSelectedChange = onSelected),
    supportingContent = supportingContent,
) {
    SecurityIcon(channel)
    Spacer(modifier = Modifier.width(10.dp))
    // The row is the toggle, so the checkbox only displays state; its interactive-size box keeps the row's layout.
    Checkbox(
        enabled = enabled,
        checked = isSelected,
        onCheckedChange = null,
        modifier = Modifier.minimumInteractiveComponentSize(),
    )
}

private data class PreviewRow(val title: String, val enabled: Boolean, val selected: Boolean, val key: Int)

@Preview
@Composable
fun ChannelSelectionPreview() {
    val rows =
        listOf(
            PreviewRow("LongFast", enabled = false, selected = true, key = 1),
            PreviewRow("Team 2", enabled = true, selected = true, key = 32),
            PreviewRow("Hiking", enabled = true, selected = false, key = 32),
            PreviewRow("A channel name too long to fit on a single line of the row", true, true, key = 32),
        )
    AppTheme {
        Column {
            rows.forEachIndexed { index, row ->
                val settings =
                    ChannelSettings.Builder()
                        .also { wb ->
                            wb.name = row.title
                            wb.psk = ByteArray(row.key) { 1 }.toByteString()
                        }
                        .build()
                ChannelSelection(
                    index = index,
                    title = row.title,
                    enabled = row.enabled,
                    isSelected = row.selected,
                    onSelected = {},
                    channel = Channel(settings, Channel.default.loraConfig),
                )
            }
        }
    }
}
