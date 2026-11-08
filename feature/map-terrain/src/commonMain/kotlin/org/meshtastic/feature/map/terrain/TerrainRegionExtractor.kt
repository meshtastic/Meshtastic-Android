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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface TerrainDownloadState {
    data class InProgress(val completed: Int, val total: Int) : TerrainDownloadState

    /** [archives] maps each archive URL that supplied tiles to its version (the server's ETag), when it gave one. */
    data class Complete(
        val tileCount: Long,
        val byteSize: Long,
        val hasRegionalDetail: Boolean,
        val archives: Map<String, String?> = emptyMap(),
    ) : TerrainDownloadState

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
 * plus a regional high-res layer from every z6 regional archive the bounds touch, each clipped to its own z6 tile. A z6
 * tile with no published archive contributes nothing, and the renderer falls back to the global layer there. One tile
 * per HTTP request, like the base offline layer's own extractor; see its README for why that bounds [MAX_TILES] well
 * below iOS's own per-region cap.
 *
 * This module owns nothing about how a terrain download relates to a base map region (shared storage budget, region
 * lifecycle, "delete the terrain without touching the basemap") — that's the caller's integration concern, same
 * separation as [TerrainTileStore] only knowing about the directory it's handed.
 */
class TerrainRegionExtractor(
    private val store: TerrainTileStore,
    private val openArchive: (pmtilesUrl: String) -> TileArchive? = ::remoteArchive,
) {

    /**
     * One open PMTiles archive — [TerrainTileFetcher] in production; a seam so tests can script sparse coverage.
     * [openArchive] returns `null` for an archive the host doesn't have.
     */
    interface TileArchive : AutoCloseable {
        /** The archive's version as the server reports it (its ETag), or `null` if it gave none. */
        val version: String?
            get() = null

        fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray?
    }

    @Suppress("SuspendFunSwallowedCancellation") // cancellation deletes the partial region, then is rethrown
    fun download(bounds: GeoBounds, maxZoom: Int): Flow<TerrainDownloadState> = flow {
        val globalZoomRange = globalTerrainZooms(maxZoom)
        val regionalZoomRange = regionalTerrainZooms(maxZoom)

        // Counted from tile corners before anything is materialized. A single regional z6 tile at maxZoom 18 is ~16.7M
        // tiles at z18 alone; enumerating before this check would allocate that whole list on exactly the oversized
        // requests this limit exists to reject.
        val totalTiles = terrainTileCount(bounds, maxZoom)
        if (totalTiles > MAX_TILES) {
            emit(TerrainDownloadState.Failed(TerrainDownloadFailure.TILE_LIMIT_EXCEEDED))
            return@flow
        }
        if (totalTiles == 0L) {
            emit(TerrainDownloadState.Complete(tileCount = 0, byteSize = 0, hasRegionalDetail = false))
            return@flow
        }

        // Only materialized now that the cheap count above has confirmed it's under MAX_TILES.
        val globalTiles = globalZoomRange.flatMap { zoom -> TerrainTileMath.tilesAt(zoom, bounds) }
        val regionalTilesByArchive =
            regionalZoomRange
                ?.flatMap { zoom -> TerrainTileMath.tilesAt(zoom, bounds) }
                .orEmpty()
                .groupBy { TerrainTileMath.ancestorAt(MapterhornEndpoints.REGIONAL_ARCHIVE_ZOOM, it) }
        val total = totalTiles.toInt()

        var stored: StoredTiles? = null
        var attempt = 0
        while (stored == null) {
            try {
                stored = fetchAll(globalTiles, regionalTilesByArchive, total) { emit(it) }
            } catch (e: CancellationException) {
                // A cancelled download leaves no partial region behind, and nothing may be emitted after cancellation.
                store.deleteAll()
                throw e
            } catch (_: ArchiveChangedException) {
                // Tiles already stored may come from the replaced build, so start over from nothing.
                store.deleteAll()
                if (attempt++ >= ARCHIVE_CHANGE_RETRIES) {
                    emit(TerrainDownloadState.Failed(TerrainDownloadFailure.IO_ERROR))
                    return@flow
                }
            } catch (_: Exception) {
                store.deleteAll()
                emit(TerrainDownloadState.Failed(TerrainDownloadFailure.IO_ERROR))
                return@flow
            }
        }

        // From what was stored, not from which archives existed: an archive with no coverage here stores nothing.
        emit(
            TerrainDownloadState.Complete(
                tileCount = stored.global + stored.regional,
                byteSize = store.sizeBytes(),
                hasRegionalDetail = stored.regional > 0,
                archives = stored.archives,
            ),
        )
    }

    private data class StoredTiles(val global: Long, val regional: Long, val archives: Map<String, String?>)

    private suspend fun fetchAll(
        globalTiles: List<TileIndex>,
        regionalTilesByArchive: Map<TileIndex, List<TileIndex>>,
        total: Int,
        onProgress: suspend (TerrainDownloadState.InProgress) -> Unit,
    ): StoredTiles {
        val global =
            fetchInto(MapterhornEndpoints.GLOBAL_PMTILES_URL, TerrainSource.GLOBAL, globalTiles, 0, total, onProgress)
                ?: error("Global terrain archive not found")
        var processed = global.processed
        var regionalStored = 0L
        val archives = mutableMapOf<String, String?>()
        if (global.stored > 0) archives[MapterhornEndpoints.GLOBAL_PMTILES_URL] = global.version
        for ((archiveTile, tiles) in regionalTilesByArchive) {
            val url = MapterhornEndpoints.regionalArchiveUrl(archiveTile)
            val regional = fetchInto(url, TerrainSource.REGIONAL, tiles, processed, total, onProgress)
            if (regional == null) {
                processed += tiles.size
                onProgress(TerrainDownloadState.InProgress(processed, total))
            } else {
                processed = regional.processed
                regionalStored += regional.stored
                if (regional.stored > 0) archives[url] = regional.version
            }
        }
        return StoredTiles(global = global.stored, regional = regionalStored, archives = archives)
    }

    /**
     * [processed] drives [TerrainDownloadState.InProgress] (every tile the fetch loop reaches, whether or not the
     * archive actually had it); [stored] drives [TerrainDownloadState.Complete.tileCount] (only tiles [store]'s
     * writeTile actually persisted). Sparse archive coverage — [TerrainTileFetcher.fetchTile] legitimately returning
     * `null` for a tile the source archive doesn't have — is documented as normal, not an error, so it must not inflate
     * the count the UI reports as "downloaded".
     */
    private data class FetchResult(val processed: Int, val stored: Long, val version: String?)

    private suspend fun fetchInto(
        pmtilesUrl: String,
        source: TerrainSource,
        tiles: List<TileIndex>,
        alreadyProcessed: Int,
        totalTiles: Int,
        onProgress: suspend (TerrainDownloadState.InProgress) -> Unit,
    ): FetchResult? {
        var processed = alreadyProcessed
        var stored = 0L
        val opened = openArchive(pmtilesUrl) ?: return null
        val version = opened.use { archive ->
            for (tile in tiles) {
                archive.fetchTile(tile.zoom, tile.x, tile.y)?.let { bytes ->
                    store.writeTile(source, tile, bytes)
                    stored++
                }
                processed++
                if (processed % PROGRESS_STRIDE == 0 || processed == totalTiles) {
                    onProgress(TerrainDownloadState.InProgress(processed, totalTiles))
                }
            }
            archive.version
        }
        return FetchResult(processed, stored, version)
    }

    companion object {
        /** See the base offline layer's own extractor for why this is far below iOS's 600k-tile-scale caps. */
        const val MAX_TILES = 3_000
        private const val PROGRESS_STRIDE = 10
        private const val ARCHIVE_CHANGE_RETRIES = 1

        private fun remoteArchive(pmtilesUrl: String): TileArchive? {
            val fetcher = openTerrainTileFetcher(pmtilesUrl) ?: return null
            return object : TileArchive {
                override val version: String?
                    get() = fetcher.version

                override fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray? = fetcher.fetchTile(zoom, x, y)

                override fun close() = fetcher.close()
            }
        }
    }
}
