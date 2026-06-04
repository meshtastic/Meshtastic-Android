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
package org.meshtastic.core.model

private const val RED_WEIGHT = 0.299
private const val GREEN_WEIGHT = 0.587
private const val BLUE_WEIGHT = 0.114
private const val BRIGHTNESS_THRESHOLD = 0.5
private const val MAX_CHANNEL = 255
private const val RED_MASK = 0xFF0000
private const val GREEN_MASK = 0x00FF00
private const val BLUE_MASK = 0x0000FF
private const val ALPHA_MASK = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val ALPHA_SHIFT = 24
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/** Derives a unique color pair from a node number. Returns (foreground, background) as @ColorInt. */
fun nodeColorsFromNum(nodeNum: Int): Pair<Int, Int> {
    val r = (nodeNum and RED_MASK) shr RED_SHIFT
    val g = (nodeNum and GREEN_MASK) shr GREEN_SHIFT
    val b = nodeNum and BLUE_MASK
    val brightness = ((r * RED_WEIGHT) + (g * GREEN_WEIGHT) + (b * BLUE_WEIGHT)) / MAX_CHANNEL
    val foreground = if (brightness > BRIGHTNESS_THRESHOLD) BLACK else WHITE
    val background = (ALPHA_MASK shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
    return foreground to background
}
