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

/**
 * Retries a BLE operation a specified number of times with a delay between attempts.
 *
 * @param count The number of attempts to make.
 * @param delayMs The delay in milliseconds between attempts.
 * @param tag A tag for logging.
 * @param block The operation to perform.
 * @return The result of the operation.
 * @throws Exception if the operation fails after all attempts.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> retryBleOperation(
    count: Int = 3,
    delayMs: Long = 500L,
    tag: String = "BLE",
    block: suspend () -> T,
): T {
    var currentAttempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentAttempt++
            if (currentAttempt >= count) {
                Logger.w(e) { "[$tag] BLE operation failed after $count attempts, giving up" }
                throw e
            }
            Logger.w(e) {
                "[$tag] BLE operation failed (attempt $currentAttempt/$count), " + "retrying in ${delayMs}ms..."
            }
            delay(delayMs)
        }
    }
}
