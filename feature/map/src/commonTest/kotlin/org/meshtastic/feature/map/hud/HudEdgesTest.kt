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

import kotlin.math.PI
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals

private const val W = 1000f
private const val FOCAL = 800f

private fun pin(yaw: Double, pitch: Double = -10.0, previous: PinEdge? = null, projected: ScreenPoint? = null) =
    pinEdge(projected, W, yaw, pitch, previous)

class HudEdgesTest {

    @Test
    fun `a target just past the side threshold keeps its vertical edge inside the hysteresis band`() {
        assertEquals(PinEdge.BOTTOM, pin(yaw = 40.0))
        assertEquals(PinEdge.BOTTOM, pin(yaw = 40.0, previous = PinEdge.BOTTOM))
        assertEquals(PinEdge.TOP, pin(yaw = -40.0, pitch = 10.0, previous = PinEdge.TOP))
    }

    @Test
    fun `a target clearing the band to the side moves to that side`() {
        assertEquals(PinEdge.RIGHT, pin(yaw = 48.0, previous = PinEdge.BOTTOM))
        assertEquals(PinEdge.LEFT, pin(yaw = -48.0, previous = PinEdge.TOP))
    }

    @Test
    fun `a side pinned target holds its side until it clears the band toward ahead`() {
        assertEquals(PinEdge.RIGHT, pin(yaw = 30.0, previous = PinEdge.RIGHT))
        assertEquals(PinEdge.LEFT, pin(yaw = -24.0, previous = PinEdge.LEFT))
        assertEquals(PinEdge.BOTTOM, pin(yaw = 20.0, previous = PinEdge.RIGHT))
        assertEquals(PinEdge.TOP, pin(yaw = -20.0, pitch = 5.0, previous = PinEdge.LEFT))
    }

    @Test
    fun `a target behind pins to the side it is nearer`() {
        assertEquals(PinEdge.RIGHT, pin(yaw = 170.0))
        assertEquals(PinEdge.LEFT, pin(yaw = -170.0))
        assertEquals(PinEdge.RIGHT, pin(yaw = 120.0, pitch = 60.0))
    }

    @Test
    fun `a side pinned target near straight behind keeps its side as it crosses over`() {
        assertEquals(PinEdge.RIGHT, pin(yaw = -175.0, previous = PinEdge.RIGHT))
        assertEquals(PinEdge.LEFT, pin(yaw = 175.0, previous = PinEdge.LEFT))
    }

    @Test
    fun `a target past the behind window switches to its nearer side`() {
        assertEquals(PinEdge.LEFT, pin(yaw = -160.0, previous = PinEdge.RIGHT))
        assertEquals(PinEdge.RIGHT, pin(yaw = 160.0, previous = PinEdge.LEFT))
    }

    @Test
    fun `a target roughly ahead pins above or below by its pitch`() {
        assertEquals(PinEdge.TOP, pin(yaw = 10.0, pitch = 30.0))
        assertEquals(PinEdge.BOTTOM, pin(yaw = 10.0, pitch = -30.0))
        assertEquals(PinEdge.BOTTOM, pin(yaw = 0.0, pitch = 0.0))
    }

    @Test
    fun `a target projected within the view width pins vertically whatever its yaw`() {
        assertEquals(PinEdge.TOP, pin(yaw = 100.0, pitch = 70.0, projected = ScreenPoint(500f, -50f)))
        assertEquals(PinEdge.BOTTOM, pin(yaw = -100.0, previous = PinEdge.LEFT, projected = ScreenPoint(0f, 3000f)))
    }

    @Test
    fun `a target projected beyond the view width pins by yaw`() {
        assertEquals(PinEdge.RIGHT, pin(yaw = 60.0, projected = ScreenPoint(W + 1f, 100f)))
    }

    @Test
    fun `top and bottom offsets follow yaw`() {
        assertEquals(FOCAL, edgeOffsetPx(PinEdge.TOP, 45.0, 30.0, FOCAL), 0.01f)
        assertEquals(-FOCAL, edgeOffsetPx(PinEdge.BOTTOM, -45.0, 30.0, FOCAL), 0.01f)
    }

    @Test
    fun `side offsets follow pitch`() {
        assertEquals(FOCAL, edgeOffsetPx(PinEdge.LEFT, 120.0, 45.0, FOCAL), 0.01f)
        assertEquals(-FOCAL, edgeOffsetPx(PinEdge.RIGHT, 120.0, -45.0, FOCAL), 0.01f)
        assertEquals(0f, edgeOffsetPx(PinEdge.RIGHT, 120.0, 0.0, FOCAL), 0.01f)
    }

    @Test
    fun `offsets clamp at eighty degrees`() {
        val max = (tan(80.0 * PI / 180.0) * FOCAL).toFloat()
        assertEquals(max, edgeOffsetPx(PinEdge.TOP, 89.0, 0.0, FOCAL), 0.1f)
        assertEquals(-max, edgeOffsetPx(PinEdge.LEFT, 0.0, -170.0, FOCAL), 0.1f)
    }
}
