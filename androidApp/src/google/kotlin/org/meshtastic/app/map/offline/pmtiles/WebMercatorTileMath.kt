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
import org.meshtastic.feature.map.terrain.TerrainTileMath

/** [TerrainTileMath] in Google Maps [LatLng] terms, for MVT-local-coordinate placement. */
internal object WebMercatorTileMath {

    /** Places a feature's tile-local point (`0 until extent` on each axis) at its real-world [LatLng]. */
    fun tileLocalToLatLng(tile: TileIndex, extent: Int, local: TileCoord): LatLng =
        tileFractionalToLatLng(tile.zoom, tile.x, tile.y, local.x.toDouble() / extent, local.y.toDouble() / extent)

    /**
     * Places a fractional `[0,1]×[0,1]` tile-local point, [org.meshtastic.feature.map.terrain.ContourPoint]'s own
     * convention, at its real-world [LatLng].
     */
    fun tileFractionalToLatLng(zoom: Int, tileX: Int, tileY: Int, fracX: Double, fracY: Double): LatLng {
        val point = TerrainTileMath.lonLatAt(zoom, tileX + fracX, tileY + fracY)
        return LatLng(point.latitude, point.longitude)
    }
}

internal typealias TileIndex = org.meshtastic.feature.map.terrain.TileIndex
