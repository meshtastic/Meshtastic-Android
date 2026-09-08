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
package org.meshtastic.app.map.offline.terrain

import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainTileMath

/**
 * Picks how deep a terrain download should go before starting one.
 *
 * [org.meshtastic.feature.map.terrain.TerrainRegionExtractor] deliberately has no zoom-fitting of its own — per its own
 * doc comment, relating a terrain download to a base region's storage budget is the caller's job. Left to always
 * request Mapterhorn's full regional depth (zoom 18), most regions small enough to actually qualify for regional detail
 * (they must fit inside a single zoom-6 tile — see [MapterhornEndpoints.regionalUrlFor]) would still enumerate a 13-18
 * tile count well past [org.meshtastic.feature.map.terrain.TerrainRegionExtractor.MAX_TILES] and fail outright with no
 * partial credit. So this walks zoom back from 18 until the enumeration — computed the same way the extractor's own
 * `download` counts tiles — actually fits.
 */
internal object TerrainDownloadPlanner {

    /**
     * The deepest zoom in `[MapterhornEndpoints.GLOBAL_MAX_ZOOM, MapterhornEndpoints.REGIONAL_MAX_ZOOM]` whose tile
     * count fits within [maxTiles]. Falls back to [MapterhornEndpoints.GLOBAL_MAX_ZOOM] when even the always-downloaded
     * global tier alone already exceeds [maxTiles] — the extractor will still fail with `TILE_LIMIT_EXCEEDED` in that
     * case, same as it always would; this function's only job is to not make that worse by also asking for regional
     * detail on top.
     */
    fun maxZoomFitting(bounds: GeoBounds, maxTiles: Int): Int {
        for (zoom in MapterhornEndpoints.REGIONAL_MAX_ZOOM downTo MapterhornEndpoints.GLOBAL_MAX_ZOOM) {
            if (tileCount(bounds, zoom) <= maxTiles) return zoom
        }
        return MapterhornEndpoints.GLOBAL_MAX_ZOOM
    }

    /**
     * Mirrors [org.meshtastic.feature.map.terrain.TerrainRegionExtractor.download]'s own tile enumeration, but via
     * [TerrainTileMath.tileCountAt] rather than materializing each zoom level's tile list — this function walks zoom 18
     * down to 12 on every call, and materializing to count at the deep end would be the same
     * count-before-you-can-afford-to-materialize problem the extractor itself was fixed for.
     */
    private fun tileCount(bounds: GeoBounds, maxZoom: Int): Long {
        val globalZoomRange = 0..minOf(maxZoom, MapterhornEndpoints.GLOBAL_MAX_ZOOM)
        val globalCount = globalZoomRange.sumOf { zoom -> TerrainTileMath.tileCountAt(zoom, bounds) }

        val regionalUrl =
            if (maxZoom > MapterhornEndpoints.GLOBAL_MAX_ZOOM) MapterhornEndpoints.regionalUrlFor(bounds) else null
        val regionalCount =
            if (regionalUrl == null) {
                0L
            } else {
                val regionalZoomRange =
                    MapterhornEndpoints.REGIONAL_MIN_ZOOM..minOf(maxZoom, MapterhornEndpoints.REGIONAL_MAX_ZOOM)
                regionalZoomRange.sumOf { zoom -> TerrainTileMath.tileCountAt(zoom, bounds) }
            }

        return globalCount + regionalCount
    }
}
