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

import com.google.android.gms.maps.model.LatLngBounds
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.TerrainTileMath

/**
 * The standard slippy-map tile enumeration for a region, shared by the download estimate and the extractor.
 * [TerrainTileMath] does the enumeration, including the antimeridian split.
 */
internal object OfflineRegionTileSet {

    /** Every tile in [bounds] across [zoomRange], each zoom level's tiles listed before the next deepens. */
    fun tiles(bounds: LatLngBounds, zoomRange: IntRange): List<TileIndex> = zoomRange.flatMap { zoom ->
        TerrainTileMath.tilesAt(zoom, bounds.toGeoBounds())
    }

    fun estimateTileCount(bounds: LatLngBounds, zoomRange: IntRange): Long = zoomRange.sumOf { zoom ->
        TerrainTileMath.tileCountAt(zoom, bounds.toGeoBounds())
    }

    private fun LatLngBounds.toGeoBounds() = GeoBounds(
        south = southwest.latitude,
        west = southwest.longitude,
        north = northeast.latitude,
        east = northeast.longitude,
    )
}
