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

import io.ktor.client.HttpClient
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
import org.meshtastic.feature.map.terrain.TerrainTileMath
import org.meshtastic.feature.map.terrain.TerrainTileStore
import org.meshtastic.feature.map.terrain.decodeTerrariumTile
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Real elevation from the same Mapterhorn terrain the map already uses for hillshade and contours.
 *
 * Common, not desktop-only: tile bytes arrive over ktor and `decodeTerrariumTile` is already expect/actual with android
 * and jvm actuals. The native actual of the decoder throws by design (see its doc comment in feature:map-terrain),
 * which is the same trade that module already makes.
 *
 * This is what makes a coverage plot mean anything: over flat synthetic ground it is a bullseye and proves nothing.
 * Against real terrain the prediction has to show ridges shadowing valleys, which is the entire reason the planner
 * exists.
 *
 * Decoded tiles are shared process-wide and, given a [store], kept on disk between launches.
 */
class MapterhornElevation(
    /** The area the caller will sample, which is what [prefetch] warms when given no box of its own. */
    private val bounds: GeoBounds? = null,
    /** Where to keep tiles between launches. Without one, every launch re-downloads its terrain. */
    private val store: TerrainTileStore? = null,
    zoom: Int = DEFAULT_ZOOM,
    http: HttpClient? = null,
) : ElevationSource,
    AutoCloseable {

    private val zoom = zoomFitting(zoom.coerceAtMost(MapterhornEndpoints.TILES_MAX_ZOOM), bounds)

    // Shared, and never closed here: a caller's client is theirs, and the shared one outlives us.
    private val tiles = MapterhornTiles(http ?: SharedTerrain.http, store)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Decoded terrain outlives this instance - see TerrainCache. Memoised after the first suspend
    // acquisition; two callers racing here both get the same cache, so the race is harmless.
    @Volatile private var cache: TerrainCache? = null

    /** The zoom actually in use, for reporting. */
    val zoomLevel: Int
        get() = zoom

    private suspend fun cache(): TerrainCache = cache ?: SharedTerrain.forArchive(ARCHIVE, zoom).also { cache = it }

    override suspend fun elevationMeters(latitude: Double, longitude: Double): Double {
        val tile = TerrainTileMath.tileAt(zoom, latitude, longitude)
        val key = tileKey(tile.x, tile.y)
        val snapshot = cache().snapshot()
        // Ocean, or outside the endpoint's data, decodes to null — sea level, as the planner assumes too.
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

    /** Warm every tile a sweep of [site] will touch. */
    suspend fun prefetch(site: Site): Int = prefetch(site.coverageBounds())

    /**
     * Warm every tile inside [area] before anything asks for one, many at a time.
     *
     * Optional — sampling fetches on demand without it — and returns how many tiles it warmed. Worth calling anyway:
     * on-demand misses arrive one radial at a time, while this issues the whole disc at once and lets the requests
     * overlap.
     */
    suspend fun prefetch(area: GeoBounds? = bounds): Int {
        val cache = cache()
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

        val warmed = coroutineScope {
            wanted.map { request -> async { request.key to decode(request.x, request.y) } }.awaitAll()
        }
        cache.publish(warmed)
        return warmed.size
    }

    /** How many tiles are decoded and resident — useful when reporting a run. */
    val tilesResident: Int
        get() = cache?.size ?: 0

    private suspend fun awaitTile(key: Long, x: Int, y: Int): ElevationTile? =
        cache().getOrFetch(key) { scope.async { decode(x, y) } }

    private suspend fun decode(x: Int, y: Int): ElevationTile? =
        tiles.bytes(zoom, x, y)?.let { runCatching { decodeTerrariumTile(it) }.getOrNull() }

    override fun close() = scope.cancel()

    /** One tile a prefetch still has to fetch. */
    private class TileRequest(val key: Long, val x: Int, val y: Int)

    companion object {
        /** Below this one tile spans a continent; the guard never needs to go further. */
        const val MIN_ZOOM = 6

        /**
         * ~37 m per pixel at mid latitudes, which matches the 50 m terrain profile step.
         *
         * Measured against z13 on the Seattle demo: identical reachable fraction and max range, and an rx range of
         * -147.8..-41.3 against -148.2..-41.3 — for 72 tiles instead of 256. The extra detail lands below the step the
         * model samples at. The endpoint serves up to [MapterhornEndpoints.TILES_MAX_ZOOM] for anyone who wants it.
         */
        const val DEFAULT_ZOOM = 12
    }
}

/** One endpoint now, but the cache is keyed by it so a second source would not collide. */
private const val ARCHIVE = "tiles.mapterhorn.com"

/** Pack a tile's coordinates into one cache key. */
private fun tileKey(x: Int, y: Int): Long = (x.toLong() shl TILE_KEY_SHIFT) or y.toLong()

/**
 * The lat/lon box a sweep of this site samples.
 *
 * Pass it to [MapterhornElevation] so a prefetch knows what ground to warm.
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

private const val TILE_KEY_SHIFT = 32
private const val FULL_CIRCLE_DEG = 360.0
private const val STRAIGHT_ANGLE_DEG = 180.0
private const val HALF = 2.0
private const val KM_PER_DEG_LAT = 111.32
private const val DEG_TO_RAD = 0.017453292519943295

/**
 * The deepest zoom at or below [wanted] whose tiles for [bounds] still fit the shared cache.
 *
 * Tiles scale with the square of the sampled radius and with 1/cos(latitude), so a fixed zoom is only ever right for
 * one area. At z12 a 30 km disc is ~120 tiles at mid latitudes but ~360 above 65°N, and the radius is a free-text field
 * — 70 km asks for 500 to 1800. Past the cache's capacity the failure is not graceful: eviction is insertion order, so
 * the sweep evicts the very tiles it is about to read and re-decodes the whole disc on every pass. Measured once at
 * ~1800 tiles: 133 MB downloaded and seconds per estimate instead of hundreds of milliseconds.
 *
 * Dropping a zoom quarters the tile count, so this converges immediately and leaves the common case untouched — a 30 km
 * disc at mid latitudes still samples at z12.
 */
internal fun zoomFitting(wanted: Int, bounds: GeoBounds?): Int {
    if (bounds == null) return wanted
    var zoom = wanted
    while (zoom > MapterhornElevation.MIN_ZOOM && tilesSpanning(bounds, zoom) > TerrainCache.DEFAULT_CAPACITY) {
        zoom--
    }
    return zoom
}

/** How many tiles [bounds] covers at [zoom]. */
internal fun tilesSpanning(bounds: GeoBounds, zoom: Int): Int {
    val nw = TerrainTileMath.tileAt(zoom, bounds.north, bounds.west)
    val se = TerrainTileMath.tileAt(zoom, bounds.south, bounds.east)
    return (se.x - nw.x + 1) * (se.y - nw.y + 1)
}
