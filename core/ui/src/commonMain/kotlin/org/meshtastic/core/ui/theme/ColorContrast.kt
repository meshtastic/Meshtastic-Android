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
package org.meshtastic.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** WCAG AA contrast ratio for normal text. */
const val MIN_TEXT_CONTRAST: Float = 4.5f

/** WCAG contrast ratio between two opaque colors (1.0 = identical, 21.0 = black-on-white). */
fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    val hi = maxOf(l1, l2)
    val lo = minOf(l1, l2)
    return (hi + WCAG_OFFSET) / (lo + WCAG_OFFSET)
}

private const val WCAG_OFFSET = 0.05f
