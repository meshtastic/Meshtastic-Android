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

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

const val RGB_CHANNEL_MAX = 255

fun rgbToColor(red: Int, green: Int, blue: Int): Color = Color(
    red = red.coerceIn(0, RGB_CHANNEL_MAX),
    green = green.coerceIn(0, RGB_CHANNEL_MAX),
    blue = blue.coerceIn(0, RGB_CHANNEL_MAX),
)

fun Color.toRgbChannels(): Triple<Int, Int, Int> = Triple(red.toChannel(), green.toChannel(), blue.toChannel())

private fun Float.toChannel(): Int = (this * RGB_CHANNEL_MAX).roundToInt().coerceIn(0, RGB_CHANNEL_MAX)
