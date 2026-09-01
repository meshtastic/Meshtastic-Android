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

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineRegionTileSetTest {

    @Test
    fun `the whole world is exactly one tile at zoom 0`() {
        val world = LatLngBounds(LatLng(-85.0, -179.0), LatLng(85.0, 179.0))

        assertEquals(1L, OfflineRegionTileSet.estimateTileCount(world, 0..0))
    }

    @Test
    fun `a tight box still counts at least one tile per zoom level`() {
        val tinyBox = LatLngBounds(LatLng(37.7749, -122.4194), LatLng(37.7750, -122.4193))

        assertEquals(3L, OfflineRegionTileSet.estimateTileCount(tinyBox, 10..12))
    }

    @Test
    fun `tile count grows monotonically with zoom depth`() {
        val box = LatLngBounds(LatLng(40.0, -75.0), LatLng(41.0, -74.0))

        val shallow = OfflineRegionTileSet.estimateTileCount(box, 8..8)
        val deep = OfflineRegionTileSet.estimateTileCount(box, 12..12)

        assertTrue(deep > shallow)
    }

    @Test
    fun `estimateTileCount matches the tiles actually enumerated`() {
        val box = LatLngBounds(LatLng(40.0, -75.0), LatLng(41.0, -74.0))

        assertEquals(
            OfflineRegionTileSet.estimateTileCount(box, 5..9).toInt(),
            OfflineRegionTileSet.tiles(box, 5..9).size,
        )
    }
}
