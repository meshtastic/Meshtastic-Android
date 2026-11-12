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
package org.meshtastic.app.map

import com.google.maps.android.data.parser.geojson.GeoJsonParser
import com.google.maps.android.data.renderer.mapper.toLayer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MapLayerVertexBudgetTest {

    private fun layerFrom(geoJson: String) = assertNotNull(GeoJsonParser().parse(geoJson.byteInputStream())?.toLayer())

    @Test
    fun `a polygon counts its outline and every hole`() {
        val layer =
            layerFrom(
                """
                {"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[
                  [[0,0],[4,0],[4,4],[0,4],[0,0]],
                  [[1,1],[2,1],[2,2],[1,1]],
                  [[2,2],[3,2],[3,3],[2,2]]]}}
                """
                    .trimIndent(),
            )
        assertEquals(13, layer.vertexCount())
    }

    @Test
    fun `a multi polygon and a line and a point are summed across features`() {
        val layer =
            layerFrom(
                """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{},"geometry":{"type":"MultiPolygon","coordinates":[
                    [[[0,0],[1,0],[1,1],[0,0]]],
                    [[[5,5],[6,5],[6,6],[5,5]],[[5.2,5.2],[5.4,5.2],[5.4,5.4],[5.2,5.2]]]]}},
                  {"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[[0,0],[1,1],[2,2]]}},
                  {"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[7,7]}}]}
                """
                    .trimIndent(),
            )
        assertEquals(12 + 3 + 1, layer.vertexCount())
    }

    @Test
    fun `a layer larger than the per layer ceiling is refused even on an empty map`() {
        val budget = MapLayerVertexBudget(perLayer = 100, total = 1_000)
        assertFalse(budget.tryAcquire(101))
        assertEquals(0, budget.inUse)
    }

    @Test
    fun `layers are refused once the total is spent and accepted again after a release`() {
        val budget = MapLayerVertexBudget(perLayer = 100, total = 250)
        assertTrue(budget.tryAcquire(100))
        assertTrue(budget.tryAcquire(100))
        assertFalse(budget.tryAcquire(100))
        assertTrue(budget.tryAcquire(50))
        budget.release(100)
        assertTrue(budget.tryAcquire(100))
        assertEquals(250, budget.inUse)
    }

    @Test
    fun `a release never drives the count below zero`() {
        val budget = MapLayerVertexBudget(perLayer = 10, total = 10)
        budget.release(5)
        assertEquals(0, budget.inUse)
        assertTrue(budget.tryAcquire(10))
    }

    @Test
    fun `the ceilings scale with the heap`() {
        val small = MapLayerVertexBudget.forHeap(256L * 1024 * 1024)
        val large = MapLayerVertexBudget.forHeap(512L * 1024 * 1024)
        assertEquals(small.total * 2, large.total)
        assertTrue(small.perLayer < small.total)
        assertTrue(large.perLayer in 200_000..250_000)
    }
}
