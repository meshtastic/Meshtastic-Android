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
package org.meshtastic.feature.map.maplibre.terrain

import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainTileMath

/**
 * How many tiles [TerrainRegionExtractor][org.meshtastic.feature.map.terrain.TerrainRegionExtractor] would fetch for
 * [bounds] up to [maxZoom] — the global tier always, the regional tier only where [MapterhornEndpoints.regionalUrlFor]
 * finds one.
 *
 * A separate, pure re-implementation of the extractor's own tile enumeration rather than a shared helper: the
 * extractor's `download` is a suspending [kotlinx.coroutines.flow.Flow] that fetches as it counts, which is exactly the
 * network work this estimate has to run *without* — matching [org.meshtastic.feature.map.maplibre.tileCount]'s own
 * "shown before a download starts" role for the base offline layer. If the extractor's own enumeration ever changes,
 * this must change with it — that coupling is intentional, not an oversight; a unit test pins it against
 * [MapterhornEndpoints]'s real constants rather than a copy of them.
 */
internal fun estimateTerrainTiles(bounds: GeoBounds, maxZoom: Int): Long {
    val globalZoomRange = 0..minOf(maxZoom, MapterhornEndpoints.GLOBAL_MAX_ZOOM)
    val globalCount = globalZoomRange.sumOf { zoom -> countTiles(zoom, bounds) }

    val hasRegionalTier =
        maxZoom > MapterhornEndpoints.GLOBAL_MAX_ZOOM && MapterhornEndpoints.regionalUrlFor(bounds) != null
    val regionalCount =
        if (hasRegionalTier) {
            val regionalMaxZoom = minOf(maxZoom, MapterhornEndpoints.REGIONAL_MAX_ZOOM)
            val regionalZoomRange = MapterhornEndpoints.REGIONAL_MIN_ZOOM..regionalMaxZoom
            regionalZoomRange.sumOf { zoom -> countTiles(zoom, bounds) }
        } else {
            0L
        }

    return globalCount + regionalCount
}

/**
 * How many tiles [TerrainTileMath.tilesAt] would return for [zoom]/[bounds], computed from the two corner indices
 * rather than by materializing the list — [TerrainTileMath.tilesAt] doesn't unwrap the antimeridian (unlike
 * [org.meshtastic.feature.map.maplibre.tileCount]'s own counter), so a world-spanning [bounds] at a deep zoom is a
 * multi-million-entry list; this stays O(1) per zoom regardless of how large the count itself is. `coerceAtLeast(0)`
 * mirrors what an empty `IntRange` (start > end) already does in [TerrainTileMath.tilesAt]'s own loop.
 */
private fun countTiles(zoom: Int, bounds: GeoBounds): Long {
    val northwest = TerrainTileMath.tileAt(zoom, bounds.north, bounds.west)
    val southeast = TerrainTileMath.tileAt(zoom, bounds.south, bounds.east)
    val columns = (southeast.x - northwest.x + 1).coerceAtLeast(0)
    val rows = (southeast.y - northwest.y + 1).coerceAtLeast(0)
    return columns.toLong() * rows.toLong()
}
