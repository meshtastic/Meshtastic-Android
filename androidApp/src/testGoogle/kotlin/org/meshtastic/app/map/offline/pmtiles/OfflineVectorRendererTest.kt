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

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineVectorRendererTest {

    private val west = TileIndex(zoom = 2, x = 1, y = 1)
    private val east = TileIndex(zoom = 2, x = 2, y = 1)

    private fun lakeAt(tile: TileIndex, fromFrac: Double, toFrac: Double, id: Long?): OfflineFeature {
        fun at(fx: Double, fy: Double) = WebMercatorTileMath.tileFractionalToLatLng(tile.zoom, tile.x, tile.y, fx, fy)
        return OfflineFeature(
            layerName = "water",
            geometryType = VectorTile.GEOM_POLYGON,
            rings = emptyList(),
            polygons =
            listOf(
                OfflinePolygon(
                    listOf(
                        at(fromFrac, 0.2),
                        at(toFrac, 0.2),
                        at(toFrac, 0.4),
                        at(fromFrac, 0.4),
                        at(fromFrac, 0.2),
                    ),
                    emptyList(),
                ),
            ),
            id = id,
            tile = tile,
        )
    }

    @Test
    fun `a copy wholly inside a neighbor tile that has the same feature is dropped`() {
        // The lake belongs to the east tile; the west tile carries it in its buffer, past its own east edge (fx > 1).
        val own = lakeAt(east, 0.2, 0.4, id = 7)
        val bufferCopy = lakeAt(west, 1.3, 1.5, id = 7)

        val kept = OfflineVectorRenderer.dropBufferCopies(listOf(listOf(bufferCopy), listOf(own)))

        assertEquals(listOf(own), kept)
    }

    @Test
    fun `a feature cut by a tile edge keeps each tile's piece`() {
        val westPiece = lakeAt(west, 0.6, 1.2, id = 7)
        val eastPiece = lakeAt(east, -0.2, 0.4, id = 7)

        val kept = OfflineVectorRenderer.dropBufferCopies(listOf(listOf(westPiece), listOf(eastPiece)))

        assertEquals(listOf(westPiece, eastPiece), kept)
    }

    @Test
    fun `features without an id are kept, and the same id in another layer is a different feature`() {
        val noId = lakeAt(east, 0.2, 0.4, id = null)
        val noIdCopy = lakeAt(west, 1.3, 1.5, id = null)
        val road = lakeAt(west, 1.3, 1.5, id = 7).copy(layerName = "roads")
        val lake = lakeAt(east, 0.2, 0.4, id = 7)

        val kept = OfflineVectorRenderer.dropBufferCopies(listOf(listOf(noIdCopy, road), listOf(noId, lake)))

        assertEquals(listOf(noIdCopy, road, noId, lake), kept)
    }

    @Test
    fun `a point on a shared tile edge is kept once`() {
        fun pointAt(tile: TileIndex, fx: Double) = OfflineFeature(
            layerName = "places",
            geometryType = VectorTile.GEOM_POINT,
            rings = listOf(listOf(WebMercatorTileMath.tileFractionalToLatLng(tile.zoom, tile.x, tile.y, fx, 0.5))),
            id = 9,
            tile = tile,
        )
        val fromWest = pointAt(west, 1.0)
        val fromEast = pointAt(east, 0.0)

        val kept = OfflineVectorRenderer.dropBufferCopies(listOf(listOf(fromWest), listOf(fromEast)))

        assertEquals(listOf(fromWest), kept)
    }

    @Test
    fun `a buffer copy inside the easternmost tile is dropped`() {
        val easternmost = TileIndex(zoom = 2, x = 3, y = 1)
        val own = lakeAt(easternmost, 0.3, 0.5, id = 7)
        val bufferCopy = lakeAt(east, 1.3, 1.5, id = 7)

        val kept = OfflineVectorRenderer.dropBufferCopies(listOf(listOf(bufferCopy), listOf(own)))

        assertEquals(listOf(own), kept)
    }

    @Test
    fun `a lone tile's features are untouched`() {
        val only = lakeAt(east, 0.2, 0.4, id = 7)

        assertEquals(listOf(only), OfflineVectorRenderer.dropBufferCopies(listOf(listOf(only))))
    }
}
