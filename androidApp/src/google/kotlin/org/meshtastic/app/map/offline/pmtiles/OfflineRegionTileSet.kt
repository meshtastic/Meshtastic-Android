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

/** The standard slippy-map tile enumeration for a region, shared by the download estimate and the extractor. */
internal object OfflineRegionTileSet {

    /** Every tile in [bounds] across [zoomRange], each zoom level's tiles listed before the next deepens. */
    fun tiles(bounds: LatLngBounds, zoomRange: IntRange): List<TileIndex> =
        zoomRange.flatMap { zoom -> tilesAtZoom(bounds, zoom) }

    fun estimateTileCount(bounds: LatLngBounds, zoomRange: IntRange): Long =
        zoomRange.sumOf { zoom -> tilesAtZoom(bounds, zoom).size.toLong() }

    private fun tilesAtZoom(bounds: LatLngBounds, zoom: Int): List<TileIndex> {
        // Northwest corner has the smaller tile-column and smallest tile-row (XYZ rows increase southward);
        // southeast has the larger of both.
        val northwestCorner = LatLng(bounds.northeast.latitude, bounds.southwest.longitude)
        val southeastCorner = LatLng(bounds.southwest.latitude, bounds.northeast.longitude)
        val northwest = WebMercatorTileMath.tileAt(zoom, northwestCorner)
        val southeast = WebMercatorTileMath.tileAt(zoom, southeastCorner)

        val tiles = mutableListOf<TileIndex>()
        for (x in northwest.x..southeast.x) {
            for (y in northwest.y..southeast.y) {
                tiles += TileIndex(zoom, x, y)
            }
        }
        return tiles
    }
}
