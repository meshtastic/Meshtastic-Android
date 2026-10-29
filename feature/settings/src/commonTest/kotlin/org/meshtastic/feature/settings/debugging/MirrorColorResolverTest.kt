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
package org.meshtastic.feature.settings.debugging

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class MirrorColorResolverTest {

    @Test
    fun `rgb565 expands by bit replication`() {
        assertEquals(Color(255, 255, 255), rgb565ToColor(0xFFFF))
        assertEquals(Color(0, 0, 0), rgb565ToColor(0x0000))
        assertEquals(Color(255, 0, 0), rgb565ToColor(0xF800))
        assertEquals(Color(0, 255, 0), rgb565ToColor(0x07E0))
        assertEquals(Color(0, 0, 255), rgb565ToColor(0x001F))
        // 5-bit 16 replicates to 10000100b = 132, where plain scaling floors to 131
        assertEquals(132, (rgb565ToColor(16 shl 11).red * 255).toInt())
    }

    @Test
    fun `highest-index region wins where regions overlap`() {
        val under = ResolvedRegion(left = 0, top = 0, right = 100, bottom = 10, on = Color.Red, off = Color.Black)
        val over = ResolvedRegion(left = 50, top = 0, right = 100, bottom = 10, on = Color.Green, off = Color.Blue)
        val rows = listOf(under, over)

        assertEquals(
            Color.Red,
            resolvePixelColor(x = 10, set = true, rowRegions = rows, defaultOn = Color.White, defaultOff = Color.Black),
        )
        assertEquals(
            Color.Green,
            resolvePixelColor(x = 60, set = true, rowRegions = rows, defaultOn = Color.White, defaultOff = Color.Black),
        )
        assertEquals(
            Color.Blue,
            resolvePixelColor(
                x = 60,
                set = false,
                rowRegions = rows,
                defaultOn = Color.White,
                defaultOff = Color.Black,
            ),
        )
    }

    @Test
    fun `pixels outside all regions use the defaults`() {
        val region = ResolvedRegion(left = 0, top = 0, right = 10, bottom = 10, on = Color.Red, off = Color.Black)

        assertEquals(
            Color.White,
            resolvePixelColor(
                x = 50,
                set = true,
                rowRegions = listOf(region),
                defaultOn = Color.White,
                defaultOff = Color.Gray,
            ),
        )
        assertEquals(
            Color.Gray,
            resolvePixelColor(
                x = 50,
                set = false,
                rowRegions = listOf(region),
                defaultOn = Color.White,
                defaultOff = Color.Gray,
            ),
        )
    }
}
