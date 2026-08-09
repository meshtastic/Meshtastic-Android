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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp

/**
 * Full-width band of an event edition's brand [palette], left to right in authored order.
 *
 * The ambient accent wash can only ever carry one color, and for editions whose primary is very dark (DEF CON's navy)
 * it is nearly invisible against a dark surface — this strip is where the rest of the palette actually shows up. Used
 * as a hairline rule under [MainAppBar] and as the closing edge of the [EventInfoSheet] header.
 *
 * Draws nothing for an empty palette, and a flat band rather than a gradient when the edition publishes a single color.
 */
@Composable
fun EventPaletteStrip(palette: List<Color>, height: Dp, modifier: Modifier = Modifier) {
    if (palette.isEmpty()) return
    val brush =
        remember(palette) {
            if (palette.size >= MIN_GRADIENT_STOPS) Brush.horizontalGradient(palette) else SolidColor(palette.first())
        }
    Box(modifier.fillMaxWidth().height(height).background(brush))
}

/** A gradient needs two stops; a single-color palette is drawn as a flat band instead. */
private const val MIN_GRADIENT_STOPS = 2
