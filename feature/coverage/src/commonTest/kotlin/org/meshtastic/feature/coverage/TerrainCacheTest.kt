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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.feature.map.terrain.ElevationTile
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TerrainCacheTest {

    private val tile = ElevationTile(1, 1, floatArrayOf(5f))

    @Test
    fun aFetchCanceledUnderAStillActiveCallerIsStartedAgain() = runTest {
        val cache = TerrainCache()
        val owner = CoroutineScope(Job())
        val ownerFetch = owner.async { awaitCancellation() }

        // The owner starts the fetch, then a second caller joins it rather than starting its own.
        val first = launch { runCatching { cache.getOrFetch(KEY) { ownerFetch } } }
        runCurrent()
        val second = async { cache.getOrFetch(KEY) { CompletableDeferred(tile) } }
        runCurrent()

        owner.cancel()

        assertSame(tile, second.await())
        first.join()
        assertTrue(ownerFetch.isCancelled)
    }

    @Test
    fun aCallerWhoseOwnFetchIsCanceledStopsWaiting() = runTest {
        val cache = TerrainCache()
        val owner = CoroutineScope(Job())
        val outcome = async { runCatching { cache.getOrFetch(KEY) { owner.async { awaitCancellation() } } } }
        runCurrent()

        owner.cancel()

        assertTrue(outcome.await().isFailure)
    }

    private companion object {
        const val KEY = 42L
    }
}
