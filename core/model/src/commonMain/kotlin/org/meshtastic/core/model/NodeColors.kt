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

/** Derives a unique color pair from a node number. Returns (foreground, background) as @ColorInt. */
fun nodeColorsFromNum(nodeNum: Int): Pair<Int, Int> {
    val r = (nodeNum and 0xFF0000) shr 16
    val g = (nodeNum and 0x00FF00) shr 8
    val b = nodeNum and 0x0000FF
    val brightness = ((r * RED_WEIGHT) + (g * GREEN_WEIGHT) + (b * BLUE_WEIGHT)) / MAX_CHANNEL
    val foreground = if (brightness > BRIGHTNESS_THRESHOLD) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    val background = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    return foreground to background
}
