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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebMercatorTileMathTest {

    @Test
    fun `zoom 0 has exactly one tile, covering everywhere`() {
        assertEquals(TileIndex(0, 0, 0), WebMercatorTileMath.tileAt(zoom = 0, LatLng(0.0, 0.0)))
        assertEquals(TileIndex(0, 0, 0), WebMercatorTileMath.tileAt(zoom = 0, LatLng(70.0, 179.0)))
    }

    @Test
    fun `zoom 1 splits the world into quadrants by hemisphere`() {
        assertEquals(TileIndex(1, 0, 0), WebMercatorTileMath.tileAt(zoom = 1, LatLng(45.0, -170.0))) // NW
        assertEquals(TileIndex(1, 1, 0), WebMercatorTileMath.tileAt(zoom = 1, LatLng(45.0, 10.0))) // NE
        assertEquals(TileIndex(1, 0, 1), WebMercatorTileMath.tileAt(zoom = 1, LatLng(-45.0, -170.0))) // SW
        assertEquals(TileIndex(1, 1, 1), WebMercatorTileMath.tileAt(zoom = 1, LatLng(-45.0, 10.0))) // SE
    }

    @Test
    fun `the tile origin lands on Web Mercator's own latitude ceiling`() {
        // At extent 1, local (0, 0) is the tile's own fx=0, fy=0 corner — the world tile's NW corner.
        val corner = WebMercatorTileMath.tileLocalToLatLng(TileIndex(0, 0, 0), extent = 1, TileCoord(0, 0))

        assertEquals(-180.0, corner.longitude, ABSOLUTE_TOLERANCE)
        assertTrue(abs(corner.latitude - WEB_MERCATOR_MAX_LATITUDE) < ABSOLUTE_TOLERANCE)
    }

    @Test
    fun `the middle of the world tile is the equator and prime meridian`() {
        val center = WebMercatorTileMath.tileLocalToLatLng(TileIndex(0, 0, 0), extent = 2, TileCoord(1, 1))

        assertEquals(0.0, center.longitude, ABSOLUTE_TOLERANCE)
        assertEquals(0.0, center.latitude, ABSOLUTE_TOLERANCE)
    }

    @Test
    fun `a deeper tile's local placement matches its own slice of the world`() {
        // Tile (2, 3, 1) at zoom 2 spans lon [90, 180); its local midpoint (extent 2, local (1,1)) should land
        // exactly on that range's own midpoint, 135 — not drift into a neighboring tile's range, which is the bug
        // off-by-one tile-index math would produce here.
        val point = WebMercatorTileMath.tileLocalToLatLng(TileIndex(2, 3, 1), extent = 2, TileCoord(1, 1))

        assertEquals(135.0, point.longitude, ABSOLUTE_TOLERANCE)
    }

    private companion object {
        const val ABSOLUTE_TOLERANCE = 1e-6
        const val WEB_MERCATOR_MAX_LATITUDE = 85.05112878
    }
}
