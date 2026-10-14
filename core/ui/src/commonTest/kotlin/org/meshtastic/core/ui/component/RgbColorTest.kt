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
import kotlin.test.Test
import kotlin.test.assertEquals

class RgbColorTest {
    @Test
    fun channelsRoundTripThroughColorForEveryValue() {
        for (v in 0..255) {
            assertEquals(Triple(v, 255 - v, v / 2), rgbToColor(v, 255 - v, v / 2).toRgbChannels())
        }
    }

    @Test
    fun zeroChannelsMapToBlackAndFullChannelsToWhite() {
        assertEquals(Color.Black, rgbToColor(0, 0, 0))
        assertEquals(Color.White, rgbToColor(255, 255, 255))
    }

    @Test
    fun outOfRangeChannelsAreClamped() {
        assertEquals(Triple(255, 0, 255), rgbToColor(300, -5, 255).toRgbChannels())
    }

    @Test
    fun presetsRoundTripAndEndWithWhite() {
        val presets = presetColors()
        assertEquals(Color.White, presets.last())
        presets.forEach { color ->
            val (r, g, b) = color.toRgbChannels()
            assertEquals(Triple(r, g, b), rgbToColor(r, g, b).toRgbChannels())
        }
    }
}
