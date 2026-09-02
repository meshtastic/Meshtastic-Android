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
package org.meshtastic.feature.map.terrain

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.pow
import kotlin.math.tan

/** A geographic bounding box, degrees. */
data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double)

/** A single XYZ (google/osm-convention, not TMS) slippy-map tile index. */
data class TileIndex(val zoom: Int, val x: Int, val y: Int)

/**
 * Standard XYZ/slippy-map Web Mercator tile math, self-contained here (rather than reused from either flavor's own copy
 * — `feature/map-maplibre`'s `TileEstimate.kt` and the Google flavor's `WebMercatorTileMath` in the sibling
 * `feat/map-google-pmtiles-offline` branch) so this module has no dependency in either direction on flavor-specific
 * code — this module is a shared math library, not a consumer of one flavor's app code.
 */
object TerrainTileMath {

    private const val MAX_LATITUDE = 85.05112878

    fun tileAt(zoom: Int, latitude: Double, longitude: Double): TileIndex {
        val n = 2.0.pow(zoom)
        val clampedLat = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val latRad = clampedLat * PI / HALF_TURN_DEGREES
        val x = (((longitude + FULL_TURN_DEGREES / 2) / FULL_TURN_DEGREES) * n).toInt().coerceIn(0, (n - 1).toInt())
        val y = (((1.0 - asinh(tan(latRad)) / PI) / 2.0) * n).toInt().coerceIn(0, (n - 1).toInt())
        return TileIndex(zoom, x, y)
    }

    /** Every tile in [bounds] at [zoom], inclusive of both corners' own tiles. */
    fun tilesAt(zoom: Int, bounds: GeoBounds): List<TileIndex> {
        val northwest = tileAt(zoom, bounds.north, bounds.west)
        val southeast = tileAt(zoom, bounds.south, bounds.east)
        val tiles = mutableListOf<TileIndex>()
        for (x in northwest.x..southeast.x) {
            for (y in northwest.y..southeast.y) {
                tiles += TileIndex(zoom, x, y)
            }
        }
        return tiles
    }

    /** Whether [bounds] fits entirely inside a single tile at [zoom] — used for Mapterhorn's regional-archive gate. */
    fun fitsInSingleTile(zoom: Int, bounds: GeoBounds): Boolean {
        val northwest = tileAt(zoom, bounds.north, bounds.west)
        val southeast = tileAt(zoom, bounds.south, bounds.east)
        return northwest == southeast
    }

    private const val HALF_TURN_DEGREES = 180.0
    private const val FULL_TURN_DEGREES = 360.0
}
