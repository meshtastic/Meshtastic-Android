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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface TerrainDownloadState {
    data class InProgress(val completed: Int, val total: Int) : TerrainDownloadState

    data class Complete(val tileCount: Long, val byteSize: Long, val hasRegionalDetail: Boolean) : TerrainDownloadState

    data class Failed(val reason: TerrainDownloadFailure) : TerrainDownloadState
}

enum class TerrainDownloadFailure {
    /** More tiles than [TerrainRegionExtractor.MAX_TILES] — likely too large an area or too deep a zoom. */
    TILE_LIMIT_EXCEEDED,

    /** A network or disk failure partway through; nothing partial is kept. */
    IO_ERROR,
}

/**
 * Extracts a region's offline terrain: a global low-res layer (always, up to [MapterhornEndpoints.GLOBAL_MAX_ZOOM])
 * plus, where [bounds] fits inside a single z6 tile, a regional high-res layer on top — ported from the sibling iOS
 * app's two-tier `TerrainStore` download behavior. One tile per HTTP request, like the base offline layer's own
 * extractor; see its README for why that bounds [MAX_TILES] well below iOS's own per-region cap.
 *
 * This module owns nothing about how a terrain download relates to a base map region (shared storage budget, region
 * lifecycle, "delete the terrain without touching the basemap") — that's the caller's integration concern, same
 * separation as [TerrainTileStore] only knowing about the directory it's handed.
 */
class TerrainRegionExtractor(private val store: TerrainTileStore) {

    fun download(bounds: GeoBounds, maxZoom: Int): Flow<TerrainDownloadState> = flow {
        val globalZoomRange = 0..minOf(maxZoom, MapterhornEndpoints.GLOBAL_MAX_ZOOM)
        val globalTiles = globalZoomRange.flatMap { zoom -> TerrainTileMath.tilesAt(zoom, bounds) }

        val regionalUrl =
            if (maxZoom > MapterhornEndpoints.GLOBAL_MAX_ZOOM) MapterhornEndpoints.regionalUrlFor(bounds) else null
        val regionalZoomRange =
            MapterhornEndpoints.REGIONAL_MIN_ZOOM..minOf(maxZoom, MapterhornEndpoints.REGIONAL_MAX_ZOOM)
        val regionalTiles =
            if (regionalUrl != null) {
                regionalZoomRange.flatMap { zoom -> TerrainTileMath.tilesAt(zoom, bounds) }
            } else {
                emptyList()
            }

        val totalTiles = globalTiles.size + regionalTiles.size
        if (totalTiles > MAX_TILES) {
            emit(TerrainDownloadState.Failed(TerrainDownloadFailure.TILE_LIMIT_EXCEEDED))
            return@flow
        }
        if (totalTiles == 0) {
            emit(TerrainDownloadState.Complete(tileCount = 0, byteSize = 0, hasRegionalDetail = false))
            return@flow
        }

        var completed = 0
        try {
            completed =
                fetchInto(
                    MapterhornEndpoints.GLOBAL_PMTILES_URL,
                    TerrainSource.GLOBAL,
                    globalTiles,
                    completed,
                    totalTiles,
                ) {
                    emit(it)
                }
            if (regionalUrl != null) {
                completed =
                    fetchInto(regionalUrl, TerrainSource.REGIONAL, regionalTiles, completed, totalTiles) { emit(it) }
            }
        } catch (_: Exception) {
            store.deleteAll()
            emit(TerrainDownloadState.Failed(TerrainDownloadFailure.IO_ERROR))
            return@flow
        }

        emit(
            TerrainDownloadState.Complete(
                tileCount = completed.toLong(),
                byteSize = store.sizeBytes(),
                hasRegionalDetail = regionalUrl != null,
            ),
        )
    }

    private suspend fun fetchInto(
        pmtilesUrl: String,
        source: TerrainSource,
        tiles: List<TileIndex>,
        startingCompleted: Int,
        totalTiles: Int,
        onProgress: suspend (TerrainDownloadState.InProgress) -> Unit,
    ): Int {
        var completed = startingCompleted
        TerrainTileFetcher(pmtilesUrl).use { fetcher ->
            for (tile in tiles) {
                fetcher.fetchTile(tile.zoom, tile.x, tile.y)?.let { bytes -> store.writeTile(source, tile, bytes) }
                completed++
                if (completed % PROGRESS_STRIDE == 0 || completed == totalTiles) {
                    onProgress(TerrainDownloadState.InProgress(completed, totalTiles))
                }
            }
        }
        return completed
    }

    companion object {
        /** See the base offline layer's own extractor for why this is far below iOS's 600k-tile-scale caps. */
        const val MAX_TILES = 3_000
        private const val PROGRESS_STRIDE = 10
    }
}
