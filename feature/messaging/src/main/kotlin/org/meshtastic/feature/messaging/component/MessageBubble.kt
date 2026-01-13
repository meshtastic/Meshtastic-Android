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

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun getMessageBubbleShape(
    cornerRadius: Dp,
    isSender: Boolean,
    hasSamePrev: Boolean = false,
    hasSameNext: Boolean = false,
): CornerBasedShape {
    val square = 0.dp
    val round = cornerRadius

    return if (isSender) {
        RoundedCornerShape(
            topStart = if (hasSamePrev) square else round,
            topEnd = if (hasSamePrev) square else round,
            bottomStart = if (hasSameNext) square else round,
            bottomEnd = square,
        )
    } else {
        RoundedCornerShape(
            topStart = if (hasSamePrev) square else round,
            topEnd = if (hasSamePrev) square else round,
            bottomStart = square,
            bottomEnd = if (hasSameNext) square else round,
        )
    }
}
