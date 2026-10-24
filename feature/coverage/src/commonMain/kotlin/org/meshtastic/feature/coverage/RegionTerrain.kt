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
import org.meshtastic.feature.map.terrain.TerrainTileMath
import org.meshtastic.feature.map.terrain.TerrainTileStore
import org.meshtastic.feature.map.terrain.TileIndex

/** A downloaded map region's terrain: its tiles, and the ground it was downloaded for. */
class RegionTerrain(val store: TerrainTileStore, val bounds: GeoBounds)

/** The ground this tile covers. */
internal fun TileIndex.bounds(): GeoBounds {
    val northwest = TerrainTileMath.lonLatAt(zoom, x.toDouble(), y.toDouble())
    val southeast = TerrainTileMath.lonLatAt(zoom, x + 1.0, y + 1.0)
    return GeoBounds(
        south = southeast.latitude,
        west = northwest.longitude,
        north = northwest.latitude,
        east = southeast.longitude,
    )
}

/** Whether the two overlap. A box whose west is east of its east crosses the antimeridian. */
internal fun GeoBounds.intersects(other: GeoBounds): Boolean = south < other.north &&
    other.south < north &&
    longitudeSpans().any { (w, e) -> other.longitudeSpans().any { (ow, oe) -> w < oe && ow < e } }

private fun GeoBounds.longitudeSpans(): List<Pair<Double, Double>> =
    if (west <= east) listOf(west to east) else listOf(west to MAX_LONGITUDE, -MAX_LONGITUDE to east)

private const val MAX_LONGITUDE = 180.0
