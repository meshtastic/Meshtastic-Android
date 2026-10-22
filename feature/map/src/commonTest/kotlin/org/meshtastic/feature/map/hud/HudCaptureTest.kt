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
package org.meshtastic.feature.map.hud

import kotlin.test.Test
import kotlin.test.assertEquals

private const val TOLERANCE = 1e-3f

private fun FloatArray.map(x: Float, y: Float) =
    (this[0] * x + this[1] * y + this[2]) to (this[3] * x + this[4] * y + this[5])

private fun assertMaps(matrix: FloatArray, view: Pair<Float, Float>, buffer: Pair<Float, Float>) {
    val (x, y) = matrix.map(view.first, view.second)
    assertEquals(buffer.first, x, TOLERANCE)
    assertEquals(buffer.second, y, TOLERANCE)
}

class HudCaptureTest {

    @Test
    fun `an unrotated crop of the view aspect maps corner to corner`() {
        val matrix = viewToBufferMatrix(1000f, 750f, 100f, 50f, 4100f, 3050f, rotationDegrees = 0)
        assertMaps(matrix, 0f to 0f, 100f to 50f)
        assertMaps(matrix, 1000f to 750f, 4100f to 3050f)
    }

    @Test
    fun `a portrait view over a landscape buffer shows the middle of the upright frame`() {
        // Upright the 4000 by 3000 buffer is 3000 wide and 4000 tall; a 1000 by 2000 view fills its height and shows
        // the middle 2000 of its width, so the view's top left is 500 in from the upright left edge.
        val matrix = viewToBufferMatrix(1000f, 2000f, 0f, 0f, 4000f, 3000f, rotationDegrees = 90)
        // Turned clockwise, the buffer's bottom left becomes the upright top left.
        assertMaps(matrix, 0f to 0f, 0f to 2500f)
        assertMaps(matrix, 1000f to 2000f, 4000f to 500f)
    }

    @Test
    fun `the view center is the crop center at every rotation`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val matrix = viewToBufferMatrix(1080f, 2400f, 0f, 0f, 4000f, 3000f, rotation)
            assertMaps(matrix, 540f to 1200f, 2000f to 1500f)
        }
    }

    @Test
    fun `a half turn swaps the corners`() {
        val matrix = viewToBufferMatrix(400f, 300f, 0f, 0f, 400f, 300f, rotationDegrees = 180)
        assertMaps(matrix, 0f to 0f, 400f to 300f)
    }
}
