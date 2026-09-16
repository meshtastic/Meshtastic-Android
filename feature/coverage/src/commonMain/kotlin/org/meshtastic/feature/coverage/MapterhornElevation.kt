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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.meshtastic.feature.map.terrain.ElevationTile
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainTileFetcher
import org.meshtastic.feature.map.terrain.TerrainTileMath
import org.meshtastic.feature.map.terrain.decodeTerrariumTile
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * Real elevation from the same Mapterhorn archives the map already uses for hillshade and contours.
 *
 * Common, not desktop-only: `TerrainTileFetcher` and `decodeTerrariumTile` are already
 * expect/actual with android and jvm actuals, so nothing here is platform-specific. The native
 * actual of the decoder throws by design (see its doc comment in feature:map-terrain), which is
 * the same trade that module already makes.
 *
 * This is what makes the demo meaningful: over flat synthetic ground a coverage plot is a bullseye
 * and proves nothing. Against real terrain the prediction has to show ridges shadowing valleys,
 * which is the entire reason the planner exists.
 *
 * Tiles are decoded once and cached in memory — a radial sweep asks for thousands of points that
 * land in a handful of tiles.
 */
class MapterhornElevation(private val zoom: Int = DEFAULT_ZOOM) : ElevationSource, AutoCloseable {

    private val fetcher = TerrainTileFetcher(MapterhornEndpoints.GLOBAL_PMTILES_URL)
    private val cache = HashMap<Long, ElevationTile?>()
    private val lock = Mutex()

    override suspend fun elevationMeters(latitude: Double, longitude: Double): Double {
        val tile = TerrainTileMath.tileAt(zoom, latitude, longitude)
        val key = (tile.x.toLong() shl 32) or tile.y.toLong()
        val decoded = lock.withLock {
            if (cache.containsKey(key)) {
                cache[key]
            } else {
                        // Dispatchers.IO is JVM/Android-only; Default keeps this source common.
                val bytes = withContext(Dispatchers.Default) { fetcher.fetchTile(zoom, tile.x, tile.y) }
                val t = bytes?.let { runCatching { decodeTerrariumTile(it) }.getOrNull() }
                cache[key] = t
                t
            }
        } ?: return 0.0 // ocean, or outside coverage — sea level, as the planner also assumes

        // Fractional position of this coordinate within its tile.
        val n = 1 shl zoom
        val fx = (longitude + 180.0) / 360.0 * n - tile.x
        val latRad = latitude * PI / 180.0
        val fy = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n - tile.y
        val px = (fx * decoded.width).toInt().coerceIn(0, decoded.width - 1)
        val py = (fy * decoded.height).toInt().coerceIn(0, decoded.height - 1)
        return decoded.elevationAt(px, py).toDouble()
    }

    /** How many distinct tiles the sweep actually touched — useful when reporting a run. */
    val tilesFetched: Int get() = cache.size

    override fun close() = fetcher.close()

    private companion object {
        /**
         * Mapterhorn's global archive tops out at z12 ([MapterhornEndpoints.GLOBAL_MAX_ZOOM]);
         * z11 is ~75 m/px at mid latitudes, comparable to the planner's 90 m standard mode.
         */
        const val DEFAULT_ZOOM = 11
    }
}
