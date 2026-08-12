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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.ui.theme.AppTheme

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun MessageActionsContentPreview() {
    AppTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            MessageActionsContent(
                // Text placeholders, not emoji: emoji glyphs are not host-deterministic, so the
                // golden guards the row layout (44dp targets, scrolling) rather than emoji pixels.
                quickEmojis = listOf("+1", "-1", "ha", "!!", "<3", "??"),
                onReply = {},
                onReact = {},
                onMoreReactions = {},
                onCopy = {},
                onSelect = {},
                onDelete = {},
                onStatus = {},
                status = MessageStatus.DELIVERED,
                timestamp = "Aug 11, 2026 10:15 AM",
            )
        }
    }
}
