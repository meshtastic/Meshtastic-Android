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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.User

@PreviewLightDark
@Composable
private fun ReactionItemPreview() {
    AppTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            ReactionItem(emoji = "\uD83D\uDE42")
            ReactionItem(emoji = "\uD83D\uDE42", emojiCount = 2)
            AddReactionButton()
        }
    }
}

@Preview
@Composable
private fun ReactionRowPreview() {
    AppTheme {
        ReactionRow(
            reactions =
            listOf(
                Reaction(
                    replyId = 1,
                    user = User(),
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L,
                    snr = -1.0f,
                    rssi = -99,
                    hopsAway = 1,
                ),
                Reaction(
                    replyId = 1,
                    user = User(),
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L,
                    snr = -1.0f,
                    rssi = -99,
                    hopsAway = 1,
                ),
            ),
        )
    }
}
