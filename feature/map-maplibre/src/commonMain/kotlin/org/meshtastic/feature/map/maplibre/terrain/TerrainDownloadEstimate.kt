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
 * The counting itself is [TerrainTileMath.tileCountAt] — the same O(1)-per-zoom corner arithmetic the extractor uses to
 * bound memory before it enumerates tiles, so this and the extractor can never silently disagree on *how many* a zoom
 * level covers. What's re-derived here, network-free, is only the *which zoom levels / which tier* decision from the
 * extractor's own suspending `download` [kotlinx.coroutines.flow.Flow] — matching
 * [org.meshtastic.feature.map.maplibre.tileCount]'s own "shown before a download starts" role for the base offline
 * layer. If the extractor's own tier/zoom-range logic ever changes, this must change with it — that coupling is
 * intentional, not an oversight; a unit test pins it against [MapterhornEndpoints]'s real constants rather than a copy
 * of them.
 */
internal fun estimateTerrainTiles(bounds: GeoBounds, maxZoom: Int): Long {
    val globalZoomRange = 0..minOf(maxZoom, MapterhornEndpoints.GLOBAL_MAX_ZOOM)
    val globalCount = globalZoomRange.sumOf { zoom -> TerrainTileMath.tileCountAt(zoom, bounds) }

    val hasRegionalTier =
        maxZoom > MapterhornEndpoints.GLOBAL_MAX_ZOOM && MapterhornEndpoints.regionalUrlFor(bounds) != null
    val regionalCount =
        if (hasRegionalTier) {
            val regionalMaxZoom = minOf(maxZoom, MapterhornEndpoints.REGIONAL_MAX_ZOOM)
            val regionalZoomRange = MapterhornEndpoints.REGIONAL_MIN_ZOOM..regionalMaxZoom
            regionalZoomRange.sumOf { zoom -> TerrainTileMath.tileCountAt(zoom, bounds) }
        } else {
            0L
        }

    return globalCount + regionalCount
}
