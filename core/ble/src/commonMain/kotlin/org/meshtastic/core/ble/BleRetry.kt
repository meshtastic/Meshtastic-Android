/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.ble

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

/** Cap on the per-attempt backoff to prevent unbounded growth. */
private const val MAX_RETRY_DELAY_MS = 2_000L

/** Multiplicative growth factor between attempts (delay doubles each time). */
private const val BACKOFF_FACTOR = 2.0

/**
 * Retries a BLE operation with bounded exponential backoff and jitter.
 *
 * Each retry waits `delayMs * 2^(attempt-1)`, capped at [MAX_RETRY_DELAY_MS], with a random ±25% jitter applied to
 * avoid synchronised retry storms when multiple operations fail in lockstep (e.g. a TX/RX pair both failing the same
 * `STATUS_GATT_BUSY` window).
 *
 * @param count Total attempt count (default 3).
 * @param delayMs Initial delay before the first retry. Subsequent delays grow exponentially.
 * @param tag Tag for log prefixes.
 * @param block The operation to perform.
 * @return The result of the operation.
 * @throws Exception If the operation fails after all attempts. [CancellationException] is always re-thrown immediately.
 */
@Suppress("MagicNumber")
suspend fun <T> retryBleOperation(
    count: Int = 3,
    delayMs: Long = 250L,
    tag: String = "BLE",
    block: suspend () -> T,
): T {
    var currentAttempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            currentAttempt++
            if (currentAttempt >= count) {
                Logger.w(e) { "[$tag] BLE operation failed after $count attempts, giving up" }
                throw e
            }
            val backoffMs = (delayMs * BACKOFF_FACTOR.pow(currentAttempt - 1)).toLong().coerceAtMost(MAX_RETRY_DELAY_MS)
            val jitterRange = (backoffMs / 4).coerceAtLeast(1L)
            val jitter = Random.nextLong(-jitterRange, jitterRange + 1)
            val sleepMs = (backoffMs + jitter).coerceAtLeast(0L)
            Logger.w(e) { "[$tag] BLE operation failed (attempt $currentAttempt/$count), retrying in ${sleepMs}ms..." }
            delay(sleepMs)
        }
    }
}
