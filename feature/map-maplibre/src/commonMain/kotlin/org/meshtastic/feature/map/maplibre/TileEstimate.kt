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
package org.meshtastic.feature.map.maplibre

import org.maplibre.spatialk.geojson.BoundingBox
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * How many tiles cover [this] region between [minZoom] and [maxZoom] inclusive.
 *
 * Shown to the user before a download starts, which is what the OSMdroid map did: a region that looks modest on screen
 * can be thousands of tiles once a few zoom levels are included, and the only honest way to convey that is to count
 * them up front.
 *
 * Standard slippy-map arithmetic, so it matches what the renderer will actually request.
 */
internal fun BoundingBox.tileCount(minZoom: Int, maxZoom: Int): Long {
    if (maxZoom < minZoom) return 0L

    return (minZoom..maxZoom).sumOf { zoom ->
        val span = 1 shl zoom
        val left = longitudeToTileX(west, span)
        val right = longitudeToTileX(east, span)
        // Tile rows run north to south, so the northern edge gives the lower index.
        val top = latitudeToTileY(north, span)
        val bottom = latitudeToTileY(south, span)

        // A box straddling the antimeridian arrives with west > east: its columns wrap around the tile grid,
        // and the direct difference would go negative.
        val columns = if (right >= left) right - left + 1 else span - left + right + 1
        columns.toLong() * (bottom - top + 1).toLong()
    }
}

private fun longitudeToTileX(longitude: Double, span: Int): Int =
    floor((longitude + HALF_TURN) / FULL_TURN * span).toInt().coerceIn(0, span - 1)

private fun latitudeToTileY(latitude: Double, span: Int): Int {
    // Clamped to the Mercator limit: the projection runs to infinity at the poles.
    val radians = latitude.coerceIn(-MERCATOR_LIMIT, MERCATOR_LIMIT) * PI / HALF_TURN
    val projected = ln(tan(radians) + 1.0 / cos(radians)) / PI
    return floor((1.0 - projected) / 2.0 * span).toInt().coerceIn(0, span - 1)
}

private const val HALF_TURN = 180.0
private const val FULL_TURN = 360.0
private const val MERCATOR_LIMIT = 85.05112878
