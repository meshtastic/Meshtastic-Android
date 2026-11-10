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
package org.meshtastic.app.map.offline.pmtiles

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.TerrainTileMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class OfflineBuildingsTest {
    private val downtown = GeoBounds(south = 47.600, west = -122.345, north = 47.620, east = -122.315)

    /** MoveTo, three LineTo and ClosePath around a square from ([x], [y]) with [side], in MVT's zigzag deltas. */
    private fun square(x: Int, y: Int, side: Int): List<Int> {
        fun zigzag(n: Int) = (n shl 1) xor (n shr 31)
        return listOf(9, zigzag(x), zigzag(y), 26, zigzag(side), 0, 0, zigzag(side), zigzag(-side), 0, 15)
    }

    @Test
    fun `buildings come from the deepest zoom a region has that fits the tile budget`() {
        assertEquals(15, OfflineBuildings.zoom(downtown, 10..16))
        assertEquals(14, OfflineBuildings.zoom(downtown, 10..14))
        val atFourteen = TerrainTileMath.tileCountAt(14, downtown).toInt()
        assertTrue(TerrainTileMath.tileCountAt(15, downtown) > atFourteen)
        assertEquals(14, OfflineBuildings.zoom(downtown, 10..16, maxTiles = atFourteen))
        assertNull(OfflineBuildings.zoom(downtown, 8..12))
        assertNull(OfflineBuildings.zoom(GeoBounds(south = 46.5, west = -123.5, north = 48.5, east = -121.0), 10..16))
    }

    @Test
    fun `an outline over the tile's edge is cut to the tile`() {
        val ring =
            listOf(TileCoord(-10, -10), TileCoord(10, -10), TileCoord(10, 10), TileCoord(-10, 10), TileCoord(-10, -10))

        val cut = OfflineBuildings.clip(ring, extent = 4096)

        assertEquals(setOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0, 0.0 to 10.0), cut.toSet())
        assertEquals(4, cut.size)
        assertTrue(OfflineBuildings.clip(ring.map { TileCoord(it.x - 100, it.y) }, extent = 4096).isEmpty())
    }

    @Test
    fun `a tile's buildings carry their height and whether they are a part`() {
        val layer =
            VectorTile.Layer(
                name = "buildings",
                keys = listOf("height", "min_height", "kind"),
                values =
                listOf(
                    VectorTile.Value(doubleValue = 184.0),
                    VectorTile.Value(intValue = 100),
                    VectorTile.Value(stringValue = "building_part"),
                    VectorTile.Value(stringValue = "building"),
                ),
                features =
                listOf(
                    VectorTile.Feature(
                        id = 1,
                        tags = listOf(0, 0, 1, 1, 2, 2),
                        type = VectorTile.GEOM_POLYGON,
                        geometry = square(1024, 1024, 64),
                    ),
                    VectorTile.Feature(
                        id = 2,
                        tags = listOf(2, 3),
                        type = VectorTile.GEOM_POLYGON,
                        geometry = square(2048, 2048, 64),
                    ),
                    VectorTile.Feature(id = 3, type = VectorTile.GEOM_POINT, geometry = listOf(9, 0, 0)),
                ),
            )
        val roads = layer.copy(name = "roads")
        val tile = TerrainTileMath.tileAt(15, 47.6062, -122.3321)
        val bytes = ProtoBuf.encodeToByteArray(VectorTile.serializer(), VectorTile(listOf(roads, layer)))

        val (part, plain) = OfflineBuildings.decode(tile, bytes).also { assertEquals(2, it.size) }

        assertEquals(184.0, part.heightMeters)
        assertEquals(100.0, part.minHeightMeters)
        assertTrue(part.isPart)
        assertEquals(4, part.outline.size)
        val northwest = TerrainTileMath.lonLatAt(15, tile.x + 0.25, tile.y + 0.25)
        assertEquals(northwest.latitude, part.outline.maxOf { it.latitude }, 1e-9)
        assertEquals(northwest.longitude, part.outline.minOf { it.longitude }, 1e-9)
        assertNull(plain.heightMeters)
        assertEquals(0.0, plain.minHeightMeters)
        assertTrue(!plain.isPart)
    }
}
