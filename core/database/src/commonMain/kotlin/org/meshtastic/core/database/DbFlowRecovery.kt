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
package org.meshtastic.core.database

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

private const val INITIAL_DB_RETRY_DELAY_MS = 1_000L
private const val MAX_DB_RETRY_DELAY_MS = 30_000L
private const val MAX_DB_RETRY_BACKOFF_SHIFT = 5

/**
 * Restarts a database-backed Flow after the Room pool wedge behind #6608 — an acquire timeout or a stall — with capped
 * exponential backoff. The backoff resets after a successful emission; every other failure propagates.
 *
 * [DatabaseManager.observeCurrentDb] recovers a wedged pool in place, but its per-collector and per-window budgets can
 * exhaust while a leaked-permit storm is still active; a `stateIn(..., SharingStarted.Eagerly, ...)` upstream that then
 * throws is dead for the process lifetime. Retrying re-enters recovery with a fresh collector once the manager's rate
 * window reopens.
 *
 * Deliberately narrower than [DatabaseManager.isDbClosedException]: a closed pool is not retried here.
 * `observeCurrentDb` re-latches on the replacement pool by itself, so retrying adds nothing — and it would keep a flow
 * alive that used to terminate, turning a dead collector into a backoff loop that outlives its collecting scope.
 */
fun <T> Flow<T>.retryOnDbPoolFailure(label: String): Flow<T> = flow {
    // Per-collection state: each collector backs off independently, and a plain var is safe because a Flow is
    // collected by one coroutine at a time.
    var consecutiveFailures = 0
    emitAll(
        onEach { consecutiveFailures = 0 }
            .retryWhen { cause, _ ->
                val recoverable =
                    cause is DbFlowStalledException ||
                        (cause is Exception && DatabaseManager.isDbPoolAcquireTimeoutException(cause))
                if (!recoverable) return@retryWhen false
                consecutiveFailures += 1
                val delayMs =
                    (INITIAL_DB_RETRY_DELAY_MS shl (consecutiveFailures - 1).coerceAtMost(MAX_DB_RETRY_BACKOFF_SHIFT))
                        .coerceAtMost(MAX_DB_RETRY_DELAY_MS)
                Logger.w(cause) { "DB flow '$label' failed with a recoverable pool error; retrying in ${delayMs}ms" }
                delay(delayMs)
                true
            },
    )
}

/** Signals that a database-backed Flow never produced its first value, i.e. the Room pool is not serving queries. */
class DbFlowStalledException(message: String) : IllegalStateException(message)
