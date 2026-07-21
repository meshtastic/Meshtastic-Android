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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.util.safeCatching
import kotlin.coroutines.CoroutineContext

/**
 * Runs [fetchAndPersist] as a single shared flight on a detached, app-lifetime scope: concurrent callers join the
 * in-flight refresh instead of starting their own, a caller that stops waiting (cancellation or [refresh]'s
 * `maxWaitMs`) can't abort it, and — critically — no lock a cache-read path can see is ever held across the network
 * call. api.meshtastic.org routinely takes 20-60s (and minutes during outages); holding a caller-visible mutex across
 * that request has wedged cached emissions, and the UI with them, before.
 *
 * [fetchAndPersist] performs the network call and writes results to the local cache as a side-effect; its failures are
 * caught and logged here, so callers observe refresh results only through the cache. The owner is expected to be an
 * app-lifetime singleton — the internal SupervisorJob is never cancelled.
 */
internal class SingleFlightRefresher(
    context: CoroutineContext,
    private val tag: String,
    private val fetchAndPersist: suspend () -> Unit,
) {

    /** Guards [inFlight] so concurrent callers share one refresh. */
    private val guard = Mutex()

    private var inFlight: Deferred<Unit>? = null

    private val scope = CoroutineScope(context + SupervisorJob())

    /**
     * Starts (or joins) the shared refresh. When [maxWaitMs] is set, the caller waits at most that long before falling
     * back to cached data; the refresh itself always runs to completion, bounded only by the HttpClient's own
     * timeout/retry policy.
     */
    suspend fun refresh(maxWaitMs: Long? = null) {
        val refresh =
            guard.withLock {
                inFlight?.takeIf { it.isActive }
                    ?: scope
                        .async {
                            safeCatching { fetchAndPersist() }
                                .onFailure { e -> Logger.w(e) { "$tag: network refresh failed" } }
                            Unit
                        }
                        .also { inFlight = it }
            }
        if (maxWaitMs == null) {
            refresh.join()
        } else if (withTimeoutOrNull(maxWaitMs) { refresh.join() } == null) {
            Logger.w { "$tag: refresh still in flight after ${maxWaitMs}ms; using cached data" }
        }
    }
}
