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
package org.meshtastic.app.map.offline.terrain

import org.meshtastic.feature.map.terrain.ElevationTile
import kotlin.test.Test
import kotlin.test.assertEquals

class ElevationStitcherTest {

    // 2x2 center tile: (x=0,y=0)=1, (x=1,y=0)=2, (x=0,y=1)=3, (x=1,y=1)=4.
    private val center = ElevationTile(width = 2, height = 2, elevations = floatArrayOf(1f, 2f, 3f, 4f))

    @Test
    fun `the interior of the padded tile is exactly the center tile, offset by the margin`() {
        val padded = ElevationStitcher.buildPadded(center, neighbors = emptyMap())

        assertEquals(4, padded.width)
        assertEquals(4, padded.height)
        assertEquals(1f, padded.elevationAt(1, 1))
        assertEquals(2f, padded.elevationAt(2, 1))
        assertEquals(3f, padded.elevationAt(1, 2))
        assertEquals(4f, padded.elevationAt(2, 2))
    }

    @Test
    fun `a missing neighbor's padding clamps to the center tile's own edge, not a crash or zero`() {
        val padded = ElevationStitcher.buildPadded(center, neighbors = emptyMap())

        // Padded (0,0) is one pixel NW of the center tile's own (0,0); with no NW neighbor it should clamp to the
        // center tile's own corner value, same as ElevationTile.elevationAt's own out-of-bounds behavior.
        assertEquals(1f, padded.elevationAt(0, 0))
        // Padded bottom row, one pixel south of center's own bottom-left: clamps to center's own bottom row.
        assertEquals(3f, padded.elevationAt(1, 3))
    }

    @Test
    fun `a north neighbor's bottom row supplies the padded tile's top row`() {
        // North neighbor's bottom row (y=1): (0,1)=101, (1,1)=102 — top row is irrelevant filler.
        val north = ElevationTile(width = 2, height = 2, elevations = floatArrayOf(201f, 202f, 101f, 102f))

        val padded = ElevationStitcher.buildPadded(center, neighbors = mapOf((0 to -1) to north))

        assertEquals(101f, padded.elevationAt(1, 0))
        assertEquals(102f, padded.elevationAt(2, 0))
    }

    @Test
    fun `a west neighbor's rightmost column supplies the padded tile's left column`() {
        // West neighbor's right column (x=1): (1,0)=301, (1,1)=302 — left column is irrelevant filler.
        val west = ElevationTile(width = 2, height = 2, elevations = floatArrayOf(999f, 301f, 999f, 302f))

        val padded = ElevationStitcher.buildPadded(center, neighbors = mapOf((-1 to 0) to west))

        assertEquals(301f, padded.elevationAt(0, 1))
        assertEquals(302f, padded.elevationAt(0, 2))
    }

    @Test
    fun `a diagonal neighbor supplies exactly the padded tile's own corner`() {
        // NW neighbor's own bottom-right pixel (1,1)=401 is the only one that should ever be read for this corner.
        val northwest = ElevationTile(width = 2, height = 2, elevations = floatArrayOf(999f, 999f, 999f, 401f))

        val padded = ElevationStitcher.buildPadded(center, neighbors = mapOf((-1 to -1) to northwest))

        assertEquals(401f, padded.elevationAt(0, 0))
        // The other three corners have no neighbor supplied, so they still clamp to center's own corners.
        assertEquals(2f, padded.elevationAt(3, 0))
        assertEquals(3f, padded.elevationAt(0, 3))
        assertEquals(4f, padded.elevationAt(3, 3))
    }
}
