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

import org.meshtastic.core.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals

private fun nearby(bearing: Double, distance: Double) =
    NearbyTarget(HudSubject.NodeSubject(Node(num = 1)), 0.0, 0.0, bearing, distance, 0.0)

class HudRadarTest {

    @Test
    fun `the scale rounds up to one two or five`() {
        assertEquals(20.0, niceScaleM(13.0))
        assertEquals(50.0, niceScaleM(21.0))
        assertEquals(100.0, niceScaleM(51.0))
        assertEquals(10.0, niceScaleM(10.0))
    }

    @Test
    fun `a target straight ahead plots straight up`() {
        val at = radarOffset(nearby(bearing = 90.0, distance = 10.0), headingDeg = 90.0, scaleM = 20.0, radiusPx = 100f)
        assertEquals(0f, at.x, 0.01f)
        assertEquals(-50f, at.y, 0.01f)
    }

    @Test
    fun `a target to the right of the heading plots to the right`() {
        val at =
            radarOffset(nearby(bearing = 180.0, distance = 20.0), headingDeg = 90.0, scaleM = 20.0, radiusPx = 100f)
        assertEquals(100f, at.x, 0.01f)
        assertEquals(0f, at.y, 0.01f)
    }

    @Test
    fun `a target beyond the scale stays on the rim`() {
        val at = radarOffset(nearby(bearing = 0.0, distance = 500.0), headingDeg = 0.0, scaleM = 20.0, radiusPx = 100f)
        assertEquals(-100f, at.y, 0.01f)
    }

    @Test
    fun `a path point beyond the scale runs past the rim the same way`() {
        val at = radarPathOffset(bearingDeg = 180.0, distanceM = 500.0, headingDeg = 90.0, pxPerM = 1f)
        assertEquals(500f, at.x, 0.01f)
        assertEquals(0f, at.y, 0.01f)
    }
}
