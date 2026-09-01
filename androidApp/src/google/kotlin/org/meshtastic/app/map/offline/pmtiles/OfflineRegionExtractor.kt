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

import ch.poole.geo.pmtiles.Constants
import ch.poole.geo.pmtiles.HttpUrlConnectionChannel
import ch.poole.geo.pmtiles.Reader
import co.touchlab.kermit.Logger
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.meshtastic.core.common.util.ioDispatcher
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.uuid.Uuid

/** Public alongside [OfflineRegion] — see that class's doc comment for why these two aren't `internal`. */
sealed interface OfflineDownloadState {
    data class InProgress(val completed: Int, val total: Int) : OfflineDownloadState

    data class Complete(val region: OfflineRegion) : OfflineDownloadState

    data class Failed(val reason: OfflineDownloadFailure) : OfflineDownloadState
}

enum class OfflineDownloadFailure {
    /** None of the last [PmTilesDailyBuild]-searched days had a published build. */
    NO_BUILD_AVAILABLE,

    /** More tiles than [OfflineRegionExtractor.MAX_TILES_PER_REGION] — likely too large an area or too deep a zoom. */
    TILE_LIMIT_EXCEEDED,

    /** Already at [OfflineRegionExtractor.MAX_REGIONS] downloaded regions. */
    REGION_LIMIT_EXCEEDED,

    /** Existing regions already occupy [OfflineRegionExtractor.MAX_TOTAL_BYTES]. */
    STORAGE_LIMIT_EXCEEDED,

    /** A network or disk failure partway through; nothing partial is kept. */
    IO_ERROR,
}

/**
 * Extracts an offline vector region: enumerates the tiles [bounds]/[zoomRange] cover, fetches each individually from
 * the current Protomaps daily build via [ch.poole.geo.pmtiles.Reader] (which range-requests only that tile's own bytes,
 * not the surrounding multi-gigabyte file), and writes them into a local `.mbtiles` archive.
 *
 * Deliberately simpler than the sibling iOS app's extractor, which coalesces adjacent tiles' byte ranges into batched
 * HTTP requests before downloading anything (`PMTilesExtractor.swift`) — this fetches one tile per request. That is the
 * reason [MAX_TILES_PER_REGION] here (2,000) is far below iOS's 600,000: at one round trip per tile, anything near that
 * many tiles would take an impractically long time on a phone connection. Coalescing requests to raise this cap is the
 * natural next step; see the module README.
 */
internal class OfflineRegionExtractor(private val store: OfflineRegionStore) {

    fun download(bounds: LatLngBounds, zoomRange: IntRange): Flow<OfflineDownloadState> = flow {
        val tiles = OfflineRegionTileSet.tiles(bounds, zoomRange)
        when {
            tiles.size > MAX_TILES_PER_REGION -> {
                emit(OfflineDownloadState.Failed(OfflineDownloadFailure.TILE_LIMIT_EXCEEDED))
                return@flow
            }

            store.list().size >= MAX_REGIONS -> {
                emit(OfflineDownloadState.Failed(OfflineDownloadFailure.REGION_LIMIT_EXCEEDED))
                return@flow
            }

            store.totalBytes() >= MAX_TOTAL_BYTES -> {
                emit(OfflineDownloadState.Failed(OfflineDownloadFailure.STORAGE_LIMIT_EXCEEDED))
                return@flow
            }
        }

        val url = PmTilesDailyBuild.resolveLatestUrl()
        if (url == null) {
            emit(OfflineDownloadState.Failed(OfflineDownloadFailure.NO_BUILD_AVAILABLE))
            return@flow
        }

        val id = Uuid.random().toString()
        val archiveFile = store.archiveFile(id)

        try {
            extractInto(archiveFile, url, tiles) { completed ->
                emit(OfflineDownloadState.InProgress(completed, tiles.size))
            }
        } catch (e: IOException) {
            LOG.w(e) { "Offline region extraction failed" }
            archiveFile.delete()
            emit(OfflineDownloadState.Failed(OfflineDownloadFailure.IO_ERROR))
            return@flow
        }

        val region =
            OfflineRegion(
                id = id,
                southLat = bounds.southwest.latitude,
                westLon = bounds.southwest.longitude,
                northLat = bounds.northeast.latitude,
                eastLon = bounds.northeast.longitude,
                minZoom = zoomRange.first,
                maxZoom = zoomRange.last,
                tileCount = tiles.size.toLong(),
                byteSize = archiveFile.length(),
                createdAtEpochSeconds = System.currentTimeMillis() / MILLIS_PER_SECOND,
            )
        store.add(region)
        emit(OfflineDownloadState.Complete(region))
    }
        .flowOn(ioDispatcher)

    private suspend fun extractInto(
        archiveFile: java.io.File,
        url: java.net.URL,
        tiles: List<TileIndex>,
        onProgress: suspend (completed: Int) -> Unit,
    ) {
        Reader(HttpUrlConnectionChannel(url)).use { reader ->
            OfflineVectorArchive.create(archiveFile, attribution = ATTRIBUTION).use { archive ->
                tiles.forEachIndexed { index, tile ->
                    val raw = reader.getTile(tile.zoom, tile.x, tile.y)
                    if (raw != null) {
                        val bytes = if (reader.tileCompression == Constants.COMPRESSION_GZIP) gunzip(raw) else raw
                        archive.writeTile(tile.zoom, tile.x, tile.y, bytes)
                    }
                    val completed = index + 1
                    if (completed % PROGRESS_STRIDE == 0 || completed == tiles.size) onProgress(completed)
                }
            }
        }
    }

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

    internal companion object {
        private val LOG = Logger.withTag("OfflineRegionExtractor")

        /** See the class doc: one HTTP round trip per tile makes iOS's 600,000-tile cap impractical here. */
        const val MAX_TILES_PER_REGION = 2_000
        const val MAX_REGIONS = 10
        const val MAX_TOTAL_BYTES = 300L * 1024 * 1024
        private const val PROGRESS_STRIDE = 10
        private const val MILLIS_PER_SECOND = 1_000L

        /** Both the Protomaps build and the MVT layers it packages (OpenStreetMap) require attribution. */
        const val ATTRIBUTION = "© OpenStreetMap contributors, © Protomaps"
    }
}
