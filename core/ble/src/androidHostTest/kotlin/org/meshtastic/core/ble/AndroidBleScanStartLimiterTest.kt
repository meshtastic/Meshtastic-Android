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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class AndroidBleScanStartLimiterTest {
    @Test
    fun `all scanner consumers share one process quota`() {
        assertSame(createBleScanStartLimiter(), createBleScanStartLimiter())
    }

    @Test
    fun `sixth scan inside Android quota window is rejected`() = runTest {
        val limiter = AndroidBleScanStartLimiter(timeSource = TestTimeSource())

        repeat(ANDROID_BLE_SCAN_START_LIMIT) { limiter.reserveStart() }

        val failure = assertFailsWith<BleScanStartException> { limiter.reserveStart() }
        assertEquals(BleScanStartFailureReason.ScanningTooFrequently, failure.reason)
        assertEquals(ANDROID_BLE_SCAN_START_WINDOW, failure.retryAfter)
    }

    @Test
    fun `reservation succeeds after oldest scan leaves quota window`() = runTest {
        val timeSource = TestTimeSource()
        val limiter = AndroidBleScanStartLimiter(timeSource = timeSource, maxStarts = 2, window = 30.seconds)

        limiter.reserveStart()
        timeSource += 10.seconds
        limiter.reserveStart()
        timeSource += 20.seconds

        limiter.reserveStart()
    }

    @Test
    fun `concurrent callers cannot exceed quota`() = runTest {
        val limiter = AndroidBleScanStartLimiter(timeSource = TestTimeSource())

        val results =
            List(ANDROID_BLE_SCAN_START_LIMIT + 1) { async { runCatching { limiter.reserveStart() } } }.awaitAll()

        assertEquals(ANDROID_BLE_SCAN_START_LIMIT, results.count { it.isSuccess })
        val failure = results.single { it.isFailure }.exceptionOrNull()
        assertTrue(failure is BleScanStartException)
        assertEquals(BleScanStartFailureReason.ScanningTooFrequently, failure.reason)
    }
}
