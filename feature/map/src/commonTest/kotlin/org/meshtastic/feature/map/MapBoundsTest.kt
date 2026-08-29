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
package org.meshtastic.feature.map

import org.meshtastic.core.model.Node
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapBoundsTest {

    private fun node(num: Int, latitude: Double, longitude: Double) = Node(
        num = num,
        position = Position(latitude_i = (latitude * 1e7).toInt(), longitude_i = (longitude * 1e7).toInt()),
    )

    @Test
    fun `no points yields no box`() {
        assertNull(MapBounds.around(emptyList()))
    }

    @Test
    fun `a node with no fix is not a location`() {
        // Treating "no data yet" as (0, 0) is how the OSMdroid map opened in the Atlantic, and how the Google map's
        // initial fit was dragged there by a single positionless node.
        assertNull(MapBounds.aroundNodes(listOf(node(1, 0.0, 0.0))))
    }

    @Test
    fun `a box covers every point given`() {
        val bounds =
            assertNotNull(
                MapBounds.around(listOf(MapPoint(45.0, -122.0), MapPoint(46.0, -120.0), MapPoint(44.5, -123.5))),
            )
        assertEquals(44.5, bounds.south)
        assertEquals(46.0, bounds.north)
        assertEquals(-123.5, bounds.west)
        assertEquals(-120.0, bounds.east)
    }

    @Test
    fun `a single point is padded into something the camera can fit`() {
        val bounds = assertNotNull(MapBounds.around(listOf(MapPoint(45.0, -122.0))))
        assertTrue(bounds.north > bounds.south, "a zero-height box cannot be fitted to")
        assertTrue(bounds.east > bounds.west, "a zero-width box cannot be fitted to")
    }

    @Test
    fun `points stacked on one spot are padded the same way`() {
        // A stationary node's whole position track is exactly this.
        val stacked = List(5) { MapPoint(45.0, -122.0) }
        val bounds = assertNotNull(MapBounds.around(stacked))
        assertTrue(bounds.north > bounds.south)
        assertTrue(bounds.east > bounds.west)
    }
}
