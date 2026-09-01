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
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/** Standard XYZ/slippy-map Web Mercator conversions, shared by tile enumeration and MVT-local-coordinate placement. */
internal object WebMercatorTileMath {

    /** The tile (at [zoom]) containing [latLng] — the same indexing GoogleMap, MapLibre and PMTiles all share. */
    fun tileAt(zoom: Int, latLng: LatLng): TileIndex {
        val n = 2.0.pow(zoom)
        val latRad = Math.toRadians(latLng.latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        val x = ((latLng.longitude + LON_RANGE_DEG) / FULL_LON_RANGE_DEG * n).toInt().coerceIn(0, (n - 1).toInt())
        val y = ((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, (n - 1).toInt())
        return TileIndex(zoom, x, y)
    }

    /** Places a feature's tile-local point (`0 until extent` on each axis) at its real-world [LatLng]. */
    fun tileLocalToLatLng(tile: TileIndex, extent: Int, local: TileCoord): LatLng {
        val n = 2.0.pow(tile.zoom)
        val fx = tile.x + local.x.toDouble() / extent
        val fy = tile.y + local.y.toDouble() / extent
        val lon = fx / n * FULL_LON_RANGE_DEG - LON_RANGE_DEG
        val latRad = atan(sinh(PI * (1 - 2 * fy / n)))
        return LatLng(Math.toDegrees(latRad), lon)
    }

    private const val LON_RANGE_DEG = 180.0
    private const val FULL_LON_RANGE_DEG = 360.0

    /** Web Mercator's own latitude ceiling (~85.0511°), where the projection would otherwise reach infinity. */
    private const val MAX_LATITUDE = 85.05112878
}

internal data class TileIndex(val zoom: Int, val x: Int, val y: Int)
