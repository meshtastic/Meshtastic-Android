/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Returns a [CornerBasedShape] for a message bubble based on its position in a sequence.
 *
 * @param cornerRadius The base corner radius for the bubble.
 * @param isSender Whether the message was sent by the local user.
 * @param hasSamePrev Whether the previous message in the list is from the same sender.
 * @param hasSameNext Whether the next message in the list is from the same sender.
 */
internal fun getMessageBubbleShape(
    cornerRadius: Dp,
    isSender: Boolean,
    hasSamePrev: Boolean = false,
    hasSameNext: Boolean = false,
): CornerBasedShape {
    val square = 0.dp
    val round = cornerRadius

    return if (isSender) {
        // Sent messages are on the right.
        RoundedCornerShape(
            topStart = round,
            topEnd = if (hasSamePrev) square else round,
            bottomStart = round,
            bottomEnd = square,
        )
    } else {
        // Received messages are on the left.
        RoundedCornerShape(
            topStart = square,
            topEnd = round,
            bottomStart = if (hasSameNext) square else round,
            bottomEnd = round,
        )
    }
}
