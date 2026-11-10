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
package org.meshtastic.feature.coverage

import org.meshtastic.feature.map.terrain.GeoBounds
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionTerrainTest {

    // Fiji, west 177 to east -178: across the antimeridian.
    private val acrossTheAntimeridian = GeoBounds(south = -19.0, west = 177.0, north = -16.0, east = -178.0)

    @Test
    fun aWrappedRegionMeetsGroundOnBothSidesOfTheLine() {
        assertTrue(acrossTheAntimeridian.intersects(GeoBounds(-18.0, 178.0, -17.0, 179.0)))
        assertTrue(acrossTheAntimeridian.intersects(GeoBounds(-18.0, -179.5, -17.0, -179.0)))
    }

    @Test
    fun aWrappedRegionMissesGroundBetweenItsEnds() {
        assertFalse(acrossTheAntimeridian.intersects(GeoBounds(-18.0, 0.0, -17.0, 1.0)))
    }

    @Test
    fun boxesThatOnlyTouchDoNotIntersect() {
        assertFalse(GeoBounds(0.0, 0.0, 1.0, 1.0).intersects(GeoBounds(0.0, 1.0, 1.0, 2.0)))
    }
}
