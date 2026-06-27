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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Constant near-black backing for status-colored content. The status tokens (StatusGreen/Yellow/Orange/Red) are bright
 * indicator colors that only clear WCAG AA against a near-black or near-white surface — never a mid-tone. A fixed dark
 * scrim lets them keep their true values and stay legible on ANY surface (message bubbles, map tiles, colored cards).
 *
 * ponytail: must be opaque — a translucent scrim composites with whatever is behind it and can't guarantee the ratio.
 */
val StatusScrim = Color(0xFF101113)

/**
 * Wraps status-colored [content] (e.g. [Snr]/[Rssi], a signed shield) on a [StatusScrim] chip so the colors stay
 * AA-legible regardless of the surface underneath. Render the status content in its true token color inside.
 */
@Composable
fun StatusSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.clip(shape).background(StatusScrim).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}
