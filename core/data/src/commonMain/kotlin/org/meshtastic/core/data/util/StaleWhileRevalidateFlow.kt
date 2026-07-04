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
package org.meshtastic.core.data.util

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.util.safeCatching
import kotlin.coroutines.CoroutineContext

private const val DEFAULT_NETWORK_TIMEOUT_MS = 5_000L

/**
 * Creates a cold Flow that implements the stale-while-revalidate caching pattern:
 * 1. Load and emit cached data immediately (UI never waits for network).
 * 2. If [shouldFetch] returns true, attempt a network refresh bounded by [networkTimeoutMs].
 * 3. Reload from cache and emit again if the data changed.
 *
 * The [fetch] lambda is expected to write results to the local cache as a side-effect; [loadFromCache] is called again
 * after fetch to pick up the fresh data.
 *
 * All work runs on [context] (typically an IO dispatcher).
 *
 * @param T The domain model type emitted to collectors.
 * @param loadFromCache Loads the current best-available data from local storage.
 * @param shouldFetch Decides whether a network refresh is needed based on the cached value (suspendable).
 * @param fetch Performs the network call and persists results locally (side-effect only).
 * @param context The coroutine context for cache/network operations.
 * @param networkTimeoutMs Maximum time to wait for [fetch] before falling back to cached data.
 * @param tag Logging tag for diagnostics.
 */
internal fun <T : Any> staleWhileRevalidateFlow(
    loadFromCache: suspend () -> T?,
    shouldFetch: suspend (T?) -> Boolean,
    fetch: suspend () -> Unit,
    context: CoroutineContext,
    networkTimeoutMs: Long = DEFAULT_NETWORK_TIMEOUT_MS,
    tag: String = "StaleWhileRevalidate",
): Flow<T?> = flow {
    val cached = loadFromCache()
    emit(cached)

    if (!shouldFetch(cached)) return@flow

    val completed =
        withTimeoutOrNull(networkTimeoutMs) {
            safeCatching { fetch() }.onFailure { e -> Logger.w(e) { "$tag: network fetch failed" } }
        }
    if (completed == null) {
        Logger.w { "$tag: network fetch timed out after ${networkTimeoutMs}ms" }
    }

    val fresh = loadFromCache()
    if (fresh != cached) {
        emit(fresh)
    }
}
    .flowOn(context)
