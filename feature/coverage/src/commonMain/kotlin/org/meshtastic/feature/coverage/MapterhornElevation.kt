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
package org.meshtastic.feature.coverage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import org.meshtastic.feature.map.terrain.ElevationTile
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainTileFetcher
import org.meshtastic.feature.map.terrain.TerrainTileMath
import org.meshtastic.feature.map.terrain.decodeTerrariumTile
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Real elevation from the same Mapterhorn archives the map already uses for hillshade and contours.
 *
 * Common, not desktop-only: `TerrainTileFetcher` and `decodeTerrariumTile` are already expect/actual with android and
 * jvm actuals, so nothing here is platform-specific. The native actual of the decoder throws by design (see its doc
 * comment in feature:map-terrain), which is the same trade that module already makes.
 *
 * This is what makes the demo meaningful: over flat synthetic ground a coverage plot is a bullseye and proves nothing.
 * Against real terrain the prediction has to show ridges shadowing valleys, which is the entire reason the planner
 * exists.
 *
 * Tiles are decoded once and cached in memory — a radial sweep asks for thousands of points that land in a handful of
 * tiles.
 */
class MapterhornElevation(
    /**
     * The area the caller will sample. When it fits inside a single z6 tile, Mapterhorn has a regional archive for it,
     * which carries far finer terrain than the global one — z13 is ~19 m per pixel against the global archive's ~75 m
     * at z11.
     */
    private val bounds: GeoBounds? = null,
    zoom: Int? = null,
) : ElevationSource,
    AutoCloseable {

    private val archiveUrl = bounds?.let { MapterhornEndpoints.regionalUrlFor(it) }

    /**
     * Regional where one is published, global otherwise.
     *
     * Not every z6 tile has a regional archive — most 404 — so a missing one is a normal outcome rather than an error,
     * and opening the reader is where that shows up.
     */
    private val regional: TerrainTileFetcher? =
        archiveUrl?.let { url -> runCatching { TerrainTileFetcher(url) }.getOrNull() }

    private val url = if (regional != null) archiveUrl!! else MapterhornEndpoints.GLOBAL_PMTILES_URL

    /**
     * Opened only when a tile actually has to be downloaded.
     *
     * Opening one reads the archive header over the network — measured at 719 ms — and an estimate whose terrain is
     * already in [SharedTerrain] needs no reader at all. Eager, that open was more than half the cost of a repeat
     * estimate.
     */
    private val lazyFetcher = lazy { regional ?: TerrainTileFetcher(url) }

    private val fetcher: TerrainTileFetcher
        get() = lazyFetcher.value

    /** True when this is drawing on a regional archive rather than the coarse global one. */
    val isRegional: Boolean = regional != null

    private val zoom = zoom ?: if (isRegional) DEFAULT_REGIONAL_ZOOM else DEFAULT_GLOBAL_ZOOM

    /** The zoom actually in use, for reporting. */
    val zoomLevel: Int
        get() = zoom

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Decoded terrain outlives this instance - see TerrainCache. Memoised after the first suspend
    // acquisition; two callers racing here both get the same cache, so the race is harmless.
    @Volatile private var tiles: TerrainCache? = null

    private suspend fun tiles(): TerrainCache = tiles ?: SharedTerrain.forArchive(url, zoom).also { tiles = it }

    override suspend fun elevationMeters(latitude: Double, longitude: Double): Double {
        val tile = TerrainTileMath.tileAt(zoom, latitude, longitude)
        val key = tileKey(tile.x, tile.y)
        val snapshot = tiles().snapshot()
        // Ocean, or outside the archive, decodes to null — sea level, as the planner assumes too.
        val decoded = (if (snapshot.containsKey(key)) snapshot[key] else awaitTile(key, tile.x, tile.y)) ?: return 0.0

        // Fractional position of this coordinate within its tile.
        val n = 1 shl zoom
        val fx = (longitude + STRAIGHT_ANGLE_DEG) / FULL_CIRCLE_DEG * n - tile.x
        val latRad = latitude * PI / STRAIGHT_ANGLE_DEG
        val fy = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / HALF * n - tile.y
        val px = (fx * decoded.width).toInt().coerceIn(0, decoded.width - 1)
        val py = (fy * decoded.height).toInt().coerceIn(0, decoded.height - 1)
        return decoded.elevationAt(px, py).toDouble()
    }

    /**
     * Warm every tile inside [bounds] before anything asks for one, several at a time.
     *
     * The archive is read over HTTP range requests through a single seekable channel, so one reader serves tiles
     * strictly one after another — measured at ~300 ms each, which for a 25 km disc (~60 tiles) *was* the entire cost
     * of a sweep: prediction count barely moved the total. A pool of readers is the fix, and it is a pool rather than
     * concurrent calls on one reader because the channel carries a position that concurrent seeks would corrupt.
     *
     * Optional — sampling fetches on demand without it — and returns how many tiles it warmed.
     */
    suspend fun prefetch(site: Site, concurrency: Int = DEFAULT_PREFETCH_READERS): Int =
        prefetch(site.coverageBounds(), concurrency)

    /** As [prefetch], for a box that is not a site's coverage disc. */
    suspend fun prefetch(area: GeoBounds? = bounds, concurrency: Int = DEFAULT_PREFETCH_READERS): Int {
        val cache = tiles()
        val resident = cache.snapshot()
        val wanted =
            area
                ?.let { box ->
                    val nw = TerrainTileMath.tileAt(zoom, box.north, box.west)
                    val se = TerrainTileMath.tileAt(zoom, box.south, box.east)
                    buildList {
                        for (x in nw.x..se.x) {
                            for (y in nw.y..se.y) {
                                val key = tileKey(x, y)
                                if (!resident.containsKey(key)) add(TileRequest(key, x, y))
                            }
                        }
                    }
                }
                .orEmpty()
        if (wanted.isEmpty()) return 0

        // Opening a reader costs a header round trip of its own, so the pool opens concurrently:
        // built serially, the pool's own setup grew linearly with its size and ate the speed-up.
        val extras = coroutineScope {
            List((concurrency - 1).coerceAtLeast(0)) {
                async(Dispatchers.Default) { runCatching { TerrainTileFetcher(url) }.getOrNull() }
            }
                .awaitAll()
        }
        val readers = listOf(fetcher) + extras.filterNotNull()
        try {
            val warmed = coroutineScope {
                wanted
                    .chunked((wanted.size + readers.size - 1) / readers.size)
                    .mapIndexed { i, chunk ->
                        val reader = readers[i % readers.size]
                        async(Dispatchers.Default) {
                            chunk.map { req ->
                                val bytes = reader.fetchTile(zoom, req.x, req.y)
                                req.key to bytes?.let { runCatching { decodeTerrariumTile(it) }.getOrNull() }
                            }
                        }
                    }
                    .awaitAll()
                    .flatten()
            }
            cache.publish(warmed)
            return warmed.size
        } finally {
            for (extra in extras) extra?.close()
        }
    }

    /** Fetch and decode one tile, sharing the work with anyone else who wants it. */
    private suspend fun awaitTile(key: Long, x: Int, y: Int): ElevationTile? = tiles().getOrFetch(key) {
        // Dispatchers.IO is JVM/Android-only; Default keeps this source common.
        scope.async {
            val bytes = fetcher.fetchTile(zoom, x, y)
            bytes?.let { runCatching { decodeTerrariumTile(it) }.getOrNull() }
        }
    }

    /** How many tiles are decoded and resident, useful when reporting a run. */
    val tilesResident: Int
        get() = tiles?.size ?: 0

    override fun close() {
        scope.cancel()
        // Never force the lazy open just to close it; and when regional won, it *is* the fetcher.
        if (lazyFetcher.isInitialized()) lazyFetcher.value.close() else regional?.close()
    }

    companion object {
        /**
         * The global archive's own maximum, ~37 m/px at mid latitudes. Asking for more returns nothing; asking for less
         * throws away detail that costs no extra requests, because a coverage disc spans few enough tiles either way.
         */
        internal const val DEFAULT_GLOBAL_ZOOM = MapterhornEndpoints.GLOBAL_MAX_ZOOM

        /**
         * Regional archives run z13–18. z13 is ~19 m/px at mid latitudes — four times the detail of the global archive,
         * and well below the 100 m profile step, so the profile stops being the thing that limits accuracy. Higher
         * zooms multiply the tiles fetched without the model resolving much more.
         */
        internal const val DEFAULT_REGIONAL_ZOOM = 13

        /**
         * Readers opened for a prefetch. Measured on a 72-tile disc: 8 readers took 5.4 s, 16 took 4.9 s and 32 took
         * 6.4 s, the last losing to its own setup. Past ~16 the archive's own round trips, not our concurrency, are the
         * floor.
         */
        const val DEFAULT_PREFETCH_READERS = 16
    }

    /** One tile a prefetch still has to fetch. */
    private class TileRequest(val key: Long, val x: Int, val y: Int)
}

/**
 * The lat/lon box a sweep of this site samples.
 *
 * Pass it to [MapterhornElevation] to get the regional archive where one covers the site.
 */
internal fun Site.coverageBounds(): GeoBounds {
    val latSpan = radiusKm / KM_PER_DEG_LAT
    val lonSpan = latSpan / cos(latitude * DEG_TO_RAD)
    return GeoBounds(
        south = latitude - latSpan,
        west = longitude - lonSpan,
        north = latitude + latSpan,
        east = longitude + lonSpan,
    )
}

private const val KM_PER_DEG_LAT = 111.32
private const val DEG_TO_RAD = 0.017453292519943295

/** Pack a tile's coordinates into one cache key. */
private fun tileKey(x: Int, y: Int): Long = (x.toLong() shl TILE_KEY_SHIFT) or y.toLong()

private const val TILE_KEY_SHIFT = 32
private const val FULL_CIRCLE_DEG = 360.0
private const val STRAIGHT_ANGLE_DEG = 180.0
private const val HALF = 2.0
