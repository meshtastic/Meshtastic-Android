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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Dark backing for status-colored content. The status tokens (StatusGreen/Yellow/Orange/Red) are bright indicator
 * colors that only stay legible against a dark surface, so this scrim keeps them readable on any surface (message
 * bubbles, node cards) while letting them keep their true values.
 *
 * Slightly translucent so the surface tint shows through and it doesn't read as a harsh black box. The common cases
 * (green/yellow/orange) stay at AA text contrast even over a light card; the darkest token (red) holds the 3:1
 * graphical floor — see ColorContrastTest / StatusSurfaceTest.
 */
// Alpha is at the floor for the surfaces the chip actually sits on: over the lightest real backdrop (the card surface
// at max node tint) 0xCD is the limit before red drops under 3:1 / green under 4.5:1. One step lower (0xCC) fails AA —
// see StatusSurfaceTest.
val StatusScrim = Color(0xCD101113)

/**
 * Wraps status-colored [content] (e.g. [Snr]/[Rssi], a signed shield) on the [StatusScrim] so the colors stay legible
 * regardless of the surface underneath. [CircleShape] gives a pill for a row of content and a circle behind a solo
 * icon. Render the status content in its true token color inside.
 */
@Composable
fun StatusSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.clip(shape).background(StatusScrim).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}
