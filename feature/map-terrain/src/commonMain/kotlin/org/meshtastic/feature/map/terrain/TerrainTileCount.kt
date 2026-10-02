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

/**
 * How many tiles [TerrainRegionExtractor.download] fetches for [bounds] up to [maxZoom]: the global tier always, the
 * regional tier only where [MapterhornEndpoints.regionalUrlFor] finds an archive. Counted from tile corners, O(1) per
 * zoom, so it is safe to call before deciding whether a download fits.
 */
fun terrainTileCount(bounds: GeoBounds, maxZoom: Int): Long {
    val regional = regionalTerrainZooms(bounds, maxZoom)?.let { tileCount(bounds, it) } ?: 0L
    return tileCount(bounds, globalTerrainZooms(maxZoom)) + regional
}

/** Zoom levels fetched from the global archive for a download to [maxZoom]. */
internal fun globalTerrainZooms(maxZoom: Int): IntRange = 0..minOf(maxZoom, MapterhornEndpoints.GLOBAL_MAX_ZOOM)

/** Zoom levels fetched from the regional archive, or null when [bounds] has none or [maxZoom] stays global. */
internal fun regionalTerrainZooms(bounds: GeoBounds, maxZoom: Int): IntRange? =
    if (maxZoom > MapterhornEndpoints.GLOBAL_MAX_ZOOM && MapterhornEndpoints.regionalUrlFor(bounds) != null) {
        MapterhornEndpoints.REGIONAL_MIN_ZOOM..minOf(maxZoom, MapterhornEndpoints.REGIONAL_MAX_ZOOM)
    } else {
        null
    }

private fun tileCount(bounds: GeoBounds, zooms: IntRange): Long = zooms.sumOf { zoom ->
    TerrainTileMath.tileCountAt(zoom, bounds)
}
