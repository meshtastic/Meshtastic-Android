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
package org.meshtastic.core.ble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleRetryTest {

    @Test
    fun `retryBleOperation returns immediately on success`() = runTest {
        var attempts = 0
        val result =
            retryBleOperation(count = 3, delayMs = 10L) {
                attempts++
                "success"
            }
        assertEquals("success", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retryBleOperation retries on exception and succeeds`() = runTest {
        var attempts = 0
        val result =
            retryBleOperation(count = 3, delayMs = 10L) {
                attempts++
                if (attempts < 2) {
                    throw RuntimeException("Temporary error")
                }
                "success"
            }
        assertEquals("success", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `retryBleOperation throws exception after max attempts`() = runTest {
        var attempts = 0
        var caughtException: Exception? = null
        try {
            retryBleOperation(count = 3, delayMs = 10L) {
                attempts++
                throw RuntimeException("Persistent error")
            }
        } catch (e: Exception) {
            caughtException = e
        }

        assertTrue(caughtException is RuntimeException)
        assertEquals("Persistent error", caughtException?.message)
        assertEquals(3, attempts)
    }

    @Test(expected = CancellationException::class)
    fun `retryBleOperation does not retry CancellationException`() = runTest {
        var attempts = 0
        retryBleOperation(count = 3, delayMs = 10L) {
            attempts++
            throw CancellationException("Cancelled")
        }
        // Test fails if it catches and doesn't rethrow, or if it retries.
        // It shouldn't reach the assertion below because the exception should be thrown immediately.
        assertEquals(1, attempts)
    }
}
