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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.feature.map.terrain.ElevationTile
import kotlin.concurrent.Volatile

/**
 * Decoded terrain for one archive at one zoom, shared by every [MapterhornElevation] that reads it.
 *
 * A tile is immutable data keyed by archive, zoom and position, so there is nothing to scope it to a single instance —
 * and scoping it to one was measurably wrong. The desktop planner's composable is disposed when its sheet closes, so a
 * second estimate paid the full terrain download again for tiles it had already decoded seconds earlier: 8.8 s where
 * the sweep itself is 0.2 s.
 *
 * Bounded, because a decoded tile is ~256 KB and a 25 km disc is ~70 of them. Eviction is insertion order rather than
 * least-recently-used: a sweep's working set is one contiguous disc, so the oldest entries are from a disc the user has
 * already moved away from, and tracking access order would cost a write on the hot read path for no better answer.
 */
internal class TerrainCache(private val capacity: Int = DEFAULT_CAPACITY) {

    private val lock = Mutex()

    // The in-flight fetch, not the result, so concurrent radials landing on the same tile await one
    // download instead of starting one each.
    private val inFlight = LinkedHashMap<Long, Deferred<ElevationTile?>>()

    // A read-only snapshot of what has decoded. Sampling is overwhelmingly hits, and taking the
    // mutex for each would serialise a parallel sweep on this one lock.
    @Volatile private var resolved: Map<Long, ElevationTile?> = emptyMap()

    /** How many tiles are decoded and resident. */
    val size: Int
        get() = resolved.size

    /**
     * What is resident right now.
     *
     * Handed out as one map rather than as `contains` plus `get`, because between two calls an eviction could drop the
     * key and the second would answer null — which a sampler reads as ocean.
     */
    fun snapshot(): Map<Long, ElevationTile?> = resolved

    /**
     * The decoded tile, starting [produce] only if nobody else already has.
     *
     * [produce] returns a [Deferred] rather than the tile so that the caller owns the scope the fetch runs in — the
     * cache outlives any one [MapterhornElevation] and must not hold its scope.
     */
    suspend fun getOrFetch(key: Long, produce: () -> Deferred<ElevationTile?>): ElevationTile? {
        // A cancelled fetch must not be handed to the next caller: the scope producing it belongs to
        // one MapterhornElevation, and cancelling that (the planner sheet being dismissed mid-run)
        // would otherwise leave a dead Deferred that fails every later sweep touching this tile.
        val pending =
            lock.withLock { inFlight[key]?.takeUnless { it.isCancelled } ?: produce().also { inFlight[key] = it } }
        val tile = pending.await()
        publish(listOf(key to tile))
        return tile
    }

    /** Record tiles fetched in bulk, as a prefetch does. */
    suspend fun publish(entries: List<Pair<Long, ElevationTile?>>) = lock.withLock {
        val merged = LinkedHashMap(resolved)
        for ((key, tile) in entries) {
            merged.remove(key)
            merged[key] = tile
        }
        while (merged.size > capacity) {
            val oldest = merged.keys.first()
            merged.remove(oldest)
            inFlight.remove(oldest)
        }
        resolved = merged
    }

    private companion object {
        /**
         * A 25 km disc is ~70 tiles at z12 and a decoded tile is ~256 KB, so this holds a working set with room to pan.
         * Asking for a deeper zoom will evict — 256 tiles at z13 — which the disk cache makes a decode rather than a
         * download, but it is why z12 is the default.
         */
        const val DEFAULT_CAPACITY = 192
    }
}

/**
 * The shared caches, one per archive and zoom.
 *
 * Process-wide on purpose: terrain does not belong to a screen. The map keeps at most a couple of archives open, so
 * this never needs eviction of its own.
 */
internal object SharedTerrain {

    private val caches = HashMap<String, TerrainCache>()
    private val lock = Mutex()

    suspend fun forArchive(url: String, zoom: Int): TerrainCache =
        lock.withLock { caches.getOrPut("$url@$zoom") { TerrainCache() } }

    /**
     * One HTTP client for the process.
     *
     * Building one per estimate cost seconds — the engine brings up its own selector threads — for a client that is
     * stateless once connected. Never closed: it is shared, and an estimate that closed it would break the next one.
     */
    val http: HttpClient by lazy { HttpClient() }
}
