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
package org.meshtastic.feature.map.maplibre.geojson

import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.spatialk.geojson.LineString
import org.meshtastic.feature.map.terrain.ContourLine
import org.meshtastic.feature.map.terrain.ContourPoint
import org.meshtastic.feature.map.terrain.TerrainTileMath
import org.meshtastic.feature.map.terrain.TileIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContourFeaturesTest {

    private val tile = TileIndex(zoom = 10, x = 163, y = 353)

    @Test
    fun `a two-point line becomes one LineString feature`() {
        val line = ContourLine(elevationMeters = 250f, points = listOf(ContourPoint(0f, 0f), ContourPoint(1f, 1f)))

        val features = contourLinesToFeatures(tile, listOf(line), zoom = 12, metric = true)

        assertEquals(1, features.size)
        assertEquals(2, (features.single().geometry as LineString).coordinates.size)
    }

    @Test
    fun `a degenerate single-point line is dropped`() {
        val line = ContourLine(elevationMeters = 250f, points = listOf(ContourPoint(0.5f, 0.5f)))
        assertTrue(contourLinesToFeatures(tile, listOf(line), zoom = 12, metric = true).isEmpty())
    }

    @Test
    fun `tile-local points convert to the same lat lon TerrainTileMath's own inverse would produce`() {
        val line = ContourLine(elevationMeters = 100f, points = listOf(ContourPoint(0f, 0f), ContourPoint(1f, 1f)))
        val feature = contourLinesToFeatures(tile, listOf(line), zoom = 12, metric = true).single()
        val positions = (feature.geometry as LineString).coordinates

        val expectedFirst = TerrainTileMath.lonLatAt(tile, 0f, 0f)
        assertEquals(expectedFirst.longitude, positions.first().longitude)
        assertEquals(expectedFirst.latitude, positions.first().latitude)
    }

    @Test
    fun `an index-level line's properties differ from a minor-level line's`() {
        // At zoom 8, metric, ContourIntervals bands the index level at 2500m and the minor at 500m increments —
        // see ContourIntervalsTest for the table this pins.
        val twoPoints = listOf(ContourPoint(0f, 0f), ContourPoint(1f, 0f))
        val indexLine = ContourLine(elevationMeters = 2_500f, points = twoPoints)
        val minorLine = ContourLine(elevationMeters = 500f, points = twoPoints)

        val indexFeature = contourLinesToFeatures(tile, listOf(indexLine), zoom = 8, metric = true).single()
        val minorFeature = contourLinesToFeatures(tile, listOf(minorLine), zoom = 8, metric = true).single()

        val indexWidth = indexFeature.properties?.get("stroke-width")?.jsonPrimitive?.float
        val minorWidth = minorFeature.properties?.get("stroke-width")?.jsonPrimitive?.float
        assertTrue(requireNotNull(indexWidth) > requireNotNull(minorWidth))
    }
}
