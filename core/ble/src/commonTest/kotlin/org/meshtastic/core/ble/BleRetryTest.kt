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
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class BleRetryTest {

    @Test
    fun retryBleOperation_returns_immediately_on_success() = runTest {
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
    fun retryBleOperation_retries_on_exception_and_succeeds() = runTest {
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
    fun retryBleOperation_throws_exception_after_max_attempts() = runTest {
        var attempts = 0
        val ex =
            assertFailsWith<RuntimeException> {
                retryBleOperation(count = 3, delayMs = 10L) {
                    attempts++
                    throw RuntimeException("Persistent error")
                }
            }

        assertEquals("Persistent error", ex.message)
        assertEquals(3, attempts)
    }

    @Test
    fun retryBleOperation_stops_after_failure_when_retry_authority_is_revoked() = runTest {
        val expected = IllegalStateException("session replaced")
        var retryActive = true
        var attempts = 0

        val actual =
            assertFailsWith<IllegalStateException> {
                retryBleOperation(count = 3, delayMs = 10L, retryWhile = { retryActive }) {
                    attempts++
                    retryActive = false
                    throw expected
                }
            }

        assertSame(expected, actual)
        assertEquals(1, attempts)
    }

    @Test
    fun retryBleOperation_rechecks_authority_after_backoff_before_retrying() = runTest {
        val expected = IllegalStateException("session replaced during backoff")
        var retryActive = true
        var attempts = 0
        val result = async {
            runCatching {
                retryBleOperation(count = 3, delayMs = 10L, retryWhile = { retryActive }) {
                    attempts++
                    throw expected
                }
            }
        }

        runCurrent()
        assertEquals(1, attempts)
        retryActive = false
        advanceUntilIdle()

        assertSame(expected, result.await().exceptionOrNull())
        assertEquals(1, attempts)
    }

    @Test
    fun retryBleOperation_does_not_retry_CancellationException() = runTest {
        var attempts = 0
        assertFailsWith<CancellationException> {
            retryBleOperation(count = 3, delayMs = 10L) {
                attempts++
                throw CancellationException("Cancelled")
            }
        }
        assertEquals(1, attempts)
    }
}
