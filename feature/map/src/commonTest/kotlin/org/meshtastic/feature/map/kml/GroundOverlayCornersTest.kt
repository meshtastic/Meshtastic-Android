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
package org.meshtastic.feature.map.kml

import kotlin.test.Test
import kotlin.test.assertEquals

class GroundOverlayCornersTest {

    private fun overlay(rotation: Double = 0.0) = KmlGroundOverlay(
        name = null,
        href = "a.png",
        north = 1.0,
        south = -1.0,
        east = 2.0,
        west = -2.0,
        rotationDegrees = rotation,
    )

    @Test
    fun `no rotation is the plain box`() {
        val corners = overlay().corners()

        assertEquals(OverlayCorner(-2.0, 1.0), corners.topLeft)
        assertEquals(OverlayCorner(2.0, 1.0), corners.topRight)
        assertEquals(OverlayCorner(2.0, -1.0), corners.bottomRight)
        assertEquals(OverlayCorner(-2.0, -1.0), corners.bottomLeft)
    }

    @Test
    fun `ninety degrees counter-clockwise turns the top edge into the left edge`() {
        // Centred on the equator so the local scale is ~1 and the numbers stay readable.
        val corners = overlay(rotation = 90.0).corners()

        // The top-left corner (-2, 1) swings to (-1, -2): a CCW quarter turn about the origin.
        assertEquals(-1.0, corners.topLeft.longitude, absoluteTolerance = 1e-9)
        assertEquals(-2.0, corners.topLeft.latitude, absoluteTolerance = 1e-9)
        // And the opposite corner (2, -1) swings to (1, 2) — the same quarter turn.
        assertEquals(1.0, corners.bottomRight.longitude, absoluteTolerance = 1e-9)
        assertEquals(2.0, corners.bottomRight.latitude, absoluteTolerance = 1e-9)
    }

    @Test
    fun `rotation happens about the box centre rather than the origin`() {
        val shifted =
            KmlGroundOverlay(
                name = null,
                href = "a.png",
                north = 11.0,
                south = 9.0,
                east = 52.0,
                west = 48.0,
                rotationDegrees = 180.0,
            )

        val corners = shifted.corners()
        // A half turn swaps opposite corners; the centre (50, 10) stays put.
        assertEquals(52.0, corners.topLeft.longitude, absoluteTolerance = 1e-9)
        assertEquals(9.0, corners.topLeft.latitude, absoluteTolerance = 1e-9)
        assertEquals(48.0, corners.bottomRight.longitude, absoluteTolerance = 1e-9)
        assertEquals(11.0, corners.bottomRight.latitude, absoluteTolerance = 1e-9)
    }
}
